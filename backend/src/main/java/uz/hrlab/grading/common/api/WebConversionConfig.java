package uz.hrlab.grading.common.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC-layer type converters for request binding.
 *
 * <p>Adds {@link BlankSafeUuidConverter} so blank optional UUID request params
 * bind to {@code null} (= "no filter") instead of crashing the request with a
 * PostgreSQL {@code 22P02} that the global handler turns into a confusing 409.
 *
 * <p>Guarded by {@link ConditionalOnWebApplication} so the Liquibase migrator
 * run (web-application-type=none) does not attempt to wire MVC infrastructure.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebConversionConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new BlankSafeUuidConverter());
    }
}
