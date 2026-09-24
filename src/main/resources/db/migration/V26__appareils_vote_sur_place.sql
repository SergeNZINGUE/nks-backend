-- Blocage "dur" du vote sur place par appareil : un meme appareil (jeton signe emis par
-- POST /vote-sur-place/appareil, conserve cote navigateur) ne peut voter que pour UN seul
-- billet par soiree. On ne stocke jamais le jeton brut : uniquement le SHA-256 (hex) de son
-- uuid. La ligne est creee au premier vote de l'appareil pour la soiree.
-- Migration non destructive et reversible : DROP TABLE appareils_vote_sur_place.

CREATE TABLE appareils_vote_sur_place (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soiree_id     UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    appareil_hash VARCHAR(64) NOT NULL,
    ticket_id     UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    premier_vote  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip            VARCHAR(45),
    user_agent    VARCHAR(500),
    empreinte     VARCHAR(128),
    CONSTRAINT uq_appareils_vote_soiree_hash UNIQUE (soiree_id, appareil_hash)
);

CREATE INDEX idx_appareils_vote_ticket ON appareils_vote_sur_place (ticket_id);
CREATE INDEX idx_appareils_vote_signal ON appareils_vote_sur_place (soiree_id, ip);
