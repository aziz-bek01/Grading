package uz.hrlab.grading.tenancy.infrastructure;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.tenancy.domain.Tenant;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Provisions tenant-schema tables for a newly-created tenant.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code mode = SHARED} — no-op (tables already exist in
 *       {@code public} from the main Liquibase run; only the
 *       {@code tenant_id} column scopes the rows).</li>
 *   <li>{@code mode = SCHEMA_PER_TENANT} — creates schema
 *       {@code tenant_{slug}} and runs
 *       {@code db/changelog/tenant-schema/db.changelog-tenant.yaml}
 *       against it programmatically.</li>
 * </ul>
 *
 * <p>Production rollout of schema-per-tenant is post-MVP 1 — this class
 * exists so the API is stable from Phase 2 onwards.
 */
@Component
@EnableConfigurationProperties(TenancyProperties.class)
public class TenantSchemaProvisioner {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaProvisioner.class);
    private static final String CHANGELOG =
            "db/changelog/tenant-schema/db.changelog-tenant.yaml";

    private final DataSource dataSource;
    private final TenancyProperties properties;

    public TenantSchemaProvisioner(DataSource dataSource, TenancyProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    public void provision(Tenant tenant) {
        if (properties.getMode() == TenancyProperties.Mode.SHARED) {
            log.info("Tenancy mode SHARED — skipping per-tenant provisioning for {}",
                    tenant.slug());
            return;
        }
        String schemaName = tenant.schemaName();
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalStateException(
                    "Tenant " + tenant.slug() + " has no schema_name");
        }
        try (Connection raw = dataSource.getConnection()) {
            try (Statement st = raw.createStatement()) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + sanitize(schemaName));
            }
            raw.setSchema(schemaName);
            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(raw));
            db.setDefaultSchemaName(schemaName);
            Liquibase liquibase = new Liquibase(CHANGELOG,
                    new ClassLoaderResourceAccessor(), db);
            liquibase.update(new Contexts("mode-shared"), new LabelExpression());
            log.info("Provisioned tenant schema {}", schemaName);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Tenant schema provisioning failed for " + schemaName, ex);
        }
    }

    private String sanitize(String schema) {
        // Tenant schema names must match ^tenant_[a-z0-9_]+$ (database-blueprint §7).
        if (!schema.matches("^tenant_[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        return schema;
    }
}
