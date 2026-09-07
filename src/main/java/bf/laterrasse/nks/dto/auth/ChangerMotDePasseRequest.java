package bf.laterrasse.nks.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangerMotDePasseRequest(
        @NotBlank String motDePasseActuel,
        @NotBlank @Size(min = 8) String nouveauMotDePasse
) {}
