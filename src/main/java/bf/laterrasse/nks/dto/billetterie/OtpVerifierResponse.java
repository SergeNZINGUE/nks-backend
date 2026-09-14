package bf.laterrasse.nks.dto.billetterie;

public record OtpVerifierResponse(String accessToken, long expiresInSeconds) {
}
