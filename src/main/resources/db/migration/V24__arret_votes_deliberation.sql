-- Snapshot des votes par candidat au moment de l'arrêt
CREATE TABLE snapshots_votes_soiree (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soiree_id UUID NOT NULL REFERENCES soirees_events(id),
    candidat_id UUID NOT NULL REFERENCES candidats(id),
    voix_payantes BIGINT NOT NULL DEFAULT 0,
    voix_sociales_likes BIGINT NOT NULL DEFAULT 0,
    voix_sociales_commentaires BIGINT NOT NULL DEFAULT 0,
    voix_sur_place BIGINT NOT NULL DEFAULT 0,
    total_voix_payantes_phase BIGINT NOT NULL DEFAULT 0,
    total_voix_sur_place_soiree BIGINT NOT NULL DEFAULT 0,
    date_snapshot TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (soiree_id, candidat_id)
);

-- Cycle de vie délibération sur soirees_events
ALTER TABLE soirees_events
    ADD COLUMN votes_arretes_le TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deliberation_verrouillee BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deliberation_verrouillee_le TIMESTAMP WITH TIME ZONE;

-- Gel des résultats pour les éliminés après délibération
ALTER TABLE resultats_phase
    ADD COLUMN gele BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN soiree_gelee_id UUID REFERENCES soirees_events(id);
