package uz.hrlab.grading.common.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import uz.hrlab.grading.access.application.PermissionService;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson {@link BeanSerializerModifier} that strips DTO properties annotated
 * with {@link Sensitive} when the current {@link TenantContext} does NOT carry
 * the required permission (security-blueprint §8, finding F-12).
 *
 * <p>Strategy: wrap each {@link BeanPropertyWriter} for a sensitive property
 * in a delegating writer that consults
 * {@link PermissionService#has(String)} per-serialization. When the check
 * fails, the writer is a no-op — the field key never appears in the JSON
 * output (per design-foundation §10 — omitted, not nulled).
 *
 * <p>Failure modes:
 * <ul>
 *   <li>No active context → omit (default-deny).</li>
 *   <li>Context but missing permission → omit.</li>
 *   <li>Context with permission → include normally.</li>
 * </ul>
 */
public class SensitiveFieldSerializerModifier extends BeanSerializerModifier {

    private final PermissionService permissionService;

    public SensitiveFieldSerializerModifier(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        List<BeanPropertyWriter> out = new ArrayList<>(beanProperties.size());
        for (BeanPropertyWriter writer : beanProperties) {
            AnnotatedMember member = writer.getMember();
            Sensitive sensitive = member == null ? null : member.getAnnotation(Sensitive.class);
            if (sensitive == null) {
                out.add(writer);
            } else {
                out.add(new PermissionGatedWriter(writer, sensitive.requires(), permissionService));
            }
        }
        return out;
    }

    /** Wraps a writer so the property is omitted when the permission gate fails. */
    private static final class PermissionGatedWriter extends BeanPropertyWriter {

        private final String requiredPermission;
        private final transient PermissionService permissionService;

        PermissionGatedWriter(BeanPropertyWriter base, String requiredPermission,
                              PermissionService permissionService) {
            super(base);
            this.requiredPermission = requiredPermission;
            this.permissionService = permissionService;
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov)
                throws Exception {
            if (allowed()) {
                super.serializeAsField(bean, gen, prov);
            }
            // else: omit the field entirely — no key, no null, no placeholder.
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov)
                throws Exception {
            if (allowed()) {
                super.serializeAsElement(bean, gen, prov);
            } else {
                // Array context still needs a slot — emit null to preserve indexes.
                prov.defaultSerializeNull(gen);
            }
        }

        private boolean allowed() {
            TenantContext ctx = TenantContextHolder.get();
            if (ctx == null) return false;
            // SALARY_VIEW additionally requires the salary_data_permission flag.
            if ("SALARY_VIEW".equals(requiredPermission)) {
                return permissionService.canViewSalary();
            }
            return permissionService.has(requiredPermission);
        }
    }
}
