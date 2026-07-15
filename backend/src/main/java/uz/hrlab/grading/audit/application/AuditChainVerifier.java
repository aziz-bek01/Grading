package uz.hrlab.grading.audit.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.infrastructure.AuditChainNode;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MVP1-E10-1 — walks a tenant's append-only audit chain and verifies its
 * integrity (security-blueprint §9.2).
 *
 * <p>Pure engine: takes a tenant id + optional window + row cap and returns an
 * {@link AuditChainVerificationResult}. NO permission/request-context logic — the
 * caller ({@code VerifyAuditIntegrityQuery}) enforces {@code AUDIT_READ} and
 * supplies the tenant id from {@code TenantContext} (never client input).
 *
 * <h3>Order is derived from the CHAIN LINKS, not wall-clock (M1)</h3>
 * {@code created_at} is UTC/microsecond-truncated, so two same-tenant appends can
 * share a microsecond and a clock can step backwards; ordering by
 * {@code (created_at, id)} would then differ from how the rows were LINKED and
 * raise a FALSE break on an untampered chain. The authoritative sequence is
 * reconstructed by FOLLOWING {@code hash_prev → hash_current} — the same for
 * legacy and current rows, no timestamp dependency. The window anchor (F-1) is
 * also link-derived (the loaded row whose predecessor lies outside the loaded
 * set), never picked by wall-clock.
 *
 * <h3>Two memory-bounded passes</h3>
 * <ol>
 *   <li><b>Pass 1 (structure).</b> Load the whole bounded chain as LIGHTWEIGHT
 *       {@link AuditChainNode}s (no payload), index by {@code hash_prev}, find the
 *       anchor and walk the links to the authoritative ordered id list. Detects
 *       fork (two rows sharing a {@code hash_prev}), cycle, missing genesis (GAP),
 *       orphan/disconnected segments (deleted/re-linked middle row →
 *       {@code BROKEN_PREV_LINK}) and version regression (F-2).</li>
 *   <li><b>Pass 2 (content).</b> Page through the ordered ids, fetching payloads a
 *       page at a time (payload memory O(page)), and recompute {@code hash_current}
 *       for CURRENT-format rows only. Legacy (v1) rows are counted
 *       {@code legacyUnverifiable} — never recomputed, never tampered-flagged.</li>
 * </ol>
 *
 * <h3>Bounding + the same-microsecond boundary (F-A)</h3>
 * Pass 1 loads at most {@link #DEFAULT_MAX_ROWS} (clamped to
 * {@link #HARD_CAP_MAX_ROWS}) lightweight nodes. On hitting the cap it DRAINS the
 * trailing same-{@code created_at} bucket (bounded by {@link #DRAIN_ALLOWANCE})
 * so a same-microsecond link-group is never split across the load boundary —
 * otherwise its tail would look like an orphan and false-BROKEN. After draining,
 * an untampered bounded load is link-contiguous, so a residual unvisited node is a
 * GENUINE orphan → BROKEN even when {@code bounded}. Only in the astronomically
 * rare case that the trailing bucket exceeds the drain allowance (a malicious
 * same-µs flood) is the orphan check suppressed for that residual — a partial
 * verification is honest; a false tamper alarm on an un-orderable boundary is not.
 */
@Service
public class AuditChainVerifier {

    /** Rows fetched / recomputed per page. */
    static final int PAGE_SIZE = 1_000;
    /** Default per-run cap when the caller does not specify one. */
    public static final int DEFAULT_MAX_ROWS = 50_000;
    /** Absolute ceiling — a caller can never request more than this in one run. */
    public static final int HARD_CAP_MAX_ROWS = 200_000;
    /** Extra rows we may load past the cap to drain a trailing same-µs bucket. */
    static final int DRAIN_ALLOWANCE = PAGE_SIZE;

    // Wide, non-null sentinels so a "no window" verification still binds typed
    // (non-null) TIMESTAMPTZ bounds — see SystemAuditLogRepository.findChainNodes.
    private static final OffsetDateTime MIN_TS =
            OffsetDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MAX_TS =
            OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
    private static final UUID MIN_UUID = new UUID(0L, 0L);
    /** Map key standing in for a null {@code hash_prev} (the genesis link). */
    private static final String NULL_PREV_KEY = " __genesis__";

    private final SystemAuditLogRepository repository;
    private final AuditHashCalculator hashCalculator;

    public AuditChainVerifier(SystemAuditLogRepository repository,
                              AuditHashCalculator hashCalculator) {
        this.repository = repository;
        this.hashCalculator = hashCalculator;
    }

    /**
     * Verifies the chain of {@code tenantId}.
     *
     * @param tenantId tenant chain to verify (from TenantContext, never input)
     * @param from     optional inclusive lower bound (null = from genesis)
     * @param to       optional inclusive upper bound (null = to head)
     * @param maxRows  optional per-run cap (null = {@link #DEFAULT_MAX_ROWS})
     */
    @Transactional(readOnly = true)
    public AuditChainVerificationResult verify(UUID tenantId,
                                               OffsetDateTime from,
                                               OffsetDateTime to,
                                               Integer maxRows) {
        Objects.requireNonNull(tenantId, "tenantId");
        int cap = clampMaxRows(maxRows);
        boolean windowed = from != null;
        OffsetDateTime fromInclusive = from != null ? from : MIN_TS;
        OffsetDateTime toInclusive = to != null ? to : MAX_TS;
        OffsetDateTime verifiedAt = OffsetDateTime.now(ZoneOffset.UTC);

        long chainLength = repository.countChain(tenantId, fromInclusive, toInclusive);
        if (chainLength == 0) {
            return new AuditChainVerificationResult(
                    tenantId, AuditChainVerificationResult.Status.EMPTY,
                    0, 0, 0, 0, null, null, from, to, false, cap, null, verifiedAt);
        }

        // ---- Pass 1: load lightweight nodes + reconstruct order from links ----
        LoadResult load = loadNodes(tenantId, fromInclusive, toInclusive, cap);
        List<AuditChainNode> nodes = load.nodes();
        boolean bounded = nodes.size() < chainLength;
        LinkWalk walk = reconstructOrder(nodes, windowed, load.boundaryClean());

        if (walk.structuralBreak() != null) {
            return new AuditChainVerificationResult(
                    tenantId, AuditChainVerificationResult.Status.BROKEN,
                    walk.ordered().size(), chainLength, 0, 0, null,
                    lastCreatedAt(walk.ordered()), from, to, false, cap,
                    walk.structuralBreak(), verifiedAt);
        }

        // ---- Pass 2: recompute content for current-format rows, in link order --
        return contentVerify(tenantId, walk.ordered(), chainLength, bounded,
                from, to, cap, verifiedAt);
    }

    // ------------------------------------------------------------------ pass 1

    /**
     * Loads up to {@code cap} lightweight nodes and, if the cap is hit, drains the
     * trailing same-{@code created_at} bucket so a same-microsecond link-group is
     * never split across the boundary (F-A).
     */
    private LoadResult loadNodes(UUID tenantId, OffsetDateTime fromInc,
                                 OffsetDateTime toInc, int cap) {
        List<AuditChainNode> nodes = new ArrayList<>();
        OffsetDateTime cursorTs = MIN_TS;
        UUID cursorId = MIN_UUID;

        while (nodes.size() < cap) {
            int pageSize = Math.min(PAGE_SIZE, cap - nodes.size());
            List<AuditChainNode> page = repository.findChainNodes(
                    tenantId, fromInc, toInc, cursorTs, cursorId, PageRequest.of(0, pageSize));
            if (page.isEmpty()) {
                return new LoadResult(nodes, true); // whole chain loaded
            }
            nodes.addAll(page);
            AuditChainNode last = page.get(page.size() - 1);
            cursorTs = last.createdAt();
            cursorId = last.id();
            if (page.size() < pageSize) {
                return new LoadResult(nodes, true); // exhausted before the cap
            }
        }

        // Cap hit — drain the trailing same-created_at bucket.
        OffsetDateTime boundaryTs = nodes.get(nodes.size() - 1).createdAt();
        int drained = 0;
        while (drained < DRAIN_ALLOWANCE) {
            int req = Math.min(PAGE_SIZE, DRAIN_ALLOWANCE - drained);
            List<AuditChainNode> page = repository.findChainNodes(
                    tenantId, fromInc, toInc, cursorTs, cursorId, PageRequest.of(0, req));
            if (page.isEmpty()) {
                return new LoadResult(nodes, true); // table exhausted → clean boundary
            }
            for (AuditChainNode n : page) {
                if (!n.createdAt().isEqual(boundaryTs)) {
                    return new LoadResult(nodes, true); // bucket fully drained
                }
                nodes.add(n);
                drained++;
                cursorTs = n.createdAt();
                cursorId = n.id();
                if (drained >= DRAIN_ALLOWANCE) {
                    break;
                }
            }
            if (page.size() < req) {
                return new LoadResult(nodes, true); // exhausted
            }
        }
        // FAIL-SAFE (astronomically rare): the trailing same-µs bucket is larger
        // than the drain allowance, so the boundary may split a link-group. Flag
        // boundaryClean=false → the completeness check suppresses orphan-BROKEN for
        // this residual only (partial-but-honest, never a false alarm). Unreachable
        // in practice: appends are advisory-lock serialized (multi-ms each).
        return new LoadResult(nodes, false);
    }

    /**
     * Reconstructs the authoritative order by following {@code hash_prev →
     * hash_current}. {@code nodes} are in {@code (created_at, id)} order only so
     * the "earliest" offender / window anchor is deterministic.
     */
    private LinkWalk reconstructOrder(List<AuditChainNode> nodes, boolean windowed,
                                      boolean boundaryClean) {
        Map<String, List<AuditChainNode>> byPrev = new HashMap<>();
        Set<String> presentCurrent = new HashSet<>();
        for (AuditChainNode n : nodes) {
            presentCurrent.add(n.hashCurrent());
        }
        for (AuditChainNode n : nodes) {
            byPrev.computeIfAbsent(prevKey(n.hashPrev()), k -> new ArrayList<>()).add(n);
        }

        // Fork = two rows sharing a hash_prev (includes two genesis rows sharing
        // the null-prev slot). Report the later-created of the pair.
        for (List<AuditChainNode> siblings : byPrev.values()) {
            if (siblings.size() > 1) {
                AuditChainNode offender = laterOf(siblings.get(0), siblings.get(1));
                return LinkWalk.broken(List.of(), brk(offender,
                        AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK,
                        offender.hashPrev(), offender.hashPrev()));
            }
        }

        // Head = the link-derived anchor (predecessor not in the loaded set).
        AuditChainNode head;
        if (windowed) {
            // F-1: the window anchor is the earliest row whose hash_prev is NOT the
            // hash_current of any loaded row (its predecessor lies outside the
            // window) — NOT nodes.get(0). Extra anchors mean the slice is
            // disconnected (an interior gap); the completeness check reports them.
            head = firstAnchor(nodes, presentCurrent);
            if (head == null) {
                // Every row's predecessor is in-window → no entry point → cycle.
                return LinkWalk.broken(List.of(), brk(nodes.get(0),
                        AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK,
                        null, nodes.get(0).hashPrev()));
            }
        } else {
            List<AuditChainNode> genesis = byPrev.get(NULL_PREV_KEY);
            if (genesis == null) {
                // No genesis (null hash_prev) → the oldest row's predecessor is
                // missing (a deleted/truncated head).
                AuditChainNode offender = nodes.get(0);
                return LinkWalk.broken(List.of(), brk(offender,
                        AuditChainVerificationResult.BreakType.GAP,
                        null, offender.hashPrev()));
            }
            head = genesis.get(0);
        }

        // Walk the links from the head, enforcing version monotonicity (F-2).
        List<AuditChainNode> ordered = new ArrayList<>(nodes.size());
        Set<UUID> visited = new HashSet<>();
        short maxVersion = 0;
        AuditChainNode current = head;
        while (current != null) {
            if (!visited.add(current.id())) {
                return LinkWalk.broken(ordered, brk(current,
                        AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK,
                        current.hashPrev(), current.hashPrev()));
            }
            if (current.hashFormatVersion() < maxVersion) {
                // F-2: version decreased in link order → downgrade tamper.
                return LinkWalk.broken(ordered, brk(current,
                        AuditChainVerificationResult.BreakType.VERSION_REGRESSION,
                        String.valueOf(maxVersion), String.valueOf(current.hashFormatVersion())));
            }
            maxVersion = (short) Math.max(maxVersion, current.hashFormatVersion());
            ordered.add(current);
            List<AuditChainNode> succ = byPrev.get(prevKey(current.hashCurrent()));
            current = (succ == null) ? null : succ.get(0); // size==1 (fork checked)
        }

        if (ordered.size() == nodes.size()) {
            return LinkWalk.ok(ordered);
        }
        if (!boundaryClean) {
            // F-A fail-safe: un-orderable same-µs boundary flood — suppress the
            // orphan check for the residual (partial, honest), never false-BROKEN.
            return LinkWalk.ok(ordered);
        }
        // Some rows are unreachable from the anchor → a break in the middle left a
        // disconnected segment (deleted/re-linked/edited middle row, or a windowed
        // interior gap). Report the earliest orphan as a broken prev-link.
        AuditChainNode orphan = firstNotVisited(nodes, visited);
        return LinkWalk.broken(ordered, brk(orphan,
                AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK,
                null, orphan.hashPrev()));
    }

    // ------------------------------------------------------------------ pass 2

    private AuditChainVerificationResult contentVerify(UUID tenantId,
                                                       List<AuditChainNode> ordered,
                                                       long chainLength,
                                                       boolean bounded,
                                                       OffsetDateTime from,
                                                       OffsetDateTime to,
                                                       int cap,
                                                       OffsetDateTime verifiedAt) {
        long independentlyVerified = 0;
        long legacyUnverifiable = 0;
        OffsetDateTime verifiableFrom = null;
        OffsetDateTime verifiedThrough = null;
        AuditChainVerificationResult.Break contentBreak = null;

        outer:
        for (int i = 0; i < ordered.size(); i += PAGE_SIZE) {
            List<AuditChainNode> pageNodes = ordered.subList(i, Math.min(i + PAGE_SIZE, ordered.size()));
            List<UUID> ids = pageNodes.stream().map(AuditChainNode::id).toList();
            Map<UUID, SystemAuditLogJpaEntity> byId = repository.findPayloadsByIds(tenantId, ids)
                    .stream().collect(Collectors.toMap(SystemAuditLogJpaEntity::getId, Function.identity()));

            for (AuditChainNode node : pageNodes) {
                SystemAuditLogJpaEntity row = byId.get(node.id());
                if (row == null) {
                    // Row vanished between passes — treat as a gap.
                    contentBreak = brk(node, AuditChainVerificationResult.BreakType.GAP,
                            null, node.hashPrev());
                    break outer;
                }
                if (isContentVerifiable(node.hashFormatVersion())) {
                    String recomputed = hashCalculator.compute(toHashInput(row));
                    if (!recomputed.equals(row.getHashCurrent())) {
                        contentBreak = new AuditChainVerificationResult.Break(
                                row.getId(), row.getCreatedAt(),
                                AuditChainVerificationResult.BreakType.HASH_MISMATCH,
                                recomputed, row.getHashCurrent());
                        break outer;
                    }
                    independentlyVerified++;
                    if (verifiableFrom == null) {
                        verifiableFrom = node.createdAt();
                    }
                } else {
                    legacyUnverifiable++;
                }
                verifiedThrough = node.createdAt();
            }
        }

        AuditChainVerificationResult.Status status = contentBreak == null
                ? AuditChainVerificationResult.Status.INTACT
                : AuditChainVerificationResult.Status.BROKEN;
        boolean bnd = contentBreak == null && bounded;

        return new AuditChainVerificationResult(
                tenantId, status, ordered.size(), chainLength,
                independentlyVerified, legacyUnverifiable, verifiableFrom,
                verifiedThrough, from, to, bnd, cap, contentBreak, verifiedAt);
    }

    // ------------------------------------------------------------------ helpers

    /** Earliest (created_at, id) row whose predecessor is NOT in the loaded set. */
    private static AuditChainNode firstAnchor(List<AuditChainNode> nodes, Set<String> presentCurrent) {
        for (AuditChainNode n : nodes) {
            if (n.hashPrev() == null || !presentCurrent.contains(n.hashPrev())) {
                return n;
            }
        }
        return null;
    }

    private boolean isContentVerifiable(short version) {
        return version == AuditHashCalculator.HASH_FORMAT_VERSION;
    }

    private AuditHashInput toHashInput(SystemAuditLogJpaEntity row) {
        return new AuditHashInput(
                row.getId(), row.getTenantId(), row.getProjectId(), row.getActorUserId(),
                row.getAction(), row.getEntityType(), row.getEntityId(),
                row.getBeforeJson(), row.getAfterJson(), row.getCreatedAt(),
                row.getHashFormatVersion(), row.getHashPrev());
    }

    private static String prevKey(String hashPrev) {
        return hashPrev == null ? NULL_PREV_KEY : hashPrev;
    }

    private static AuditChainVerificationResult.Break brk(
            AuditChainNode n, AuditChainVerificationResult.BreakType type,
            String expected, String actual) {
        return new AuditChainVerificationResult.Break(n.id(), n.createdAt(), type, expected, actual);
    }

    private static AuditChainNode laterOf(AuditChainNode a, AuditChainNode b) {
        int c = a.createdAt().compareTo(b.createdAt());
        if (c != 0) {
            return c >= 0 ? a : b;
        }
        return a.id().compareTo(b.id()) >= 0 ? a : b;
    }

    private static AuditChainNode firstNotVisited(List<AuditChainNode> nodes, Set<UUID> visited) {
        for (AuditChainNode n : nodes) {
            if (!visited.contains(n.id())) {
                return n;
            }
        }
        return nodes.get(0); // unreachable — the caller only calls when orphans exist
    }

    private static OffsetDateTime lastCreatedAt(List<AuditChainNode> ordered) {
        return ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).createdAt();
    }

    private int clampMaxRows(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_MAX_ROWS;
        }
        return Math.min(requested, HARD_CAP_MAX_ROWS);
    }

    /** Pass-1 load outcome. {@code boundaryClean} is false only when a trailing
     *  same-µs bucket exceeded the drain allowance (the F-A fail-safe). */
    private record LoadResult(List<AuditChainNode> nodes, boolean boundaryClean) {
    }

    /** Pass-1 walk outcome: an ordered node list plus an optional structural break. */
    private record LinkWalk(List<AuditChainNode> ordered,
                            AuditChainVerificationResult.Break structuralBreak) {
        static LinkWalk ok(List<AuditChainNode> ordered) {
            return new LinkWalk(ordered, null);
        }
        static LinkWalk broken(List<AuditChainNode> ordered,
                               AuditChainVerificationResult.Break brk) {
            return new LinkWalk(ordered, brk);
        }
    }
}
