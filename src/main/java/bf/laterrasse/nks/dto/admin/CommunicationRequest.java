package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CommunicationRequest(
        @NotNull UUID editionId,
        StatutProfilCandidat filtreStatut,   // null = tous les candidats de l'édition
        boolean canalSms,
        boolean canalEmail,
        @NotBlank String message,            // SMS : max 160 caractères (US-35)
        String sujetEmail
) {
}
