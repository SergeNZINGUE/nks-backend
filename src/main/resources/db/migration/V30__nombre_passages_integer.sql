-- Correction du type de nombre_passages : SMALLINT → INTEGER pour correspondre
-- au mapping Hibernate (Phase.nombrePassages est un int Java → Types#INTEGER).
ALTER TABLE phases ALTER COLUMN nombre_passages TYPE INTEGER;