package bf.laterrasse.nks.dto.titre;

import bf.laterrasse.nks.domain.ChoixTitreCandidat;

import java.time.Instant;
import java.util.UUID;

public record ChoixTitreResponse(UUID titreImposeId, String titreImpose, String titrePersonnel, Instant dateChoix) {
    public static ChoixTitreResponse from(ChoixTitreCandidat c) {
        return new ChoixTitreResponse(c.getTitreImpose().getId(), c.getTitreImpose().getTitre(),
                c.getTitrePersonnel(), c.getDateChoix());
    }
}
