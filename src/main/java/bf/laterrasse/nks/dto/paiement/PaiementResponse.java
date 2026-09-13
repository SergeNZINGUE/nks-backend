package bf.laterrasse.nks.dto.paiement;

import bf.laterrasse.nks.domain.Paiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaiementResponse(
        UUID id,
        UUID utilisateurId,
        String prenomCandidat,
        String nomCandidat,
        String emailCandidat,
        String typePaiement,
        BigDecimal montant,
        String statut,
        Instant dateCreation,
        Instant dateFinalisation,
        String referenceExterne,
        boolean manuel
) {
    public static PaiementResponse from(Paiement p) {
        var u = p.getUtilisateur();
        return new PaiementResponse(
                p.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getPrenom() : null,
                u != null ? u.getNom() : null,
                u != null ? u.getEmail() : null,
                p.getTypePaiement().name(),
                p.getMontant(),
                p.getStatut().name(),
                p.getDateCreation(),
                p.getDateFinalisation(),
                p.getReferenceExterne(),
                p.isManuel());
    }
}
