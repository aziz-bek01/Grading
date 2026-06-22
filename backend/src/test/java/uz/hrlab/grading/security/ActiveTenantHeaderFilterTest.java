package uz.hrlab.grading.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * BE-AUTH-MT-001 — unit guarantees for {@link ActiveTenantHeaderFilter}:
 * <ul>
 *   <li>a present {@code X-Active-Tenant-Id} is bound on
 *       {@link ActiveTenantHeaderHolder} <em>while the chain runs</em> (this is the
 *       window in which the security chain / JWT converter reads it);</li>
 *   <li>the ThreadLocal is ALWAYS cleared after the request — even when the chain
 *       throws — so no value bleeds into the next request on a pooled thread;</li>
 *   <li>a missing / malformed header binds {@code null} (and still clears).</li>
 * </ul>
 */
@Tag("security")
class ActiveTenantHeaderFilterTest {

    private final ActiveTenantHeaderFilter filter = new ActiveTenantHeaderFilter();

    @AfterEach
    void cleanup() {
        // Belt-and-braces: a test failure must not poison sibling tests.
        ActiveTenantHeaderHolder.clear();
    }

    @Test
    void bindsHeaderForTheDurationOfTheChainThenClears() throws Exception {
        UUID tenant = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaimNames.ACTIVE_TENANT_HEADER, tenant.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UUID> seenDuringChain = new AtomicReference<>();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            // The security chain / JWT converter run HERE — the value must be visible.
            seenDuringChain.set(ActiveTenantHeaderHolder.get());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(seenDuringChain.get())
                .as("header must be readable from the ThreadLocal while the chain runs")
                .isEqualTo(tenant);
        // CRITICAL cleanup assertion — no bleed to the next pooled request.
        assertThat(ActiveTenantHeaderHolder.get())
                .as("ThreadLocal must be cleared after the request")
                .isNull();
    }

    @Test
    void clearsThreadLocalEvenWhenTheChainThrows() throws Exception {
        UUID tenant = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaimNames.ACTIVE_TENANT_HEADER, tenant.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            throw new RuntimeException("boom from downstream");
        }).when(chain).doFilter(any(), any());

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException expected) {
            // propagated — fine.
        }

        assertThat(ActiveTenantHeaderHolder.get())
                .as("finally{} must clear the ThreadLocal even on a downstream failure")
                .isNull();
    }

    @Test
    void noHeaderBindsNullAndClearsAnyStaleValue() throws Exception {
        // Simulate a poisoned pooled thread: a stale value left by a prior request.
        ActiveTenantHeaderHolder.set(UUID.randomUUID());

        MockHttpServletRequest request = new MockHttpServletRequest(); // no header
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UUID> seenDuringChain = new AtomicReference<>(UUID.randomUUID());
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            seenDuringChain.set(ActiveTenantHeaderHolder.get());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get())
                .as("a request with no header must NOT inherit a stale ThreadLocal value")
                .isNull();
        assertThat(ActiveTenantHeaderHolder.get()).isNull();
    }

    @Test
    void malformedHeaderIsTreatedAsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaimNames.ACTIVE_TENANT_HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UUID> seenDuringChain = new AtomicReference<>(UUID.randomUUID());
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            seenDuringChain.set(ActiveTenantHeaderHolder.get());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get())
                .as("a malformed header must be treated as absent (null), never throw")
                .isNull();
        assertThat(ActiveTenantHeaderHolder.get()).isNull();
    }
}
