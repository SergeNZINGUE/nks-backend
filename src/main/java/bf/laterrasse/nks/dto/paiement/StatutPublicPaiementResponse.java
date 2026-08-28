package bf.laterrasse.nks.dto.paiement;

import bf.laterrasse.nks.domain.Paiement;

import java.math.BigDecimal;
import java.util.UUID;

public record StatutPublicPaiementResponse(
        UUID id,
        String statut,
        BigDecimal montant,
        String typePaiement,
        String motif
) {
    public static StatutPublicPaiementResponse from(Paiement p, String motif) {
        return new StatutPublicPaiementResponse(
                p.getId(),
                p.getStatut().name(),
                p.getMontant(),
                p.getTypePaiement().name(),
                motif);
    }
}
