-- "Moments de l'événement" : photos/vidéos souvenirs de la soirée, distinctes de la
-- photo de profil et de la vidéo de prestation (tables medias/videos existantes).
--
-- Table neuve plutôt qu'extension de medias : medias.taille_octets est plafonné en dur
-- à 5 Mo par CHECK (V1__init_schema.sql), incompatible avec des vidéos jusqu'à 100 Mo,
-- et medias.candidat_id y est NOT NULL — ce que cette fonctionnalité doit pouvoir violer
-- (un admin/organisateur peut ajouter une photo sans l'attribuer à un candidat). Modifier
-- ces deux contraintes sur une table déjà en production (photo de profil, capture réseau
-- social) aurait été plus risqué qu'une table neuve, isolée, qui ne change rien à
-- l'existant.
CREATE TABLE moments_evenement (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                      VARCHAR(10) NOT NULL CHECK (type IN ('PHOTO', 'VIDEO')),
    url_stockage              VARCHAR(500) NOT NULL,
    nom_fichier_original      VARCHAR(255),
    -- Plafond large au niveau table (100 Mo, la vidéo) : les tailles réelles par type
    -- (photo 5 Mo / vidéo 100 Mo) sont affinées côté application via MediaProperties,
    -- même limites que le reste du site — cf. MomentEvenementService.validerTaille().
    taille_octets             BIGINT NOT NULL CHECK (taille_octets <= 104857600),
    format                    VARCHAR(10) NOT NULL,
    legende                   VARCHAR(140),
    motif_rejet               TEXT,
    en_vedette                BOOLEAN NOT NULL DEFAULT FALSE,
    statut                    VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                                  CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'MASQUE')),
    soiree_id                 UUID REFERENCES soirees_events(id) ON DELETE SET NULL,
    -- Candidat ayant lui-même soumis ce moment depuis son panel — NULL si ajouté
    -- directement par un admin/organisateur (crédit public "Équipe NKS" dans ce cas).
    candidat_uploadeur_id     UUID REFERENCES candidats(id) ON DELETE SET NULL,
    -- Admin/organisateur ayant ajouté ce moment directement — NULL pour un envoi candidat.
    ajoute_par_utilisateur_id UUID REFERENCES utilisateurs(id) ON DELETE SET NULL,
    date_upload               TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_moderation           TIMESTAMPTZ
);

CREATE INDEX idx_moments_evenement_statut ON moments_evenement(statut);
CREATE INDEX idx_moments_evenement_candidat_uploadeur ON moments_evenement(candidat_uploadeur_id);
CREATE INDEX idx_moments_evenement_soiree ON moments_evenement(soiree_id);

-- Candidats visibles/tagués sur un même média (photo de groupe) — distinct du candidat
-- uploadeur ci-dessus, réservé à l'ajout admin/organisateur (jamais renseigné par un
-- candidat sur son propre envoi, cf. décision produit : pas de tag d'un rival sans son
-- accord).
CREATE TABLE moments_evenement_candidats (
    moment_id   UUID NOT NULL REFERENCES moments_evenement(id) ON DELETE CASCADE,
    candidat_id UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    PRIMARY KEY (moment_id, candidat_id)
);
