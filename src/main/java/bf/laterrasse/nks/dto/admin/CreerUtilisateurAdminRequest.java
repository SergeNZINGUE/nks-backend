package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import jakarta.validation.constraints.*;

public record CreerUtilisateurAdminRequest(
        @NotBlank String prenom,
        @NotBlank String nom,
        @NotBlank @Email String email,
        @NotBlank String telephone,
        @NotNull RoleName role
) {}
