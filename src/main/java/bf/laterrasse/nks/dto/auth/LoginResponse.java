package bf.laterrasse.nks.dto.auth;

import java.util.List;

/**
 * `consentementRequis` : true uniquement si l'utilisateur a le rôle CANDIDAT et que son
 * `Candidat.consentementRecueilAccepte` est encore `false` — le frontend doit alors
 * rediriger systématiquement vers la page de consentement avant toute autre page de
 * l'espace candidat (cf. AuthService.calculerConsentementRequis).
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        List<String> roles,
        boolean consentementRequis
) {
}
