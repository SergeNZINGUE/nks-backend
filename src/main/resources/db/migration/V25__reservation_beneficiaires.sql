-- Un billet = une personne = un numero de telephone. A la reservation, le client saisit un
-- numero par place ; les billets ne sont crees qu'a la confirmation du paiement
-- (BilletterieService.genererTickets), on persiste donc les beneficiaires des l'initiation.
--
-- Garde-fou d'unicite en base : un numero ne peut avoir qu'UN beneficiaire ACTIF par soiree
-- (toutes reservations et categories confondues, y compris les pre-reservations PENDING non
-- expirees). Index unique PARTIEL uniquement sur cette NOUVELLE table : la table `tickets`
-- n'est jamais touchee (les billets legacy partagent le telephone du reservant et feraient
-- echouer un index unique ; ils sont comptes par un controle applicatif a la creation).
--
-- `actif` passe a false des que la place est liberee (pre-reservation expiree, reservation
-- annulee, paiement echoue, billet expire a la cloture de soiree).
-- Migration non destructive et reversible : DROP TABLE reservation_beneficiaires.

CREATE TABLE reservation_beneficiaires (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    soiree_id      UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    position       SMALLINT NOT NULL CHECK (position >= 0),
    nom            VARCHAR(150),
    telephone      VARCHAR(20) NOT NULL,
    actif          BOOLEAN NOT NULL DEFAULT true,
    date_creation  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation_beneficiaires_position UNIQUE (reservation_id, position)
);

CREATE UNIQUE INDEX ux_reservation_beneficiaires_soiree_tel_actif
    ON reservation_beneficiaires (soiree_id, telephone)
    WHERE actif;

CREATE INDEX idx_reservation_beneficiaires_reservation
    ON reservation_beneficiaires (reservation_id);
