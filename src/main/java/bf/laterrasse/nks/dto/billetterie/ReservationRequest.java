package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * {@code telephoneReservant} = contact du PAYEUR (confirmation de paiement, OTP « Mes tickets »),
 * il n'a pas à porter un billet. Un numéro distinct est exigé PAR billet dans
 * {@code beneficiaires} (taille == nbPlaces, vérifiée côté serveur).
 */
public record ReservationRequest(
        @NotNull UUID soireeId,
        @NotNull UUID categorieId,
        @Min(1) @Max(10) int nbPlaces,
        @NotBlank @Size(max = 150) String nomReservant,
        @NotBlank @Size(max = 30) String telephoneReservant,
        @Email String emailReservant,
        @NotNull @Size(min = 1, max = 10) List<@Valid BeneficiaireBilletRequest> beneficiaires
) {
}
