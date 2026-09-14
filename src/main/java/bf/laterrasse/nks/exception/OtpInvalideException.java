package bf.laterrasse.nks.exception;

import org.springframework.http.HttpStatus;

/**
 * Réponse volontairement uniforme (audit sécurité) : code faux, expiré, déjà consommé,
 * trop de tentatives ou numéro inconnu renvoient tous exactement la même erreur, pour ne
 * jamais laisser un client distinguer la cause d'un échec de vérification OTP.
 */
public class OtpInvalideException extends NksException {
    public OtpInvalideException() {
        super("Code invalide ou expiré.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getCode() {
        return "OTP_INVALIDE";
    }
}
