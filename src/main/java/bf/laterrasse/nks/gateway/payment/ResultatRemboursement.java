package bf.laterrasse.nks.gateway.payment;

public record ResultatRemboursement(boolean succes, String referenceRemboursement, String message) {
}
