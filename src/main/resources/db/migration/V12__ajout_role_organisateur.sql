-- Ajoute le rôle ORGANISATEUR : droits de gestion opérationnelle sans accès aux données financières.
-- Met à jour le CHECK constraint sur roles.nom (PostgreSQL requiert drop + recreate).

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_nom_check;

ALTER TABLE roles
    ADD CONSTRAINT roles_nom_check
    CHECK (nom IN ('VISITEUR','CANDIDAT','VOTANT_PUBLIC','JURY','PARTENAIRE',
                   'ADMIN','SUPER_ADMIN','AGENT_ACCUEIL','ORGANISATEUR'));

INSERT INTO roles (nom, description)
VALUES ('ORGANISATEUR', 'Gestionnaire opérationnel : phases, soirées, partenaires et communications — sans accès aux données financières');
