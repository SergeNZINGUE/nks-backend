package bf.laterrasse.nks.dto.auth;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        List<String> roles
) {
}
