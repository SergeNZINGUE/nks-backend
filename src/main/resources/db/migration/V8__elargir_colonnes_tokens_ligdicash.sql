-- V8 : Élargir les colonnes de stockage des tokens LigdiCash (JWT de ~200 caractères)
ALTER TABLE paiements
    ALTER COLUMN reference_externe TYPE VARCHAR(500);

ALTER TABLE transactions_mobile_money
    ALTER COLUMN reference_operateur TYPE VARCHAR(500),
    ALTER COLUMN token_creation TYPE VARCHAR(500);

ALTER TABLE ligdicash_callbacks
    ALTER COLUMN token TYPE VARCHAR(500);
