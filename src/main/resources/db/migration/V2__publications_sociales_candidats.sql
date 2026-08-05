-- Décision client : votes sociaux ingérés via API Facebook/TikTok réelle (et non saisie
-- manuelle admin comme envisagé initialement en H3 du rapport). Cela suppose une
-- publication officielle par candidat sur chaque plateforme, dont l'ID est enregistré ici
-- pour permettre le polling périodique (SocialVoteIngestionService).

ALTER TABLE candidats
    ADD COLUMN IF NOT EXISTS post_id_facebook VARCHAR(100),
    ADD COLUMN IF NOT EXISTS post_id_tiktok   VARCHAR(100);

INSERT INTO parametres_plateforme (cle, valeur, type_valeur, description, modifiable_par_admin) VALUES
    ('SOCIAL_VOTES_POLLING_ACTIF', 'false', 'BOOLEAN',
     'Active/désactive le polling automatique Facebook/TikTok (à activer une fois les identifiants d''app Meta/TikTok obtenus)', TRUE)
ON CONFLICT (cle) DO NOTHING;
