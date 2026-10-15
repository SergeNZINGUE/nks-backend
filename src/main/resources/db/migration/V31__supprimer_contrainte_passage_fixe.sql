-- La contrainte CHECK (numero_passage IN (1,2)) bloque les phases à 3+ passages.
-- Le nombre de passages est désormais configurable par phase (V29/V30) ; la validation
-- métier est assurée par @Min(1) côté backend. On supprime la contrainte DB.
ALTER TABLE notes_jury DROP CONSTRAINT ck_notes_jury_passage;