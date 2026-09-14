-- Clôture de soirée (statut TERMINEE) : les billets EMIS jamais scannés doivent expirer
-- automatiquement (cf. SoireeController.mettreAJour -> BilletterieService.expirerTicketsSoiree).
-- Le CHECK sur tickets.statut doit désormais accepter la valeur EXPIRE en plus de
-- EMIS/ANNULE/UTILISE (même pattern que V17 pour notifications.canal).
ALTER TABLE tickets DROP CONSTRAINT tickets_statut_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_statut_check
    CHECK (statut IN ('EMIS','ANNULE','UTILISE','EXPIRE'));
