package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_permissions", schema = "public")
public class RolePermissionJpaEntity {

    @EmbeddedId
    private RolePermissionId id;

    protected RolePermissionJpaEntity() { }

    public RolePermissionJpaEntity(RolePermissionId id) {
        this.id = id;
    }

    public RolePermissionId getId() { return id; }
}
