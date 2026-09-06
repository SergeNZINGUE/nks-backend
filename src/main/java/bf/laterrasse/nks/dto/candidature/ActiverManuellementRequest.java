package bf.laterrasse.nks.dto.candidature;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ActiverManuellementRequest(
        @Size(max = 500, message = "La référence ne peut excéder 500 caractères") String referenceReglement,
        @DecimalMin(value = "0.01", message = "Le montant doit être positif") BigDecimal montant
) {}