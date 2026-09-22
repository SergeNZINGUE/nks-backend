package bf.laterrasse.nks.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Grille récapitulative d'une soirée pour la délibération finale (jury + votes en ligne + public). */
public record GrilleDeliberationResponse(
        UUID soireeId,
        String soireeNom,
        Instant soireeDateHeure,
        UUID phaseId,
        String phaseNom,
        /** true si les notes de cette soirée sont clôturées/verrouillées (JuryService.cloturerSoiree). */
        boolean notationCloturee,
        /** Non null si les votes ont été figés via arreterVotes — indique la date de gel. */
        Instant votesArretesLe,
        List<CritereGrilleResponse> criteres,
        List<LigneDeliberationResponse> candidats
) {
}
