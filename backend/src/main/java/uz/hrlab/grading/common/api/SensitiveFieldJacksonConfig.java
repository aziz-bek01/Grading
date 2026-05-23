package uz.hrlab.grading.common.api;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.hrlab.grading.access.application.PermissionService;

/**
 * Registers the {@link SensitiveFieldSerializerModifier} on Spring's primary
 * {@code ObjectMapper}. Once installed, every {@code @Sensitive}-annotated
 * field is automatically omitted from JSON responses for callers lacking the
 * required permission (security-blueprint §8, finding F-12).
 */
@Configuration
public class SensitiveFieldJacksonConfig {

    @Bean
    public Module sensitiveFieldModule(PermissionService permissionService) {
        SimpleModule module = new SimpleModule("grading-sensitive-field-module");
        module.setSerializerModifier(new SensitiveFieldSerializerModifier(permissionService));
        return module;
    }
}
