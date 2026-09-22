-- V21 : notation sur deux passages — numero_passage (1 ou 2) ajouté à notes_jury
-- La contrainte unique existante (jury_id, candidat_id, soiree_id, critere_id) est remplacée
-- par (jury_id, candidat_id, soiree_id, critere_id, numero_passage) pour permettre
-- une note par passage par critère.

ALTER TABLE notes_jury
    ADD COLUMN numero_passage SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT ck_notes_jury_passage CHECK (numero_passage IN (1, 2));

ALTER TABLE notes_jury
    DROP CONSTRAINT IF EXISTS notes_jury_jury_id_candidat_id_soiree_id_critere_id_key;

ALTER TABLE notes_jury
    ADD CONSTRAINT uq_notes_jury_jury_candidat_soiree_critere_passage
        UNIQUE (jury_id, candidat_id, soiree_id, critere_id, numero_passage);
