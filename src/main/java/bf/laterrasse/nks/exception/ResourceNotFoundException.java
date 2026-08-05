package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends NksException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
