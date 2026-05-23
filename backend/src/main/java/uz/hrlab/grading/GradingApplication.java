package uz.hrlab.grading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap class for the grading.hrlab.uz modular monolith.
 *
 * <p>Architecture: see /архитектура.md (§11) and /docs/mvp1/05-database-blueprint.md.
 * Modules live under {@code uz.hrlab.grading.<module>} with strict
 * api/application/domain/infrastructure layering.
 */
@SpringBootApplication
public class GradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GradingApplication.class, args);
    }
}
