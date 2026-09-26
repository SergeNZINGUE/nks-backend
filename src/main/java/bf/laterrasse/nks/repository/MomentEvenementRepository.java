package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.MomentEvenement;
import bf.laterrasse.nks.domain.enums.Enums.StatutMedia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MomentEvenementRepository extends JpaRepository<MomentEvenement, UUID> {

    List<MomentEvenement> findByCandidatUploadeurIdOrderByDateUploadDesc(UUID candidatId);

    List<MomentEvenement> findByStatutOrderByDateUploadAsc(StatutMedia statut);

    Page<MomentEvenement> findByStatutOrderByDateUploadDesc(StatutMedia statut, Pageable pageable);

    long countByStatut(StatutMedia statut);
}
