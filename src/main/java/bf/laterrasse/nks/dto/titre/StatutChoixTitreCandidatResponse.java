package bf.laterrasse.nks.dto.titre;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du rapport admin « qui n'a pas encore choisi son titre » (GET
 * /admin/phases/{phaseId}/choix-titres) — une ligne par candidat affecté à une soirée de
 * la phase. `enRetard` est calculé par rapport à Phase.dateLimiteChoixTitres si elle est
 * définie, sinon toujours `false` (purement informatif, jamais bloquant).
 */
public record StatutChoixTitreCandidatResponse(
        UUID candidatId,
        String codeCandidat,
        String nomComplet,
        UUID soireeId,
        String soireeNom,
        boolean choisi,
        String titreImpose,
        String titrePersonnel,
        Instant dateChoix,
        boolean enRetard
) {
}
