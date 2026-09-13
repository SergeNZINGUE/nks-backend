package bf.laterrasse.nks.dto.titre;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse de GET /candidats/mon-choix-titre — soirée résolue automatiquement (la plus
 * proche, non terminée/annulée, parmi celles où le candidat est affecté). `soireeId` est
 * `null` si le candidat n'a aucune soirée à venir : dans ce cas `titresDisponibles` est
 * vide et `choixActuel` est `null`.
 */
public record MonChoixTitreResponse(
        UUID soireeId,
        String soireeNom,
        Instant soireeDateHeure,
        String phaseNom,
        Instant dateLimiteChoixTitres,
        List<TitreImposeResponse> titresDisponibles,
        ChoixTitreResponse choixActuel
) {
}
