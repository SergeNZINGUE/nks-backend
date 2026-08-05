package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Partenaire;
import bf.laterrasse.nks.domain.enums.Enums.StatutPartenaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartenaireRepository extends JpaRepository<Partenaire, UUID> {
    List<Partenaire> findByStatut(StatutPartenaire statut);
}
