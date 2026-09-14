package bf.laterrasse.nks.gateway.email;

import java.util.List;

/** Abstraction e-mail (SMTP — Gmail SMTP ou Sendgrid). */
public interface EmailGateway {
    void envoyer(String destinataire, String sujet, String corpsHtml);

    /**
     * Surcharge avec pièces jointes (ex. images QR des billets) — ne remplace pas la
     * méthode existante (7+ appelants sans pièce jointe) ; {@code envoyer(destinataire,
     * sujet, corpsHtml)} délègue à celle-ci avec une liste vide.
     */
    void envoyer(String destinataire, String sujet, String corpsHtml, List<PieceJointe> piecesJointes);

    /** Pièce jointe binaire en mémoire — nom de fichier, contenu brut, type MIME. */
    record PieceJointe(String nomFichier, byte[] contenu, String typeMime) {
    }
}
