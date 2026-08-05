package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

/** Exception métier de base. Chaque sous-classe fixe le code HTTP renvoyé au client. */
public abstract class NksException extends RuntimeException {

    protected NksException(String message) {
        super(message);
    }

    public abstract HttpStatus getStatus();

    public String getCode() {
        return getClass().getSimpleName().replace("Exception", "").toUpperCase();
    }
}
