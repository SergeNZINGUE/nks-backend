package bf.laterrasse.nks.dto.billetterie;

import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.Ticket;

import java.util.UUID;

/**
 * Réponse de {@code GET /reservations/{reservationId}/ticket} — expose le vrai {@code qrUuid}
 * de chaque billet de la réservation. Volontairement distinct de {@link ReservationPublicResponse}
 * (retourné par {@code GET /reservations/mes-tickets}) : ce dernier ne doit jamais exposer le
 * qrUuid, secret d'entrée/de vote, à une simple recherche par téléphone.
 */
public record TicketAvecQrResponse(
        UUID ticketId,
        UUID qrUuid,
        String nomSpectateur,
        String statut
) {
    public static TicketAvecQrResponse from(Ticket ticket, QRCodeTicket qr) {
        return new TicketAvecQrResponse(
                ticket.getId(),
                qr.getCodeUuid(),
                ticket.getNomSpectateur(),
                ticket.getStatut().name());
    }
}
