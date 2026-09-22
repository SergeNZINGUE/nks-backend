package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoteSnapshotService {

    private final SoireeEventRepository soireeEventRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;
    private final VoteRepository voteRepository;
    private final VoteService voteService;
    private final SnapshotVotesSoireeRepository snapshotRepository;

    @Transactional
    public void arreterVotes(UUID soireeId) {
        SoireeEvent soiree = soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));
        if (soiree.getVotesArretesLe() != null) {
            throw new ConflitEtatException("Les votes de cette soirée sont déjà arrêtés");
        }

        Phase phase = soiree.getPhase();
        List<Candidat> candidats = resolverCandidats(soireeId);

        long totalVoixPayantesPhase = voteService.totalVotesPayantsConfirmes(phase.getId());
        long totalVoixSurPlaceSoiree = candidats.stream()
                .mapToLong(c -> voteService.votesSurPlace(c.getId(), phase.getId()))
                .sum();

        List<SnapshotVotesSoiree> snapshots = candidats.stream().map(candidat -> {
            long voixPayantes = voteService.votesPayantsConfirmes(candidat.getId(), phase.getId());
            long likes = voteRepository.sommeVoixParCandidatEtTypes(candidat.getId(), phase.getId(), List.of(TypeVote.SOCIAL_LIKE));
            long commentaires = voteRepository.sommeVoixParCandidatEtTypes(candidat.getId(), phase.getId(), List.of(TypeVote.SOCIAL_COMMENTAIRE));
            long surPlace = voteService.votesSurPlace(candidat.getId(), phase.getId());

            SnapshotVotesSoiree snap = snapshotRepository
                    .findBySoireeIdAndCandidatId(soireeId, candidat.getId())
                    .orElse(SnapshotVotesSoiree.builder().soiree(soiree).candidat(candidat).build());
            snap.setVoixPayantes(voixPayantes);
            snap.setVoixSocialesLikes(likes);
            snap.setVoixSocialesCommentaires(commentaires);
            snap.setVoixSurPlace(surPlace);
            snap.setTotalVoixPayantesPhase(totalVoixPayantesPhase);
            snap.setTotalVoixSurPlaceSoiree(totalVoixSurPlaceSoiree);
            snap.setDateSnapshot(Instant.now());
            return snap;
        }).toList();

        snapshotRepository.saveAll(snapshots);
        soiree.setVotesArretesLe(Instant.now());
        soireeEventRepository.save(soiree);
    }

    public List<Candidat> resolverCandidats(UUID soireeId) {
        List<AffectationPoule> viaPoule = affectationPouleRepository.findByPouleSoireeId(soireeId);
        List<Duo> viaDuo = duoRepository.findBySoireeId(soireeId);
        return java.util.stream.Stream.concat(
                viaPoule.stream().map(AffectationPoule::getCandidat),
                viaDuo.stream().flatMap(d -> java.util.stream.Stream.of(d.getCandidat1(), d.getCandidat2()))
        ).distinct().toList();
    }
}
