-- Correctif bug categorie : la reservation ne stockait jamais sa propre categorie de ticket
-- (seul chaque Ticket la porte), donc BilletterieService devait la "deviner" en prenant la
-- 1ere categorie active de la soiree (categorieTicketRepository.findBySoireeId(...).findFirst()).
-- Faux des qu'une soiree a plusieurs categories actives (ex. Standard/VIP) : mauvaise categorie
-- verrouillee/decrementee a la confirmation de paiement, la reactivation tardive, l'echec de
-- paiement, l'annulation et l'expiration de pre-reservation.
--
-- Colonne NULLABLE (pas de NOT NULL) : les reservations PENDING/EXPIREE anciennes sans billet
-- n'ont pas de moyen fiable de retrouver leur categorie (voir backfill ci-dessous) ; ce cas
-- residuel reste couvert par un repli applicatif (BilletterieService.trouverCategoriePourReservation,
-- reduit a ce role de secours + log.warn).
--
-- Backfill best-effort : pour les reservations qui ont deja des billets, la categorie du 1er
-- billet est reprise (tous les billets d'une meme reservation partagent la meme categorie en
-- pratique, cf. genererTickets qui recoit une seule CategorieTicket pour toute la reservation).
--
-- Migration non destructive et reversible en esprit : ALTER TABLE reservations DROP COLUMN categorie_id.

ALTER TABLE reservations
    ADD COLUMN categorie_id UUID NULL REFERENCES categories_tickets(id);

UPDATE reservations r
SET categorie_id = (SELECT t.categorie_id FROM tickets t WHERE t.reservation_id = r.id LIMIT 1)
WHERE r.categorie_id IS NULL
  AND EXISTS (SELECT 1 FROM tickets t WHERE t.reservation_id = r.id);

CREATE INDEX idx_reservations_categorie ON reservations (categorie_id);
