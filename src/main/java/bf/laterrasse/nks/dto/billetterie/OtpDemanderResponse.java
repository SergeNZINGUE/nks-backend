package bf.laterrasse.nks.dto.billetterie;

/** Message volontairement générique — ne révèle jamais si le numéro a des réservations. */
public record OtpDemanderResponse(String message) {

    private static final String MESSAGE_GENERIQUE =
            "Si ce numéro a des réservations, un code de vérification a été envoyé.";

    public static OtpDemanderResponse generique() {
        return new OtpDemanderResponse(MESSAGE_GENERIQUE);
    }
}
