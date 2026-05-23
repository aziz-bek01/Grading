package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@code public.role_permissions} (database-blueprint §5.1). */
@Embeddable
public class RolePermissionId implements Serializable {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    protected RolePermissionId() { }

    public RolePermissionId(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public UUID getRoleId() { return roleId; }
    public UUID getPermissionId() { return permissionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionId other)) return false;
        return Objects.equals(roleId, other.roleId) && Objects.equals(permissionId, other.permissionId);
    }

    @Override
    public int hashCode() { return Objects.hash(roleId, permissionId); }
}
