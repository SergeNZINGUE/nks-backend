-- Normalise les numéros de téléphone vers E.164 (+226XXXXXXXX).
-- Ignore les numéros dont la version normalisée existe déjà (évite la violation UNIQUE).

UPDATE utilisateurs u
SET telephone = CASE
    WHEN telephone LIKE '00226%' THEN '+' || SUBSTRING(telephone FROM 3)
    WHEN telephone LIKE '226%'   THEN '+' || telephone
    ELSE                               '+226' || telephone
END
WHERE telephone NOT LIKE '+%'
  AND NOT EXISTS (
      SELECT 1 FROM utilisateurs u2
      WHERE u2.id != u.id
        AND u2.telephone = CASE
            WHEN u.telephone LIKE '00226%' THEN '+' || SUBSTRING(u.telephone FROM 3)
            WHEN u.telephone LIKE '226%'   THEN '+' || u.telephone
            ELSE '+226' || u.telephone
        END
  );
