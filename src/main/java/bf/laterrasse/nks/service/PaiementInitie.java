package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Paiement;

/** Résultat de la création + démarrage d'un paiement côté gateway (URL non persistée en base). */
public record PaiementInitie(Paiement paiement, String urlPaiement) {
}
