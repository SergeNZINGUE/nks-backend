package bf.laterrasse.nks.dto.billetterie;

import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.Ticket;

import java.util.UUID;

/**
 * Réponse de {@code GET /reservations/{reservationId}/ticket} — expose le vrai {@code qrUuid}
 * de chaque billet de la réservation. Volontairement distinct de {@link ReservationPublicResponse}
 * (retourné par {@code GET /reservations/mes-tickets}) : ce dernier ne doit jamais exposer le
 * qrUuid, secret d'entrée/de vote, à une simple recherche par téléphone.
 *
 * {@code telephoneMasque} : numéro du billet, masqué (2 derniers chiffres seulement) pour que
 * le payeur sache quel billet remettre à qui — jamais le numéro complet.
 */
public record TicketAvecQrResponse(
        UUID ticketId,
        UUID qrUuid,
        String nomSpectateur,
        String statut,
        String telephoneMasque
) {
    public static TicketAvecQrResponse from(Ticket ticket, QRCodeTicket qr) {
        return new TicketAvecQrResponse(
                ticket.getId(),
                qr.getCodeUuid(),
                ticket.getNomSpectateur(),
                ticket.getStatut().name(),
                masquerTelephone(ticket.getTelephoneSpectateur()));
    }

    /** {@code +22670004512} -> {@code +226 •• •• •• 12} ; jamais plus de 2 chiffres en clair, tolère toute entrée. */
    public static String masquerTelephone(String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return "";
        }
        String t = telephone.trim().replaceAll("[\\s-]", "");
        String chiffres = t.replaceAll("[^0-9]", "");
        if (chiffres.length() < 4) {
            return "••";
        }
        String deuxDerniers = chiffres.substring(chiffres.length() - 2);
        if (t.startsWith("+226") && chiffres.length() == 11) {
            return "+226 •• •• •• " + deuxDerniers;
        }
        return "+" + "•".repeat(Math.min(chiffres.length() - 2, 15)) + deuxDerniers;
    }
}
