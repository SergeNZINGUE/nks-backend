package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifierRequest(
        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        @Pattern(regexp = "^\\+?[0-9\\s-]{8,20}$", message = "Format de numéro de téléphone invalide")
        String telephone,
        @NotBlank(message = "Le code est obligatoire") String code
) {
}
