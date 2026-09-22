-- Correction du type de la colonne numero_passage : SMALLINT (int2) → INTEGER (int4)
-- pour correspondre au mapping Hibernate du champ Java int de NoteJury.
ALTER TABLE notes_jury ALTER COLUMN numero_passage TYPE INTEGER;
