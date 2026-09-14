package bf.laterrasse.nks.security;

import bf.laterrasse.nks.exception.AccesRefuseException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jetons d'accès billets (post-achat "read" seul / post-OTP "read"+"cancel") — correctif
 * de la faille IDOR relevée par l'audit sécurité : le numéro de téléphone seul ne fait
 * plus foi pour lire ou annuler une réservation.
 *
 * Volontairement séparé du JWT staff ({@link JwtService}/{@link JwtKeyConfig}) : secret
 * dédié {@code TICKET_ACCESS_SECRET} (HMAC), aucun passage par les filtres Spring Security
 * réservés aux comptes staff, aucune donnée de rôle/permission staff dans les claims.
 */
@Service
@Slf4j
public class TicketAccessTokenService {

    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_RESERVATION_ID = "reservationId";

    private final SecretKey secretKey;

    public TicketAccessTokenService(@Value("${nks.ticket-access.secret:}") String secretHex,
                                     @Value("${spring.profiles.active:dev}") String activeProfile) {
        if (secretHex == null || secretHex.isBlank()) {
            if (!"dev".equalsIgnoreCase(activeProfile) && !"test".equalsIgnoreCase(activeProfile)) {
                throw new IllegalStateException(
                        "TICKET_ACCESS_SECRET introuvable — obligatoire pour le profil '" + activeProfile
                                + "' (secret dédié, distinct de nks.jwt / JWT_PRIVATE_KEY_PATH). Générez-en un via "
                                + "`openssl rand -hex 32` et renseignez-le dans .env. Seuls les profils 'dev' et "
                                + "'test' tolèrent une clé éphémère.");
            }
            log.warn("Aucun TICKET_ACCESS_SECRET configuré — génération d'une clé HMAC éphémère pour le "
                    + "profil '{}'. Les jetons d'accès billets seront invalidés à chaque redémarrage. "
                    + "NE JAMAIS utiliser ce mode en production.", activeProfile);
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secretKey = Keys.hmacShaKeyFor(random);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(secretHex.trim()));
        }
    }

    /**
     * @param telephoneNormalise sujet du jeton (E.164)
     * @param scopes             "read" (post-achat) ou "read"+"cancel" (post-OTP)
     * @param reservationIdOuNull si non null, jeton scopé à cette seule réservation (post-achat) ;
     *                             si null, jeton valable pour toutes les réservations du numéro (post-OTP)
     * @param duree               durée de validité
     */
    public String emettre(String telephoneNormalise, Set<String> scopes, UUID reservationIdOuNull, Duration duree) {
        Instant maintenant = Instant.now();
        var builder = Jwts.builder()
                .subject(telephoneNormalise)
                .claim(CLAIM_SCOPE, List.copyOf(scopes))
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(duree)));
        if (reservationIdOuNull != null) {
            builder.claim(CLAIM_RESERVATION_ID, reservationIdOuNull.toString());
        }
        return builder.signWith(secretKey, Jwts.SIG.HS256).compact();
    }

    /**
     * Lève {@link AccesRefuseException} (message générique, jamais de détail exploitable par
     * un attaquant) si le jeton est absent, invalide, expiré, de scope insuffisant, ou si le
     * {@code sub}/{@code reservationId} ne correspond pas à ce qui est attendu.
     *
     * @param reservationIdAttendu {@code null} pour exiger un jeton phone-wide (mes-tickets,
     *                              annulation) ; sinon accepte un jeton phone-wide OU un jeton
     *                              scopé exactement à cette réservation (consultation d'un ticket).
     */
    public void verifier(String token, String scopeRequis, String telephoneAttendu, UUID reservationIdAttendu) {
        if (token == null || token.isBlank() || telephoneAttendu == null) {
            throw new AccesRefuseException("Accès non autorisé");
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AccesRefuseException("Accès non autorisé");
        }

        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) claims.get(CLAIM_SCOPE);
        if (scopes == null || !scopes.contains(scopeRequis)) {
            throw new AccesRefuseException("Accès non autorisé");
        }

        if (!telephoneAttendu.equals(claims.getSubject())) {
            throw new AccesRefuseException("Accès non autorisé");
        }

        String reservationIdToken = claims.get(CLAIM_RESERVATION_ID, String.class);
        if (reservationIdAttendu == null) {
            if (reservationIdToken != null) {
                throw new AccesRefuseException("Accès non autorisé");
            }
        } else if (reservationIdToken != null && !reservationIdToken.equals(reservationIdAttendu.toString())) {
            throw new AccesRefuseException("Accès non autorisé");
        }
    }
}
