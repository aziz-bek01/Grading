package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.util.UUID;

@Entity
@Table(name = "roles", schema = "public")
public class RoleJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private RoleScope scope;

    protected RoleJpaEntity() { }

    public RoleJpaEntity(UUID id, String code, String name, RoleScope scope) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.scope = scope;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public RoleScope getScope() { return scope; }
}
