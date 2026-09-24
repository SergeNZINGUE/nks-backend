package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

/** Jeton d'appareil absent, mal formé ou à signature invalide au moment de voter — HTTP 400. */
public class AppareilInconnuException extends NksException {
    public AppareilInconnuException() {
        super("Appareil non reconnu. Recharge la page puis réessaie.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getCode() {
        return "APPAREIL_INCONNU";
    }
}
