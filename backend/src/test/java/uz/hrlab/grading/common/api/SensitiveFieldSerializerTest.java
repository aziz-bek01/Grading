package uz.hrlab.grading.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.application.PermissionService;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link Sensitive} annotation contract: when the active context
 * lacks the required permission the field is OMITTED from the JSON output
 * (security-blueprint §8, finding F-12). Field is not nulled, not masked —
 * it must not appear in the payload at all (design-foundation §10).
 */
@Tag("salary")
class SensitiveFieldSerializerTest {

    private final PermissionService permissionService = new PermissionService();
    private final ObjectMapper mapper = newMapper();

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void sensitiveFieldIsOmittedWhenContextLacksSalaryView() throws Exception {
        setContext(Set.of("POSITION_READ"), /*salary*/ false);

        String json = mapper.writeValueAsString(new SalaryDto("Engineer",
                new BigDecimal("1000"), new BigDecimal("2000")));

        assertThat(json).contains("\"title\":\"Engineer\"");
        assertThat(json).doesNotContain("min").doesNotContain("max");
        // belt and braces: also no numeric fragment
        assertThat(json).doesNotContain("1000").doesNotContain("2000");
    }

    @Test
    void sensitiveFieldIsIncludedWhenContextHasSalaryViewAndFlag() throws Exception {
        setContext(Set.of(PermissionCodes.SALARY_VIEW), /*salary*/ true);

        String json = mapper.writeValueAsString(new SalaryDto("Engineer",
                new BigDecimal("1000"), new BigDecimal("2000")));

        assertThat(json).contains("\"min\":1000").contains("\"max\":2000");
    }

    @Test
    void sensitiveFieldIsOmittedWhenSalaryPermissionFlagMissing() throws Exception {
        // Has SALARY_VIEW permission, but salary_data_permission flag is false
        // → still denied (defense-in-depth, TC-SAL-FND-010).
        setContext(Set.of(PermissionCodes.SALARY_VIEW), /*salary*/ false);

        String json = mapper.writeValueAsString(new SalaryDto("Engineer",
                new BigDecimal("1000"), new BigDecimal("2000")));

        assertThat(json).doesNotContain("min").doesNotContain("max");
    }

    @Test
    void sensitiveFieldIsOmittedWhenNoActiveContext() throws Exception {
        // No context bound (e.g. anonymous request)
        String json = mapper.writeValueAsString(new SalaryDto("Engineer",
                new BigDecimal("1000"), new BigDecimal("2000")));

        assertThat(json).doesNotContain("min").doesNotContain("max");
    }

    private ObjectMapper newMapper() {
        ObjectMapper om = new ObjectMapper();
        SimpleModule m = new SimpleModule("test-sensitive");
        m.setSerializerModifier(new SensitiveFieldSerializerModifier(permissionService));
        om.registerModule(m);
        return om;
    }

    private void setContext(Set<String> permissions, boolean salaryFlag) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(),
                Set.of(), Set.of(), permissions, Set.of(),
                salaryFlag, "ru-RU"));
    }

    /** DTO with one normal field and two sensitive fields. */
    public record SalaryDto(
            String title,
            @Sensitive(requires = "SALARY_VIEW") BigDecimal min,
            @Sensitive(requires = "SALARY_VIEW") BigDecimal max
    ) { }
}
