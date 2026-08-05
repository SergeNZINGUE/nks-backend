package bf.laterrasse.nks.exception;

import java.time.Instant;
import java.util.List;

/** Format d'erreur standard de l'API (§13.1) : {code, message, details[]}. */
public record ApiError(String code, String message, List<String> details, Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of(), Instant.now());
    }

    public static ApiError of(String code, String message, List<String> details) {
        return new ApiError(code, message, details, Instant.now());
    }
}
