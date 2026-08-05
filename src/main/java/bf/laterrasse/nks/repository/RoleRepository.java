package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Role;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNom(RoleName nom);
}
