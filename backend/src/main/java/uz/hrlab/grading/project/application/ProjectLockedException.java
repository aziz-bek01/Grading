package uz.hrlab.grading.project.application;

import uz.hrlab.grading.common.exception.BaseDomainException;

/**
 * 409 Conflict — attempt to mutate a project that is {@code LOCKED}
 * (architecture §14). Service layer raises this; controller advice maps to 409.
 */
public class ProjectLockedException extends BaseDomainException {
    public ProjectLockedException() {
        super("PROJECT_LOCKED", "Project is locked; business edits are not permitted");
    }
}
