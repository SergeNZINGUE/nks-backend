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
        boolean voteSurPlaceActif,
        UUID editionId,
        UUID phaseId,
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
                s.isVoteSurPlaceActif(),
                s.getEdition().getId(),
                s.getPhase().getId(),
                s.getVotesArretesLe(),
                s.isDeliberationVerrouilee());
    }
}
