package uz.hrlab.grading.project.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.api.Pagination;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class FindProjectQuery {

    private final ProjectRepository projects;
    private final AbacGate abacGate;

    public FindProjectQuery(ProjectRepository projects, AbacGate abacGate) {
        this.projects = projects;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public Project findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ProjectJpaEntity entity = projects.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // ABAC: ProjectMembershipPolicy + ApprovedEntityFilterPolicy
        abacGate.enforceCanReadProject(ctx, entity.getId(), entity.getStatus());
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public Page<Project> list(int page, int size) {
        TenantContext ctx = TenantContextHolder.requireActive();
        int safeSize = Pagination.clampSize(size);
        Page<ProjectJpaEntity> raw = projects.findAllByTenantIdAndStatusNot(
                ctx.tenantId(), ProjectStatus.ARCHIVED,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("code").ascending()));
        return raw.map(ProjectJpaEntity::toDomain);
    }
}
