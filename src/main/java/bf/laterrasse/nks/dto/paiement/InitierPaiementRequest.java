package bf.laterrasse.nks.dto.paiement;

import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InitierPaiementRequest(
        @NotNull TypePaiement typePaiement,
        @NotNull @Positive BigDecimal montant,
        @NotBlank String telephone
) {
}
