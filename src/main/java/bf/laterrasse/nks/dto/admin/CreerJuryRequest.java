package bf.laterrasse.nks.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreerJuryRequest(
        @NotBlank String prenom,
        @NotBlank String nom,
        @NotBlank @Email String email,
        @NotBlank String telephone,
        String specialite,
        String bioPublique,
        @NotNull UUID editionId
) {
}
