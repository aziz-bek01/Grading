package uz.hrlab.grading.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test — verifies that {@link DevAuthFilter}:
 *   1. refuses to construct under non-dev profiles (hard guard), and
 *   2. binds a populated {@code TenantContext} via headers under dev profile.
 */
class DevAuthFilterTest {

    @Test
    void refusesToStartUnderProductionProfile() {
        MockEnvironment env = new MockEnvironment().withProperty("foo", "bar");
        env.setActiveProfiles("production");

        assertThatThrownBy(() -> new DevAuthFilter(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev");
    }

    @Test
    void refusesToStartWhenNoActiveProfile() {
        MockEnvironment env = new MockEnvironment();
        assertThatThrownBy(() -> new DevAuthFilter(env))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsUnderLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertThat(new DevAuthFilter(env)).isNotNull();
    }

    @Test
    void populatesTenantContextFromHeaders() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        DevAuthFilter filter = new DevAuthFilter(env);

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(DevAuthFilter.HEADER_USER, userId.toString());
        req.addHeader(DevAuthFilter.HEADER_TENANT, tenantId.toString());
        req.addHeader(DevAuthFilter.HEADER_PROJECTS, projectId.toString());
        req.addHeader(DevAuthFilter.HEADER_ROLES, "HRLAB_SUPER_ADMIN,HRLAB_PROJECT_MANAGER");
        req.addHeader(DevAuthFilter.HEADER_PERMISSIONS, "TENANT_CREATE,TENANT_READ");
        req.addHeader(DevAuthFilter.HEADER_LOCALE, "ru-RU");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        SecurityContextHolder.clearContext();
        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(DevAuthentication.class);
        DevAuthentication dev = (DevAuthentication) auth;
        assertThat(dev.getTenantContext().userId()).isEqualTo(userId);
        assertThat(dev.getTenantContext().tenantId()).isEqualTo(tenantId);
        assertThat(dev.getTenantContext().projectIds()).containsExactly(projectId);
        assertThat(dev.getTenantContext().roles())
                .containsExactlyInAnyOrder("HRLAB_SUPER_ADMIN", "HRLAB_PROJECT_MANAGER");
        assertThat(dev.getTenantContext().permissions())
                .containsExactlyInAnyOrder("TENANT_CREATE", "TENANT_READ");
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInvalidUserHeader() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        DevAuthFilter filter = new DevAuthFilter(env);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(DevAuthFilter.HEADER_USER, "not-a-uuid");
        MockHttpServletResponse res = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(400);
    }
}
