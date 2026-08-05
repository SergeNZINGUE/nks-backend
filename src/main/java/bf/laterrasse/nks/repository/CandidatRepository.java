package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidatRepository extends JpaRepository<Candidat, UUID> {

    Page<Candidat> findByEditionIdAndStatutProfil(UUID editionId, StatutProfilCandidat statutProfil, Pageable pageable);

    Optional<Candidat> findByEditionIdAndCodeCandidat(UUID editionId, String codeCandidat);

    Optional<Candidat> findByUtilisateurId(UUID utilisateurId);

    long countByEditionId(UUID editionId);

    @Query("SELECT c FROM Candidat c JOIN FETCH c.utilisateur WHERE c.edition.id = :editionId")
    List<Candidat> findByEditionIdWithUtilisateur(@Param("editionId") UUID editionId);

    @Query("SELECT c FROM Candidat c JOIN FETCH c.utilisateur WHERE c.edition.id = :editionId AND c.statutProfil = :statut")
    List<Candidat> findByEditionIdAndStatutWithUtilisateur(@Param("editionId") UUID editionId, @Param("statut") StatutProfilCandidat statut);

    @org.springframework.data.jpa.repository.Query(
            "SELECT c.codeCandidat FROM Candidat c WHERE c.edition.id = :editionId ORDER BY c.codeCandidat DESC LIMIT 1")
    Optional<String> findDernierCodeCandidat(UUID editionId);
}
