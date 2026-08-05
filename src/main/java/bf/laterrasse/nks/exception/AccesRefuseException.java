package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

public class AccesRefuseException extends NksException {
    public AccesRefuseException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
