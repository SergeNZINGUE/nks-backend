package bf.laterrasse.nks.dto.moment;

import bf.laterrasse.nks.domain.enums.Enums.TypeMoment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Envoi d'un candidat depuis son panel ("Ma Galerie" → "Souvenirs de l'événement") —
 * volontairement sans candidatIds/soireeId/legende : ces champs restent réservés à
 * l'ajout admin/organisateur (cf. CreerMomentAdminRequest), décision produit confirmée.
 */
public record CreerMomentCandidatRequest(
        @NotNull TypeMoment type,
        @NotBlank String publicId,
        @NotBlank String url,
        @Positive long tailleOctets
) {
}
