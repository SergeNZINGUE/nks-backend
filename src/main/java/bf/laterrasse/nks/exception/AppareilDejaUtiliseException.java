package bf.laterrasse.nks.exception;

/** Cet appareil a déjà voté pour un autre billet lors de cette soirée — HTTP 409. */
public class AppareilDejaUtiliseException extends ConflitEtatException {
    public AppareilDejaUtiliseException() {
        super("Ce téléphone a déjà servi à voter pour un autre billet lors de cette soirée");
    }

    @Override
    public String getCode() {
        return "APPAREIL_DEJA_UTILISE";
    }
}
