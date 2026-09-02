package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

public class TropDeTentativesException extends NksException {
    public TropDeTentativesException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
