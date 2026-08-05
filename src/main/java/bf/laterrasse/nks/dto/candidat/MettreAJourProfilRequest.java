package bf.laterrasse.nks.dto.candidat;

import jakarta.validation.constraints.Size;

/** US-11 — le candidat ne peut modifier que sa bio ; sa photo passe par le flux médias dédié. */
public record MettreAJourProfilRequest(
        @Size(max = 2000) String biographie
) {
}
