package bf.laterrasse.nks.dto.moment;

import bf.laterrasse.nks.domain.enums.Enums.TypeMoment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Ajout direct par un admin/organisateur — publié immédiatement (l'admin EST le
 * validateur, jamais de file d'attente). candidatIds vide/null → crédit public
 * "Équipe NKS" ; sinon un ou plusieurs candidats visibles sur le média (photo de groupe).
 */
public record CreerMomentAdminRequest(
        @NotNull TypeMoment type,
        @NotBlank String publicId,
        @NotBlank String url,
        @Positive long tailleOctets,
        UUID soireeId,
        List<UUID> candidatIds,
        @Size(max = 140) String legende
) {
}
