-- Fusionne le scan d'entrée et l'activation de consommation en une seule action tenue par
-- l'hôtesse en salle (au lieu d'un agent d'accueil à la porte + un caissier au bar) : évite
-- le goulot d'étranglement d'un point de caisse unique, plusieurs hôtesses peuvent activer
-- les consommations à chaque service, depuis leur propre téléphone, où qu'elles soient.
-- Renomme le rôle CAISSIER (introduit en V13) en HOTESSE — même capacité fonctionnelle,
-- nom qui reflète qui l'utilise réellement sur le terrain.

-- Drop avant UPDATE : la contrainte CHECK doit être absente pendant le renommage
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_nom_check;

UPDATE roles
SET nom = 'HOTESSE',
    description = 'Accueil et service en salle : scanne le billet (entrée si besoin) et active le droit de vote sur place à chaque consommation'
WHERE nom = 'CAISSIER';

ALTER TABLE roles
    ADD CONSTRAINT roles_nom_check
    CHECK (nom IN ('VISITEUR','CANDIDAT','VOTANT_PUBLIC','JURY','PARTENAIRE',
                   'ADMIN','SUPER_ADMIN','AGENT_ACCUEIL','ORGANISATEUR','HOTESSE'));
