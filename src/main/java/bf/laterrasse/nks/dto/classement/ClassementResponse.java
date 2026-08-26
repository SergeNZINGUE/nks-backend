package bf.laterrasse.nks.dto.classement;

import bf.laterrasse.nks.domain.Classement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClassementResponse(
        UUID id,
        UUID candidatId,
        String codeCandidat,
        UUID editionId,
        BigDecimal totalPointsCumules,
        Integer rangGlobal,
        boolean officiel,
        Instant dateDerniereMiseAJour
) {
    public static ClassementResponse from(Classement c) {
        return new ClassementResponse(
                c.getId(),
                c.getCandidat().getId(),
                c.getCandidat().getCodeCandidat(),
                c.getEdition().getId(),
                c.getTotalPointsCumules(),
                c.getRangGlobal(),
                c.isOfficiel(),
                c.getDateDerniereMiseAJour());
    }
}
