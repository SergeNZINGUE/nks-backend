-- V32 : soirée libre — phase optionnelle, colonne type_soiree
ALTER TABLE soirees_events ALTER COLUMN phase_id DROP NOT NULL;
ALTER TABLE soirees_events ADD COLUMN type_soiree VARCHAR(10) NOT NULL DEFAULT 'NKS';