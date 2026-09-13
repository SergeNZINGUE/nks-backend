-- Consentement explicite au "Recueil de consentement" (règlement + données personnelles +
-- frais non remboursables + droit à l'image + clauses de litige) — distinct de l'ancien
-- utilisateurs.consentement_rgpd qui était jusqu'ici mis à `true` automatiquement à la
-- création du compte candidat (CandidatureService.creerUtilisateurCandidat), donc sans
-- valeur probante de consentement réel. Défaut `false` pour TOUS les candidats, y compris
-- ceux déjà inscrits : ils n'ont jamais réellement vu/accepté ce document, donc ils doivent
-- être invités à le faire à leur prochaine connexion, comme tout nouveau candidat.

ALTER TABLE candidats
    ADD COLUMN consentement_recueil_accepte boolean NOT NULL DEFAULT false,
    ADD COLUMN date_consentement_recueil    timestamptz;
