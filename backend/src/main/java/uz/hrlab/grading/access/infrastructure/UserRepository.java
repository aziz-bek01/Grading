package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Control-plane users — not tenant business data, so {@code findById} is permitted. */
public interface UserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmailIgnoreCase(String email);

    Optional<UserJpaEntity> findByExternalIdpSubject(String externalIdpSubject);
}
