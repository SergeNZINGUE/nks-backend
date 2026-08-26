package bf.laterrasse.nks.dto.jury;

import bf.laterrasse.nks.domain.NoteJury;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NoteJuryResponse(
        UUID id,
        UUID juryId,
        UUID candidatId,
        UUID soireeId,
        UUID critereId,
        String critereNom,
        BigDecimal valeur,
        boolean verrouille,
        Instant dateSaisie
) {
    public static NoteJuryResponse from(NoteJury n) {
        return new NoteJuryResponse(
                n.getId(),
                n.getJury().getId(),
                n.getCandidat().getId(),
                n.getSoiree().getId(),
                n.getCritere().getId(),
                n.getCritere().getNom(),
                n.getValeur(),
                n.isVerrouille(),
                n.getDateSaisie());
    }
}
