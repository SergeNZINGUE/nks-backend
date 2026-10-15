-- Nombre de passages de notation jury par phase (configurable par l'admin).
-- Default 2 pour les phases existantes (éliminatoires = 2 passages, cf. V21).
ALTER TABLE phases ADD COLUMN nombre_passages SMALLINT NOT NULL DEFAULT 2;