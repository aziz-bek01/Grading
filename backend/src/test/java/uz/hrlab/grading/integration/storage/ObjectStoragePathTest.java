package uz.hrlab.grading.integration.storage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SECURITY TEST — tenant/project namespace enforcement
 * (integration-blueprint §7.1).
 */
@Tag("security")
@Tag("tenant-isolation")
class ObjectStoragePathTest {

    @Test
    void importPathFollowsCanonicalNamespace() {
        UUID t = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String path = ObjectStoragePath.forImportOriginal(t, p, b);
        assertThat(path).isEqualTo("tenants/" + t + "/projects/" + p + "/imports/" + b + "/original.xlsx");
        ObjectStoragePath.validate(path); // does not throw
    }

    @Test
    void exportPathFollowsCanonicalNamespace() {
        UUID t = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        UUID j = UUID.randomUUID();
        String path = ObjectStoragePath.forExportResult(t, p, j, "xlsx");
        assertThat(path).startsWith("tenants/" + t + "/projects/" + p + "/exports/" + j + "/");
        ObjectStoragePath.validate(path);
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> ObjectStoragePath.validate(
                "tenants/" + UUID.randomUUID() + "/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absolutePathIsRejected() {
        assertThatThrownBy(() -> ObjectStoragePath.validate(
                "/tenants/" + UUID.randomUUID() + "/projects/" + UUID.randomUUID() + "/x.xlsx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathWithoutTenantPrefixIsRejected() {
        assertThatThrownBy(() -> ObjectStoragePath.validate("imports/" + UUID.randomUUID() + "/original.xlsx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTenantRejected() {
        assertThatThrownBy(() -> ObjectStoragePath.forImportOriginal(null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullProjectRejected() {
        assertThatThrownBy(() -> ObjectStoragePath.forImportOriginal(UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
