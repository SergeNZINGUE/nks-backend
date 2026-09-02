package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Vote;
import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    List<Vote> findByCandidatIdAndPhaseId(UUID candidatId, UUID phaseId);

    List<Vote> findByPhaseId(UUID phaseId);

    /** Votes payants confirmés (COMPLETED) hors fraude détectée — source de vérité pour le classement. */
    @Query("""
            SELECT COALESCE(SUM(v.nombreVoix), 0) FROM Vote v
            JOIN VotePayant vp ON vp.vote = v
            WHERE v.candidat.id = :candidatId AND v.phase.id = :phaseId
              AND v.typeVote = 'EN_LIGNE_PAYANT' AND vp.paiement.statut = 'COMPLETED'
              AND vp.fraudeDetectee = false
            """)
    long sommeVoixPayantesConfirmees(UUID candidatId, UUID phaseId);

    @Query("""
            SELECT COALESCE(SUM(v.nombreVoix), 0) FROM Vote v
            JOIN VotePayant vp ON vp.vote = v
            WHERE v.phase.id = :phaseId
              AND v.typeVote = 'EN_LIGNE_PAYANT' AND vp.paiement.statut = 'COMPLETED'
              AND vp.fraudeDetectee = false
            """)
    long totalVoixPayantesConfirmeesPourPhase(UUID phaseId);

    @Query("SELECT COALESCE(SUM(v.nombreVoix), 0) FROM Vote v WHERE v.candidat.id = :candidatId AND v.phase.id = :phaseId AND v.typeVote IN :types")
    long sommeVoixParCandidatEtTypes(UUID candidatId, UUID phaseId, List<TypeVote> types);

    @Query("SELECT COALESCE(SUM(v.nombreVoix), 0) FROM Vote v WHERE v.phase.id = :phaseId AND v.typeVote IN :types")
    long totalVoixPourPhaseEtTypes(UUID phaseId, List<TypeVote> types);

    long countBySourceTelephoneAndTypeVoteAndDateVoteAfter(String sourceTelephone, TypeVote typeVote, Instant since);

    boolean existsBySourceExterneId(String sourceExterneId);
}
