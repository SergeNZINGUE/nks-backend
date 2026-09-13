package bf.laterrasse.nks.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Corps de PUT /admin/criteres-notation/{id}. Pas de suppression définitive possible
 * (notes_jury.critere_id référence ce critère sans ON DELETE CASCADE — un critère déjà
 * utilisé dans des notes ne peut pas être supprimé sans violer la contrainte FK) : `actif`
 * permet de le retirer des grilles de notation futures sans casser l'historique.
 */
public record MettreAJourCritereNotationRequest(
        @NotBlank String nom,
        BigDecimal noteMin,
        @NotNull BigDecimal noteMax,
        @NotNull Short ordre,
        @NotNull Boolean actif
) {
}
