package bf.laterrasse.nks.event;

import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Publié par PaiementService une fois un paiement webhook confirmé COMPLETED.
 * Chaque module concerné (Candidature, Vote, Billetterie) réagit indépendamment —
 * évite les dépendances circulaires entre services (cf. ADR-07, monolithe modulaire).
 */
public record PaiementConfirmeEvent(
        UUID paiementId,
        TypePaiement typePaiement,
        UUID utilisateurId,
        BigDecimal montant,
        String referenceExterne
) {
}
