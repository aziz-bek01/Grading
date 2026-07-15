package uz.hrlab.grading.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.audit.infrastructure.AuditChainNode;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MVP1-E10-1 — the verification engine. Uses the REAL {@link AuditHashCalculator}
 * against a REALISTIC keyset-honoring mocked repository (returns nodes in the
 * true {@code (created_at, id)} order, honours the cursor/window/page-size, and
 * backs pass-2 payload fetches) so the two-pass, LINK-FOLLOWING verifier is
 * covered without Docker — including the M1/F-A/F-1/F-2 order-and-version fixes.
 */
@Tag("audit")
class AuditChainVerifierTest {

    private static final short V1_LEGACY = 1;
    private static final short V2_CURRENT = AuditHashCalculator.HASH_FORMAT_VERSION; // 2

    private final SystemAuditLogRepository repo = mock(SystemAuditLogRepository.class);
    private final AuditHashCalculator calc = new AuditHashCalculator(new ObjectMapper());
    private final AuditChainVerifier verifier = new AuditChainVerifier(repo, calc);

    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private OffsetDateTime at(int minute) {
        return OffsetDateTime.of(2026, 7, 15, 10, minute, 0, 0, ZoneOffset.UTC);
    }

    private UUID uuid(String suffix) {
        return UUID.fromString("00000000-0000-4000-8000-0000000000" + suffix);
    }

    private SystemAuditLogJpaEntity v2Row(UUID id, OffsetDateTime ts, String afterJson, String hashPrev) {
        String hc = calc.compute(new AuditHashInput(id, tenant, null, actor,
                "TENANT_CREATED", "Tenant", tenant, null, afterJson, ts, V2_CURRENT, hashPrev));
        return row(id, ts, afterJson, hashPrev, hc, V2_CURRENT);
    }

    private SystemAuditLogJpaEntity v2Row(OffsetDateTime ts, String afterJson, String hashPrev) {
        return v2Row(UUID.randomUUID(), ts, afterJson, hashPrev);
    }

    private SystemAuditLogJpaEntity v1Row(OffsetDateTime ts, String afterJson,
                                          String hashPrev, String hashCurrent) {
        return row(UUID.randomUUID(), ts, afterJson, hashPrev, hashCurrent, V1_LEGACY);
    }

    private SystemAuditLogJpaEntity row(UUID id, OffsetDateTime ts, String afterJson,
                                        String hashPrev, String hashCurrent, short version) {
        return new SystemAuditLogJpaEntity(id, tenant, null, actor,
                "TENANT_CREATED", "Tenant", tenant, null, afterJson, null, null,
                null, null, null, ts, hashPrev, hashCurrent, version);
    }

    private AuditChainNode nodeOf(SystemAuditLogJpaEntity r) {
        return new AuditChainNode(r.getId(), r.getHashPrev(), r.getHashCurrent(),
                r.getHashFormatVersion(), r.getCreatedAt());
    }

    /**
     * Realistic repository stub over a backing row set: findChainNodes honours the
     * {@code (created_at, id)} order + window + keyset cursor + page size, so cap
     * truncation and the drain re-queries behave like the real query.
     */
    private void givenChain(List<SystemAuditLogJpaEntity> allRows) {
        List<SystemAuditLogJpaEntity> sorted = new ArrayList<>(allRows);
        sorted.sort(Comparator.comparing(SystemAuditLogJpaEntity::getCreatedAt)
                .thenComparing(SystemAuditLogJpaEntity::getId));

        when(repo.countChain(eq(tenant), any(), any())).thenAnswer(inv -> {
            OffsetDateTime f = inv.getArgument(1);
            OffsetDateTime t = inv.getArgument(2);
            return sorted.stream().filter(r -> inWindow(r, f, t)).count();
        });
        when(repo.findChainNodes(eq(tenant), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            OffsetDateTime f = inv.getArgument(1);
            OffsetDateTime t = inv.getArgument(2);
            OffsetDateTime cTs = inv.getArgument(3);
            UUID cId = inv.getArgument(4);
            Pageable p = inv.getArgument(5);
            return sorted.stream()
                    .filter(r -> inWindow(r, f, t))
                    .filter(r -> afterCursor(r, cTs, cId))
                    .limit(p.getPageSize())
                    .map(this::nodeOf).toList();
        });
        when(repo.findPayloadsByIds(eq(tenant), any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(1);
            return allRows.stream().filter(r -> ids.contains(r.getId())).toList();
        });
    }

    private static boolean inWindow(SystemAuditLogJpaEntity r, OffsetDateTime f, OffsetDateTime t) {
        OffsetDateTime ts = r.getCreatedAt();
        return !ts.isBefore(f) && !ts.isAfter(t);
    }

    private static boolean afterCursor(SystemAuditLogJpaEntity r, OffsetDateTime cTs, UUID cId) {
        OffsetDateTime ts = r.getCreatedAt();
        return ts.isAfter(cTs) || (ts.isEqual(cTs) && r.getId().compareTo(cId) > 0);
    }

    // ------------------------------------------------------------------ intact

    @Test
    void intactCurrentFormatChainVerifiesOk() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1 = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        var r2 = v2Row(at(2), "{\"a\":3}", r1.getHashCurrent());
        givenChain(List.of(r0, r1, r2));

        var result = verifier.verify(tenant, null, null, null);

        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.checkedCount()).isEqualTo(3);
        assertThat(result.independentlyVerifiedCount()).isEqualTo(3);
        assertThat(result.legacyUnverifiableCount()).isZero();
        assertThat(result.verifiableFrom()).isEqualTo(at(0));
        assertThat(result.verifiedThrough()).isEqualTo(at(2));
        assertThat(result.firstBreak()).isNull();
    }

    // ------------------------------------------------- M1: same-microsecond order

    @Test
    void sameMicrosecondRowsVerifyIntactRegardlessOfIdOrder() {
        // genesis id LOW, successor id HIGH → natural (created_at,id) order.
        var gLow = v2Row(uuid("01"), at(0), "{\"a\":1}", null);
        var sHigh = v2Row(uuid("02"), at(0), "{\"a\":2}", gLow.getHashCurrent());
        givenChain(List.of(gLow, sHigh));
        assertThat(verifier.verify(tenant, null, null, null).status())
                .isEqualTo(AuditChainVerificationResult.Status.INTACT);

        // genesis id HIGH, successor id LOW → (created_at,id) sort puts the
        // successor FIRST; a wall-clock verifier would false-BROKEN.
        var gHigh = v2Row(uuid("99"), at(0), "{\"a\":1}", null);
        var sLow = v2Row(uuid("11"), at(0), "{\"a\":2}", gHigh.getHashCurrent());
        givenChain(List.of(gHigh, sLow));
        assertThat(verifier.verify(tenant, null, null, null).status())
                .isEqualTo(AuditChainVerificationResult.Status.INTACT);
    }

    @Test
    void clockBackstepBetweenAppendsVerifiesIntact() {
        // r1 was appended AFTER r0 (links r0→r1) but got an EARLIER created_at.
        var r0 = v2Row(at(5), "{\"a\":1}", null);
        var r1 = v2Row(at(3), "{\"a\":2}", r0.getHashCurrent());
        givenChain(List.of(r0, r1));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.checkedCount()).isEqualTo(2);
    }

    // ------------------------------------------------- M1: genuine breaks BROKEN

    @Test
    void forkIsDetected() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1a = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        var r1b = v2Row(at(2), "{\"a\":3}", r0.getHashCurrent()); // same predecessor → fork
        givenChain(List.of(r0, r1a, r1b));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK);
    }

    @Test
    void deletedMiddleRowIsDetectedAsBrokenLink() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1 = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        var r2 = v2Row(at(2), "{\"a\":3}", r1.getHashCurrent());
        givenChain(List.of(r0, r2)); // r1 deleted from storage

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(r2.getId());
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK);
    }

    @Test
    void createdAtReorderTamperOnV2IsDetectedAsHashMismatch() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1 = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        var r1Reordered = row(r1.getId(), at(9), "{\"a\":2}", r0.getHashCurrent(),
                r1.getHashCurrent(), V2_CURRENT);
        givenChain(List.of(r0, r1Reordered));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(r1.getId());
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.HASH_MISMATCH);
    }

    // =============================================================== F-A (HIGH)

    @Test
    void deletedMiddleRowIsDetectedEvenWhenBounded() {
        // 6-row chain, r2 deleted; cap=3 → bounded load, but the delete is INSIDE
        // the loaded prefix. `|| bounded` used to mask this as INTACT.
        var r0 = v2Row(at(0), "{\"a\":0}", null);
        var r1 = v2Row(at(1), "{\"a\":1}", r0.getHashCurrent());
        var r2 = v2Row(at(2), "{\"a\":2}", r1.getHashCurrent());
        var r3 = v2Row(at(3), "{\"a\":3}", r2.getHashCurrent());
        var r4 = v2Row(at(4), "{\"a\":4}", r3.getHashCurrent());
        var r5 = v2Row(at(5), "{\"a\":5}", r4.getHashCurrent());
        givenChain(List.of(r0, r1, r3, r4, r5)); // r2 deleted; chainLength=5

        // cap=3 → the load is bounded, but the delete is inside the loaded prefix.
        // `|| bounded` used to mask this as INTACT; it must now be BROKEN.
        var result = verifier.verify(tenant, null, null, 3);

        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(r3.getId());
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK);
    }

    @Test
    void sameMicrosecondBucketStraddlingCapIsDrainedAndIntact() {
        // r1,r2 share a microsecond and straddle a cap=2 boundary; the drain must
        // pull the whole bucket so neither ordering false-BROKENs.
        // Ordering A: r1.id < r2.id
        runStraddleCase(uuid("21"), uuid("22"));
        // Ordering B: r1.id > r2.id (successor loaded before predecessor)
        runStraddleCase(uuid("32"), uuid("31"));
    }

    private void runStraddleCase(UUID id1, UUID id2) {
        var r0 = v2Row(at(0), "{\"a\":0}", null);
        var r1 = v2Row(id1, at(1), "{\"a\":1}", r0.getHashCurrent());
        var r2 = v2Row(id2, at(1), "{\"a\":2}", r1.getHashCurrent()); // same µs as r1
        var r3 = v2Row(at(2), "{\"a\":3}", r2.getHashCurrent());
        givenChain(List.of(r0, r1, r2, r3));

        var result = verifier.verify(tenant, null, null, 2); // cap=2 straddles the µs bucket
        assertThat(result.status())
                .as("same-µs bucket straddling the cap must not false-BROKEN")
                .isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.bounded()).isTrue();
    }

    // =============================================================== F-1 (window)

    @Test
    void windowedAnchorIsLinkDerivedForSameMicrosecondStart() {
        // g (outside window) → a → b → c, with a,b sharing a µs. The window's
        // first two rows share a µs; a wall-clock anchor (nodes.get(0)) could pick
        // b and orphan a. Both id orderings must verify INTACT.
        assertWindowedIntact(uuid("41"), uuid("42")); // a.id < b.id
        assertWindowedIntact(uuid("52"), uuid("51")); // a.id > b.id
    }

    private void assertWindowedIntact(UUID aId, UUID bId) {
        var g = v2Row(at(0), "{\"g\":1}", null);
        var a = v2Row(aId, at(5), "{\"a\":1}", g.getHashCurrent());
        var b = v2Row(bId, at(5), "{\"b\":1}", a.getHashCurrent()); // same µs as a
        var c = v2Row(at(6), "{\"c\":1}", b.getHashCurrent());
        givenChain(List.of(g, a, b, c));

        var result = verifier.verify(tenant, at(5), null, null); // window excludes genesis g
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.checkedCount()).isEqualTo(3); // a, b, c
    }

    @Test
    void windowedInteriorGapIsBroken() {
        // window covers a,b,c,d but c is deleted → d dangles inside the slice.
        var g = v2Row(at(0), "{\"g\":1}", null);
        var a = v2Row(at(5), "{\"a\":1}", g.getHashCurrent());
        var b = v2Row(at(6), "{\"b\":1}", a.getHashCurrent());
        var c = v2Row(at(7), "{\"c\":1}", b.getHashCurrent());
        var d = v2Row(at(8), "{\"d\":1}", c.getHashCurrent());
        givenChain(List.of(g, a, b, d)); // c deleted

        var result = verifier.verify(tenant, at(5), null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(d.getId());
    }

    // =============================================================== F-2 (version)

    @Test
    void versionDowngradeInInteriorIsDetected() {
        // v2 → v2 → (v1 relabel) → v2 : the downgrade tries to skip content
        // recompute to hide an edit; monotonicity catches it.
        var r0 = v2Row(at(0), "{\"a\":0}", null);
        var r1 = v2Row(at(1), "{\"a\":1}", r0.getHashCurrent());
        var r2 = row(UUID.randomUUID(), at(2), "{\"a\":2}", r1.getHashCurrent(), "DOWNGRADED", V1_LEGACY);
        var r3 = v2Row(at(3), "{\"a\":3}", "DOWNGRADED");
        givenChain(List.of(r0, r1, r2, r3));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(r2.getId());
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.VERSION_REGRESSION);
    }

    @Test
    void genuineLegacyToCurrentBoundaryIsIntact() {
        // v1 → v1 → v2 → v2 : version is non-decreasing → the real migration
        // boundary must stay INTACT.
        var r0 = v1Row(at(0), "{\"a\":0}", null, "L0");
        var r1 = v1Row(at(1), "{\"a\":1}", "L0", "L1");
        var r2 = v2Row(at(2), "{\"a\":2}", "L1");
        var r3 = v2Row(at(3), "{\"a\":3}", r2.getHashCurrent());
        givenChain(List.of(r0, r1, r2, r3));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.legacyUnverifiableCount()).isEqualTo(2);
        assertThat(result.independentlyVerifiedCount()).isEqualTo(2);
        assertThat(result.verifiableFrom()).isEqualTo(at(2));
    }

    // ------------------------------------------------------- all-legacy / mixed

    @Test
    void allLegacyChainWithIntactLinkageIsIntactNotBroken() {
        var r0 = v1Row(at(0), "{\"a\":1}", null, "L0");
        var r1 = v1Row(at(1), "{\"a\":2}", "L0", "L1");
        var r2 = v1Row(at(2), "{\"a\":3}", "L1", "L2");
        givenChain(List.of(r0, r1, r2));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.independentlyVerifiedCount()).isZero();
        assertThat(result.legacyUnverifiableCount()).isEqualTo(3);
        assertThat(result.verifiableFrom()).isNull();
    }

    @Test
    void mixedLegacyThenCurrentChainIsIntactWithCounts() {
        var r0 = v1Row(at(0), "{\"a\":1}", null, "L0");
        var r1 = v2Row(at(1), "{\"a\":2}", "L0");
        var r2 = v2Row(at(2), "{\"a\":3}", r1.getHashCurrent());
        givenChain(List.of(r0, r1, r2));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.legacyUnverifiableCount()).isEqualTo(1);
        assertThat(result.independentlyVerifiedCount()).isEqualTo(2);
        assertThat(result.verifiableFrom()).isEqualTo(at(1));
    }

    @Test
    void legacyRowWithMutatedPayloadIsNotFlagged() {
        var r0 = v1Row(at(0), "{\"payload\":\"mutated\"}", null, "not-the-real-hash");
        givenChain(List.of(r0));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.legacyUnverifiableCount()).isEqualTo(1);
        assertThat(result.independentlyVerifiedCount()).isZero();
        assertThat(result.firstBreak()).isNull();
    }

    // ----------------------------------------------------------- hash mismatch

    @Test
    void tamperedCurrentFormatPayloadIsDetectedAsHashMismatch() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1 = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        var r1Tampered = row(r1.getId(), at(1), "{\"a\":999}", r0.getHashCurrent(),
                r1.getHashCurrent(), V2_CURRENT);
        var r2 = v2Row(at(2), "{\"a\":3}", r1.getHashCurrent());
        givenChain(List.of(r0, r1Tampered, r2));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(r1.getId());
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.HASH_MISMATCH);
        assertThat(result.firstBreak().actualHash()).isEqualTo(r1.getHashCurrent());
        assertThat(result.firstBreak().expectedHash()).isNotEqualTo(r1.getHashCurrent());
        assertThat(result.independentlyVerifiedCount()).isEqualTo(1);
    }

    @Test
    void brokenLinkOnLegacyRowIsStillDetected() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1 = v1Row(at(1), "{\"a\":2}", "WRONG_PREV", "L1");
        givenChain(List.of(r0, r1));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().rowId()).isEqualTo(r1.getId());
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.BROKEN_PREV_LINK);
        assertThat(result.firstBreak().actualHash()).isEqualTo("WRONG_PREV");
    }

    // -------------------------------------------------------------------- gap

    @Test
    void missingGenesisIsGap() {
        var r0 = v2Row(at(0), "{\"a\":1}", "ORPHAN_PREV");
        givenChain(List.of(r0));

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.BROKEN);
        assertThat(result.firstBreak().breakType())
                .isEqualTo(AuditChainVerificationResult.BreakType.GAP);
        assertThat(result.firstBreak().actualHash()).isEqualTo("ORPHAN_PREV");
    }

    @Test
    void windowedVerificationAcceptsNonNullAnchorPrev() {
        var r0 = v2Row(at(0), "{\"a\":1}", "PRIOR_WINDOW_HASH");
        var r1 = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        givenChain(List.of(r0, r1));

        var result = verifier.verify(tenant, at(0), at(1), null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.from()).isEqualTo(at(0));
        assertThat(result.checkedCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------------- empty

    @Test
    void emptyChainReportsEmpty() {
        when(repo.countChain(eq(tenant), any(), any())).thenReturn(0L);

        var result = verifier.verify(tenant, null, null, null);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.EMPTY);
        assertThat(result.intact()).isTrue();
        assertThat(result.checkedCount()).isZero();
        assertThat(result.firstBreak()).isNull();
    }

    // ----------------------------------------------------------------- bounded

    @Test
    void runIsBoundedByMaxRows() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        var r1 = v2Row(at(1), "{\"a\":2}", r0.getHashCurrent());
        var r2 = v2Row(at(2), "{\"a\":3}", r1.getHashCurrent());
        givenChain(List.of(r0, r1, r2));

        var result = verifier.verify(tenant, null, null, 2);
        assertThat(result.status()).isEqualTo(AuditChainVerificationResult.Status.INTACT);
        assertThat(result.checkedCount()).isEqualTo(2);
        assertThat(result.chainLength()).isEqualTo(3);
        assertThat(result.bounded()).isTrue();
        assertThat(result.maxRows()).isEqualTo(2);
    }

    @Test
    void maxRowsIsClampedToHardCeiling() {
        var r0 = v2Row(at(0), "{\"a\":1}", null);
        givenChain(List.of(r0));

        var result = verifier.verify(tenant, null, null, Integer.MAX_VALUE);
        assertThat(result.maxRows()).isEqualTo(AuditChainVerifier.HARD_CAP_MAX_ROWS);
    }
}
