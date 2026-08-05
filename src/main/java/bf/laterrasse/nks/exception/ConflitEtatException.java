package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

/** Action refusée car l'état actuel de la ressource ne le permet pas — HTTP 409. */
public class ConflitEtatException extends NksException {
    public ConflitEtatException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
