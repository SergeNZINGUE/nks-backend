-- Abaisse l'âge minimum d'inscription de 20 à 18 ans (décision client)
ALTER TABLE candidats DROP CONSTRAINT IF EXISTS candidats_age_a_l_inscription_check;
ALTER TABLE candidats ADD CONSTRAINT candidats_age_a_l_inscription_check
    CHECK (age_a_l_inscription >= 18);
