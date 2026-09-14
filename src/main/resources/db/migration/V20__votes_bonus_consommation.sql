-- Votes bonus liés à la consommation : au-delà du droit de base (offert à la première
-- consommation validée = l'entrée, garanti unique par billet, cf. V13), chaque tranche de N
-- consommations réelles supplémentaires au bar donne droit à +1 vote bonus, plafonné par
-- soirée. Les deux paramètres (seuil, plafond) sont configurables par soirée par l'admin.
--
-- L'unicité du droit de BASE reste une garantie DB imbattable (jamais affaiblie) : on la fait
-- porter par un index unique PARTIEL (type_droit = 'BASE') au lieu de la contrainte UNIQUE
-- globale sur ticket_id, ce qui autorise désormais plusieurs droits BONUS pour le même billet.

ALTER TABLE droits_vote_sur_place DROP CONSTRAINT droits_vote_sur_place_ticket_id_key;

ALTER TABLE droits_vote_sur_place
    ADD COLUMN type_droit VARCHAR(10) NOT NULL DEFAULT 'BASE'
        CHECK (type_droit IN ('BASE','BONUS'));

CREATE UNIQUE INDEX ux_droits_vote_sur_place_ticket_base
    ON droits_vote_sur_place (ticket_id)
    WHERE type_droit = 'BASE';

ALTER TABLE tickets
    ADD COLUMN nb_consommations_supplementaires SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE soirees_events
    ADD COLUMN nb_consommations_pour_vote_bonus SMALLINT;

ALTER TABLE soirees_events
    ADD COLUMN plafond_votes_bonus SMALLINT;
