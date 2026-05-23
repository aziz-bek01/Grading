package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.util.UUID;

@Entity
@Table(name = "permissions", schema = "public")
public class PermissionJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "resource", length = 64)
    private String resource;

    @Column(name = "action", length = 32)
    private String action;

    @Column(name = "description", length = 500)
    private String description;

    protected PermissionJpaEntity() { }

    public PermissionJpaEntity(UUID id, String code, String resource, String action, String description) {
        this.id = id;
        this.code = code;
        this.resource = resource;
        this.action = action;
        this.description = description;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getResource() { return resource; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
}
