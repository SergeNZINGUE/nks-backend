package bf.laterrasse.nks.dto.billetterie;

import bf.laterrasse.nks.domain.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationPublicResponse(
        UUID id,
        UUID soireeId,
        String nomSoiree,
        UUID paiementId,
        String telephoneReservant,
        String nomReservant,
        String emailReservant,
        Integer nbPlaces,
        BigDecimal montantTotal,
        String statut,
        Instant dateReservation,
        boolean gratuit
) {
    public static ReservationPublicResponse from(Reservation r) {
        return new ReservationPublicResponse(
                r.getId(),
                r.getSoiree().getId(),
                r.getSoiree().getNom(),
                r.getPaiement() != null ? r.getPaiement().getId() : null,
                r.getTelephoneReservant(),
                r.getNomReservant(),
                r.getEmailReservant(),
                r.getNbPlaces(),
                r.getMontantTotal(),
                r.getStatut().name(),
                r.getDateReservation(),
                r.isGratuit());
    }
}
