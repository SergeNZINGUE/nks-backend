-- V6 : intégration réelle LigdiCash — colonnes de traçabilité + déduplication callbacks

-- token_creation : token LigdiCash issu de createInvoice, distinct de reference_operateur
-- code_reponse   : ex. "Code15" (OTP invalide), "00" (succès) — source de vérité pour le motif
-- motif_rejet    : texte lisible du motif, ex. "Mauvais OTP" — affiché côté frontend
ALTER TABLE transactions_mobile_money
    ADD COLUMN IF NOT EXISTS token_creation   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS code_reponse     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS motif_rejet      TEXT;

-- signature_webhook ne correspond à rien dans le vrai protocole LigdiCash (pas de signature)
COMMENT ON COLUMN transactions_mobile_money.signature_webhook IS 'Déprécié — LigdiCash ne signe pas ses webhooks';

-- Traçabilité polling de secours sur la table paiements
ALTER TABLE paiements
    ADD COLUMN IF NOT EXISTS nb_tentatives_polling        INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS derniere_tentative_polling   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS date_expiration              TIMESTAMPTZ;

-- Table de déduplication des callbacks LigdiCash
-- LigdiCash envoie systématiquement 2 POST par événement (form-encoded + JSON).
-- L'INSERT atomique sur le token sert de verrou : si la contrainte UNIQUE est violée,
-- le second POST est ignoré sans aucune logique métier exécutée.
CREATE TABLE IF NOT EXISTS ligdicash_callbacks (
    id       BIGSERIAL    PRIMARY KEY,
    token    VARCHAR(255) NOT NULL,
    recu_le  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ligdicash_callbacks_token UNIQUE (token)
);
