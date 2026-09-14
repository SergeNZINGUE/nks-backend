-- Correctif audit sécurité (IDOR billetterie) : vérification OTP par téléphone avant
-- toute lecture/annulation de réservation par un numéro non authentifié (voir
-- BilletterieController / OtpTicketController).
CREATE TABLE otp_ticket_verifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telephone      VARCHAR(20) NOT NULL,
    code_hash      VARCHAR(255) NOT NULL,
    expire_at      TIMESTAMPTZ NOT NULL,
    consomme       BOOLEAN NOT NULL DEFAULT false,
    nb_tentatives  SMALLINT NOT NULL DEFAULT 0,
    date_creation  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_telephone ON otp_ticket_verifications(telephone);
