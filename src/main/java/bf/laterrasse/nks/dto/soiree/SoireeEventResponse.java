package bf.laterrasse.nks.dto.soiree;

import bf.laterrasse.nks.domain.SoireeEvent;

import java.time.Instant;
import java.util.UUID;

public record SoireeEventResponse(
        UUID id,
        String nom,
        Instant dateHeure,
        String lieu,
        String adresse,
        Integer capaciteMax,
        String statut,
        String typeSoiree,
        boolean voteSurPlaceActif,
        boolean resultatsPublies,
        Short nbConsommationsPourVoteBonus,
        Short plafondVotesBonus,
        String imageUrl,
        UUID editionId,
        UUID phaseId,
        String nomPhase,
        int nombrePassages,
        Instant votesArretesLe,
        boolean deliberationVerrouilee
) {
    public static SoireeEventResponse from(SoireeEvent s) {
        return new SoireeEventResponse(
                s.getId(),
                s.getNom(),
                s.getDateHeure(),
                s.getLieu(),
                s.getAdresse(),
                s.getCapaciteMax(),
                s.getStatut().name(),
                s.getTypeSoiree().name(),
                s.isVoteSurPlaceActif(),
                s.isResultatsPublies(),
                s.getNbConsommationsPourVoteBonus(),
                s.getPlafondVotesBonus(),
                s.getImageUrl(),
                s.getEdition().getId(),
                s.getPhase() != null ? s.getPhase().getId() : null,
                s.getPhase() != null ? s.getPhase().getNom().name() : null,
                s.getPhase() != null ? s.getPhase().getNombrePassages() : 0,
                s.getVotesArretesLe(),
                s.isDeliberationVerrouilee());
    }
}
