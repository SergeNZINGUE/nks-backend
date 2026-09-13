package bf.laterrasse.nks.dto.titre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** La soirée n'est jamais fournie par le client : elle est résolue côté serveur à partir du candidat connecté. */
public record ChoisirTitreRequest(@NotNull UUID titreImposeId, @NotBlank String titrePersonnel) {
}
