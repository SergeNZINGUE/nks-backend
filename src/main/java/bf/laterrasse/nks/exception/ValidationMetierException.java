package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

/** Violation d'une règle métier (RM-xx du rapport) détectée côté serveur — HTTP 400. */
public class ValidationMetierException extends NksException {
    public ValidationMetierException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
