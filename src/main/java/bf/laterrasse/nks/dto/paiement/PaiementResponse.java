package bf.laterrasse.nks.dto.paiement;

import bf.laterrasse.nks.domain.Paiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaiementResponse(
        UUID id,
        UUID utilisateurId,
        String typePaiement,
        BigDecimal montant,
        String statut,
        Instant dateCreation,
        Instant dateFinalisation,
        String referenceExterne,
        boolean manuel
) {
    public static PaiementResponse from(Paiement p) {
        return new PaiementResponse(
                p.getId(),
                p.getUtilisateur() != null ? p.getUtilisateur().getId() : null,
                p.getTypePaiement().name(),
                p.getMontant(),
                p.getStatut().name(),
                p.getDateCreation(),
                p.getDateFinalisation(),
                p.getReferenceExterne(),
                p.isManuel());
    }
}
