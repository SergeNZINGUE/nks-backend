package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CommunicationRequest(
        @NotNull UUID editionId,
        StatutProfilCandidat filtreStatut,   // null = tous les candidats (ignoré si ciblePartenaires=true)
        boolean ciblePartenaires,            // true → envoyer aux partenaires ACTIFS plutôt qu'aux candidats
        boolean canalSms,
        boolean canalEmail,
        boolean canalWhatsapp,
        @NotBlank String message,            // SMS : max 160 caractères / e-mail : libre
        String sujetEmail,
        String templateWhatsapp,             // clé Meta (ex: "karaoke_info") — null si canalWhatsapp=false
        List<String> variablesWhatsapp       // variables du template dans l'ordre — null ou vide pour karaoke_accepted
) {
}
