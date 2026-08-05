package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.enums.Enums.StatutCandidature;
import bf.laterrasse.nks.domain.enums.Enums.StatutPaiement;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.dto.admin.DashboardResponse;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** US-34 — Tableau de bord admin. */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final EditionRepository editionRepository;
    private final CandidatureRepository candidatureRepository;
    private final CandidatRepository candidatRepository;
    private final PhaseRepository phaseRepository;
    private final VoteService voteService;
    private final PaiementRepository paiementRepository;
    private final SoireeEventRepository soireeEventRepository;
    private final CategorieTicketRepository categorieTicketRepository;

    public DashboardResponse construire() {
        Edition edition = editionRepository.findByStatut(bf.laterrasse.nks.domain.enums.Enums.StatutEdition.EN_COURS)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune édition en cours"));

        long total = candidatRepository.countByEditionId(edition.getId());
        long valides = candidatureRepository.findAll().stream()
                .filter(c -> c.getEdition().getId().equals(edition.getId()))
                .filter(c -> c.getStatut() == StatutCandidature.ACTIVE).count();
        long enAttente = candidatureRepository.findAll().stream()
                .filter(c -> c.getEdition().getId().equals(edition.getId()))
                .filter(c -> c.getStatut() == StatutCandidature.EN_ATTENTE
                        || c.getStatut() == StatutCandidature.EN_ATTENTE_PAIEMENT).count();
        long rejetees = candidatureRepository.findAll().stream()
                .filter(c -> c.getEdition().getId().equals(edition.getId()))
                .filter(c -> c.getStatut() == StatutCandidature.REJETEE).count();

        List<Phase> phases = phaseRepository.findByEditionIdOrderByOrdreAsc(edition.getId());
        Map<String, Long> votesParPhase = phases.stream().collect(Collectors.toMap(
                p -> p.getNom().name(),
                p -> voteService.totalVotesPayantsConfirmes(p.getId()) + voteService.totalVotesSurPlace(p.getId())));

        BigDecimal revenusInscriptions = sommePaiements(TypePaiement.INSCRIPTION);
        BigDecimal revenusVotes = sommePaiements(TypePaiement.VOTE);
        BigDecimal revenusBillets = sommePaiements(TypePaiement.BILLET);

        List<SoireeEvent> soirees = soireeEventRepository.findByEditionId(edition.getId());
        double tauxRemplissage = soirees.stream()
                .flatMap(s -> categorieTicketRepository.findBySoireeId(s.getId()).stream())
                .mapToDouble(c -> c.getNbPlacesDisponibles() == 0 ? 0
                        : (double) c.getNbPlacesReservees() / c.getNbPlacesDisponibles() * 100)
                .average().orElse(0);

        return new DashboardResponse(total, valides, enAttente, rejetees, votesParPhase,
                revenusInscriptions, revenusVotes, revenusBillets, tauxRemplissage);
    }

    private BigDecimal sommePaiements(TypePaiement type) {
        return paiementRepository.findAll().stream()
                .filter(p -> p.getTypePaiement() == type && p.getStatut() == StatutPaiement.COMPLETED)
                .map(bf.laterrasse.nks.domain.Paiement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
