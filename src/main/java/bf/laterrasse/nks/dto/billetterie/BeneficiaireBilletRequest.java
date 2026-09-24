package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Personne (numéro de téléphone) à qui sera remis un billet — un par place réservée. */
public record BeneficiaireBilletRequest(
        @NotBlank @Size(max = 30) String telephone,
        @Size(max = 150) String nom
) {
}
