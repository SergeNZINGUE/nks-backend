package bf.laterrasse.nks.security;

import bf.laterrasse.nks.domain.Utilisateur;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Émission et vérification des JWT d'accès (RS256, 1h — §14.1).
 * Les refresh tokens sont des identifiants opaques stockés hashés en base
 * (table refresh_tokens) afin de permettre leur révocation — cf. ADR-06.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final KeyPair jwtKeyPair;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(Utilisateur utilisateur) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        List<String> roles = utilisateur.getRoles().stream()
                .map(r -> r.getNom().name())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(utilisateur.getId().toString())
                .claim("email", utilisateur.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(jwtKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims parseAndValidate(String token) throws SignatureException {
        return Jwts.parser()
                .verifyWith(jwtKeyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        return (List<String>) claims.get("roles");
    }
}
