-- V10 : la contrainte UNIQUE(operateur, reference_operateur) sur transactions_mobile_money
-- bloque le job de polling qui insère une nouvelle ligne à chaque passage pour le même token.
-- La déduplication webhook est assurée par ligdicash_callbacks (contrainte UNIQUE token).
-- La table transactions_mobile_money devient un journal d'audit : plusieurs lignes par token autorisées.
ALTER TABLE transactions_mobile_money
    DROP CONSTRAINT IF EXISTS transactions_mobile_money_operateur_reference_operateur_key;
