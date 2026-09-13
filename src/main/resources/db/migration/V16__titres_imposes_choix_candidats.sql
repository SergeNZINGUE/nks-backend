-- Titres imposés par le comité d'organisation (CO), publiés par phase (valables pour
-- toutes les soirées de cette phase), et choix de chaque candidat (titre imposé retenu +
-- titre personnel) avant sa propre soirée. cf. échange du 13/09/2026.

ALTER TABLE phases
    ADD COLUMN date_limite_choix_titres timestamptz;

CREATE TABLE titres_imposes (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_id  UUID NOT NULL REFERENCES phases(id) ON DELETE CASCADE,
    titre     VARCHAR(255) NOT NULL,
    ordre     SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_titres_imposes_phase ON titres_imposes (phase_id);

-- Un seul choix par candidat et par soirée (un candidat ne se produit qu'une fois par
-- phase — RM-41 — donc en pratique un seul choix par candidat et par phase).
CREATE TABLE choix_titres_candidats (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id      UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    soiree_id        UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    titre_impose_id  UUID NOT NULL REFERENCES titres_imposes(id),
    titre_personnel  VARCHAR(255) NOT NULL,
    date_choix       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (candidat_id, soiree_id)
);

CREATE INDEX ix_choix_titres_soiree ON choix_titres_candidats (soiree_id);
