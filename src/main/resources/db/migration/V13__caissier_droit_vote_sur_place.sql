-- Ajoute le rôle CAISSIER et la table droits_vote_sur_place : un vote sur place n'est
-- désormais possible que pour un billet (ticket) déjà scanné à l'entrée (statut UTILISE,
-- anti-double-scan existant §14.6) ET pour lequel un caissier a validé une consommation
-- réelle. Un seul droit de vote par billet, jamais deux (contrainte UNIQUE sur ticket_id),
-- ce qui garantit mécaniquement 1 personne physique = 1 vote maximum sur cette soirée,
-- sans dépendre d'un numéro de téléphone (facilement dupliqué) ni d'un nouveau système de
-- bracelet — on réutilise l'identité déjà vérifiée à l'entrée.

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_nom_check;

ALTER TABLE roles
    ADD CONSTRAINT roles_nom_check
    CHECK (nom IN ('VISITEUR','CANDIDAT','VOTANT_PUBLIC','JURY','PARTENAIRE',
                   'ADMIN','SUPER_ADMIN','AGENT_ACCUEIL','ORGANISATEUR','CAISSIER'));

INSERT INTO roles (nom, description)
VALUES ('CAISSIER', 'Encaissement des consommations au bar : valide le droit de vote sur place lié à un billet déjà scanné à l''entrée');

CREATE TABLE droits_vote_sur_place (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id         UUID NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    soiree_id         UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    caissier_id       UUID NOT NULL REFERENCES utilisateurs(id),
    statut            VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE'
                          CHECK (statut IN ('DISPONIBLE','UTILISE')),
    date_emission     TIMESTAMPTZ NOT NULL DEFAULT now(),
    lien_whatsapp_envoye boolean NOT NULL DEFAULT false,
    candidat_id       UUID REFERENCES candidats(id),
    date_vote         TIMESTAMPTZ,
    -- Champs d'audit uniquement (jamais utilisés pour bloquer un vote, cf. échange du
    -- 13/09/2026 sur l'interception d'un jeton par un tiers) : téléphone saisi par le votant
    -- au moment du vote (à recouper a posteriori avec tickets.telephone_spectateur) et
    -- position du navigateur si le client l'autorise.
    telephone_votant  VARCHAR(20),
    position_latitude NUMERIC(10,6),
    position_longitude NUMERIC(10,6),
    position_precision_m NUMERIC(10,2),
    CHECK ( (statut = 'DISPONIBLE' AND candidat_id IS NULL AND date_vote IS NULL)
         OR (statut = 'UTILISE' AND candidat_id IS NOT NULL AND date_vote IS NOT NULL) )
);

CREATE INDEX ix_droits_vote_sur_place_soiree ON droits_vote_sur_place (soiree_id);
