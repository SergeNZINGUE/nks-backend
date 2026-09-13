package bf.laterrasse.nks.dto.titre;

import bf.laterrasse.nks.domain.TitreImpose;

import java.util.UUID;

public record TitreImposeResponse(UUID id, String titre, short ordre) {
    public static TitreImposeResponse from(TitreImpose t) {
        return new TitreImposeResponse(t.getId(), t.getTitre(), t.getOrdre());
    }
}
