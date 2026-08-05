-- =====================================================================================
-- NKS — Night Karaoke Stars — Schéma initial PostgreSQL
-- Basé sur le MCD/MLD/MPD du Rapport de Conception Phase 0 (v1.0, 2026-06-22)
-- Décisions arbitrées avec le client avant génération (voir README.md § Décisions) :
--   - Taille vidéo max      : 100 Mo (RM-05 tranché)
--   - Votes sociaux         : intégration API Facebook/TikTok (pas de saisie manuelle)
--   - Jury en finale        : obligatoire (H7)
--   - Vote public sur place : interface web mobile via QR code en salle (H4)
--   - Tarif inscription     : configurable (parametres_plateforme), pas de valeur imposée
--   - Remboursement billet  : manuel, au cas par cas (pas d'automatisation)
--   - Anti-fraude votes     : configurable, défaut 20 votes/heure/numéro
--   - Nombre de jurés       : variable par soirée (pas de contrainte de cardinalité fixe)
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================= DOMAINE UTILISATEURS =================================

CREATE TABLE utilisateurs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(255) NOT NULL UNIQUE,
    telephone               VARCHAR(20)  NOT NULL UNIQUE,
    mot_de_passe_hash       VARCHAR(255) NOT NULL,
    prenom                  VARCHAR(100) NOT NULL,
    nom                     VARCHAR(100) NOT NULL,
    statut                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIF'
                                CHECK (statut IN ('ACTIF','INACTIF','SUSPENDU')),
    consentement_rgpd       BOOLEAN NOT NULL DEFAULT FALSE,
    date_consentement       TIMESTAMPTZ,
    date_creation           TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_derniere_connexion TIMESTAMPTZ,
    date_suppression        TIMESTAMPTZ
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom         VARCHAR(30) NOT NULL UNIQUE
                    CHECK (nom IN ('VISITEUR','CANDIDAT','VOTANT_PUBLIC','JURY','PARTENAIRE',
                                    'ADMIN','SUPER_ADMIN','AGENT_ACCUEIL')),
    description TEXT
);

CREATE TABLE utilisateurs_roles (
    utilisateur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    role_id        UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (utilisateur_id, role_id)
);

CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    token_hash    VARCHAR(255) NOT NULL UNIQUE,
    revoque       BOOLEAN NOT NULL DEFAULT FALSE,
    date_creation TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_expiration TIMESTAMPTZ NOT NULL
);

-- ============================= DOMAINE COMPÉTITION ===================================

CREATE TABLE editions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                       VARCHAR(150) NOT NULL,
    annee                     SMALLINT NOT NULL,
    statut                    VARCHAR(20) NOT NULL DEFAULT 'EN_PREPARATION'
                                  CHECK (statut IN ('EN_PREPARATION','EN_COURS','TERMINEE','ARCHIVEE')),
    date_debut_inscriptions   DATE,
    date_fin_inscriptions     DATE,
    date_debut_competition    DATE,
    date_fin_competition      DATE,
    description               TEXT,
    date_creation             TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_modification         TIMESTAMPTZ
);

-- Hypothèse H5 : une seule édition EN_COURS à la fois — appliqué via index partiel
CREATE UNIQUE INDEX ux_editions_une_seule_en_cours ON editions ((statut))
    WHERE statut = 'EN_COURS';

CREATE TABLE phases (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edition_id                  UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    nom                         VARCHAR(30) NOT NULL
                                    CHECK (nom IN ('PRESELECTION','ELIMINATOIRES','DEMI_FINALE','FINALE')),
    type_phase                  VARCHAR(30) NOT NULL,
    ordre                       SMALLINT NOT NULL,
    date_debut                  TIMESTAMPTZ,
    date_fin                    TIMESTAMPTZ,
    statut                      VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                                    CHECK (statut IN ('EN_ATTENTE','EN_COURS','TERMINEE')),
    poids_votes_en_ligne        SMALLINT NOT NULL,
    poids_public_sur_place      SMALLINT NOT NULL,
    poids_jury                  SMALLINT NOT NULL,
    points_max_votes_en_ligne   NUMERIC(8,4) NOT NULL DEFAULT 100,
    points_max_public           NUMERIC(8,4) NOT NULL DEFAULT 100,
    points_max_jury             NUMERIC(8,4) NOT NULL DEFAULT 100,
    jury_obligatoire             BOOLEAN NOT NULL DEFAULT TRUE, -- décision client : jury obligatoire en finale (H7)
    vote_actif                  BOOLEAN NOT NULL DEFAULT FALSE,
    date_ouverture_vote         TIMESTAMPTZ,
    date_fermeture_vote         TIMESTAMPTZ,
    UNIQUE (edition_id, ordre),
    CHECK (poids_votes_en_ligne + poids_public_sur_place + poids_jury = 100)
);

CREATE TABLE soirees_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edition_id            UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    phase_id              UUID NOT NULL REFERENCES phases(id) ON DELETE CASCADE,
    nom                   VARCHAR(150) NOT NULL,
    date_heure            TIMESTAMPTZ NOT NULL,
    lieu                  VARCHAR(150),
    adresse               VARCHAR(255),
    capacite_max          INTEGER NOT NULL DEFAULT 0,
    statut                VARCHAR(20) NOT NULL DEFAULT 'PLANIFIEE'
                              CHECK (statut IN ('PLANIFIEE','EN_COURS','TERMINEE','ANNULEE')),
    vote_sur_place_actif  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE partenaires (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom               VARCHAR(150) NOT NULL,
    logo_url          VARCHAR(500),
    description       TEXT,
    site_web_url      VARCHAR(255),
    niveau_partenariat VARCHAR(20) CHECK (niveau_partenariat IN ('TITRE','OR','ARGENT','PARTENAIRE')),
    contact_nom       VARCHAR(150),
    contact_email     VARCHAR(255),
    contact_telephone VARCHAR(20),
    statut            VARCHAR(20) NOT NULL DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF','INACTIF'))
);

CREATE TABLE sponsors_placements (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partenaire_id     UUID NOT NULL REFERENCES partenaires(id) ON DELETE CASCADE,
    edition_id        UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    niveau_affichage  VARCHAR(30),
    ordre_affichage   SMALLINT NOT NULL DEFAULT 0,
    contrat_reference VARCHAR(100),
    date_debut        DATE,
    date_fin          DATE,
    UNIQUE (partenaire_id, edition_id)
);

-- ============================= DOMAINE CANDIDATS ======================================

CREATE TABLE candidats (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id        UUID NOT NULL UNIQUE REFERENCES utilisateurs(id) ON DELETE CASCADE,
    edition_id            UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    code_candidat         VARCHAR(10) NOT NULL,
    date_naissance        DATE NOT NULL,
    age_a_l_inscription   SMALLINT NOT NULL,
    biographie            TEXT,
    chanson_preselection  VARCHAR(255),
    statut_profil         VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                              CHECK (statut_profil IN ('EN_ATTENTE','ACTIF','SUSPENDU','ELIMINE','FINALISTE','GAGNANT')),
    date_activation_profil TIMESTAMPTZ,
    UNIQUE (edition_id, code_candidat),
    CHECK (age_a_l_inscription >= 20)
);

CREATE TABLE candidatures (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id            UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    edition_id             UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    statut                 VARCHAR(25) NOT NULL DEFAULT 'EN_ATTENTE'
                               CHECK (statut IN ('EN_ATTENTE','VALIDEE','REJETEE','EN_ATTENTE_PAIEMENT','ACTIVE')),
    motivation             VARCHAR(1500),
    capture_fb_tiktok_url  VARCHAR(500),
    date_soumission        TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_traitement_admin  TIMESTAMPTZ,
    admin_id               UUID REFERENCES utilisateurs(id),
    motif_rejet            TEXT,
    date_modification      TIMESTAMPTZ,
    UNIQUE (candidat_id, edition_id),
    CHECK (statut <> 'REJETEE' OR motif_rejet IS NOT NULL)
);

CREATE TABLE medias (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id           UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    type                  VARCHAR(20) NOT NULL CHECK (type IN ('PHOTO_PROFIL','CAPTURE_SOCIAL')),
    url_stockage          VARCHAR(500),
    nom_fichier_original  VARCHAR(255),
    taille_octets         BIGINT NOT NULL CHECK (taille_octets <= 5242880), -- 5 Mo (RM-03)
    format                VARCHAR(10) NOT NULL CHECK (format IN ('JPG','PNG')),
    date_upload           TIMESTAMPTZ NOT NULL DEFAULT now(),
    statut                VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                              CHECK (statut IN ('EN_ATTENTE','VALIDE','MASQUE'))
);

CREATE TABLE videos (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id              UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    phase_id                 UUID REFERENCES phases(id),
    url_stockage_originale   VARCHAR(500),
    url_streaming            VARCHAR(500),
    url_thumbnail            VARCHAR(500),
    duree_secondes           INTEGER,
    taille_octets            BIGINT NOT NULL CHECK (taille_octets <= 104857600), -- 100 Mo (décision client, RM-05 tranché)
    titre_chanson            VARCHAR(255),
    statut                   VARCHAR(20) NOT NULL DEFAULT 'EN_COURS_UPLOAD'
                                 CHECK (statut IN ('EN_COURS_UPLOAD','DISPONIBLE','MASQUEE')),
    date_upload              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================= DOMAINE JURY ET NOTATION ===============================

CREATE TABLE jurys (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id UUID NOT NULL UNIQUE REFERENCES utilisateurs(id) ON DELETE CASCADE,
    edition_id     UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    prenom         VARCHAR(100) NOT NULL,
    nom            VARCHAR(100) NOT NULL,
    specialite     VARCHAR(150),
    bio_publique   TEXT,
    statut         VARCHAR(20) NOT NULL DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF','INACTIF'))
);

CREATE TABLE jurys_soirees (
    jury_id    UUID NOT NULL REFERENCES jurys(id) ON DELETE CASCADE,
    soiree_id  UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    PRIMARY KEY (jury_id, soiree_id)
);

CREATE TABLE criteres_notation (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edition_id UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    nom        VARCHAR(150) NOT NULL,
    note_min   NUMERIC(5,2) NOT NULL DEFAULT 0,
    note_max   NUMERIC(5,2) NOT NULL,
    ordre      SMALLINT NOT NULL,
    actif      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE notes_jury (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jury_id            UUID NOT NULL REFERENCES jurys(id) ON DELETE CASCADE,
    candidat_id        UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    soiree_id          UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    critere_id         UUID NOT NULL REFERENCES criteres_notation(id),
    valeur             NUMERIC(5,2) NOT NULL,
    verrouille         BOOLEAN NOT NULL DEFAULT FALSE,
    date_saisie        TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_modification  TIMESTAMPTZ,
    UNIQUE (jury_id, candidat_id, soiree_id, critere_id)
);

-- ============================= DOMAINE VOTES ET PAIEMENTS ==============================

CREATE TABLE paiements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id      UUID REFERENCES utilisateurs(id),
    type_paiement       VARCHAR(20) NOT NULL CHECK (type_paiement IN ('INSCRIPTION','VOTE','BILLET')),
    montant             NUMERIC(12,2) NOT NULL,
    statut              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (statut IN ('PENDING','COMPLETED','FAILED','EXPIRED','REFUNDED')),
    idempotency_key     UUID NOT NULL UNIQUE,
    date_creation       TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_finalisation   TIMESTAMPTZ,
    reference_externe   VARCHAR(150),
    manuel              BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE transactions_mobile_money (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paiement_id           UUID NOT NULL REFERENCES paiements(id) ON DELETE CASCADE,
    operateur             VARCHAR(20) NOT NULL CHECK (operateur IN ('LIGDICASH','ORANGE_MONEY','MOOV_MONEY')),
    reference_operateur   VARCHAR(150) NOT NULL,
    montant               NUMERIC(12,2) NOT NULL,
    devise                VARCHAR(3) NOT NULL DEFAULT 'XOF',
    telephone_payeur      VARCHAR(20),
    statut_operateur      VARCHAR(50),
    webhook_payload       JSONB,
    signature_webhook     VARCHAR(255),
    date_webhook          TIMESTAMPTZ,
    UNIQUE (operateur, reference_operateur)
);

CREATE TABLE votes (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id       UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    phase_id          UUID NOT NULL REFERENCES phases(id) ON DELETE CASCADE,
    type_vote         VARCHAR(25) NOT NULL
                          CHECK (type_vote IN ('EN_LIGNE_PAYANT','SOCIAL_LIKE','SOCIAL_COMMENTAIRE','PUBLIC_SUR_PLACE')),
    nombre_voix       INTEGER NOT NULL DEFAULT 1,
    points_calcules   NUMERIC(10,4),
    source_telephone  VARCHAR(20),
    date_vote         TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_id_saisie   UUID REFERENCES utilisateurs(id),
    source_externe_id VARCHAR(150) -- ID du like/commentaire côté API Facebook/TikTok (traçabilité, anti-doublon)
);

CREATE TABLE votes_payants (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id               UUID NOT NULL REFERENCES votes(id) ON DELETE CASCADE,
    paiement_id           UUID NOT NULL REFERENCES paiements(id),
    nombre_votes_achetes  INTEGER NOT NULL,
    montant_total         NUMERIC(12,2) NOT NULL,
    telephone_votant      VARCHAR(20) NOT NULL,
    CHECK (montant_total = nombre_votes_achetes * 100)
);

-- ============================= DOMAINE PHASES : POULES / DUOS / RESULTATS =============

CREATE TABLE poules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_id      UUID NOT NULL REFERENCES phases(id) ON DELETE CASCADE,
    nom           VARCHAR(50) NOT NULL,
    soiree_id     UUID REFERENCES soirees_events(id),
    date_creation TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE affectations_poules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id     UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    poule_id        UUID NOT NULL REFERENCES poules(id) ON DELETE CASCADE,
    ordre_passage   SMALLINT,
    chanson_imposee VARCHAR(255),
    UNIQUE (candidat_id, poule_id)
);

CREATE TABLE duos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_id        UUID NOT NULL REFERENCES phases(id) ON DELETE CASCADE,
    soiree_id       UUID REFERENCES soirees_events(id),
    candidat1_id    UUID NOT NULL REFERENCES candidats(id),
    candidat2_id    UUID NOT NULL REFERENCES candidats(id),
    chanson_commune VARCHAR(255),
    ordre_passage   SMALLINT,
    CHECK (candidat1_id <> candidat2_id),
    UNIQUE (candidat1_id, phase_id),
    UNIQUE (candidat2_id, phase_id)
);

CREATE TABLE resultats_phase (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id             UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    phase_id                UUID NOT NULL REFERENCES phases(id) ON DELETE CASCADE,
    points_votes_en_ligne   NUMERIC(10,4) NOT NULL DEFAULT 0,
    points_public_sur_place NUMERIC(10,4) NOT NULL DEFAULT 0,
    points_jury             NUMERIC(10,4) NOT NULL DEFAULT 0,
    total_points            NUMERIC(10,4) NOT NULL DEFAULT 0,
    rang                    INTEGER,
    statut_qualification    VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                                CHECK (statut_qualification IN ('QUALIFIE','ELIMINE','REPECHAGE','EN_ATTENTE')),
    date_calcul             TIMESTAMPTZ NOT NULL DEFAULT now(),
    motif_repechage         TEXT,
    UNIQUE (candidat_id, phase_id)
);

CREATE TABLE classements (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidat_id                 UUID NOT NULL REFERENCES candidats(id) ON DELETE CASCADE,
    edition_id                  UUID NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    total_points_cumules        NUMERIC(10,4) NOT NULL DEFAULT 0,
    rang_global                 INTEGER,
    date_derniere_mise_a_jour   TIMESTAMPTZ NOT NULL DEFAULT now(),
    officiel                    BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (candidat_id, edition_id)
);

-- ============================= DOMAINE BILLETTERIE =====================================

CREATE TABLE categories_tickets (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soiree_id                     UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    nom                           VARCHAR(20) NOT NULL CHECK (nom IN ('STANDARD','VIP','PARTENAIRE')),
    prix                          NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (prix >= 0),
    nb_places_disponibles         INTEGER NOT NULL DEFAULT 0,
    nb_places_reservees           INTEGER NOT NULL DEFAULT 0,
    date_ouverture                TIMESTAMPTZ,
    date_fermeture_reservations   TIMESTAMPTZ,
    actif                         BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (nb_places_reservees <= nb_places_disponibles)
);

CREATE TABLE reservations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soiree_id             UUID NOT NULL REFERENCES soirees_events(id) ON DELETE CASCADE,
    paiement_id           UUID REFERENCES paiements(id),
    telephone_reservant   VARCHAR(20) NOT NULL,
    nom_reservant         VARCHAR(150) NOT NULL,
    email_reservant       VARCHAR(255),
    nb_places             INTEGER NOT NULL CHECK (nb_places >= 1),
    montant_total         NUMERIC(12,2) NOT NULL DEFAULT 0,
    statut                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                              CHECK (statut IN ('PENDING','CONFIRMEE','ANNULEE','EXPIREE')),
    date_reservation      TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_expiration       TIMESTAMPTZ,
    gratuit               BOOLEAN NOT NULL DEFAULT FALSE,
    admin_id_emission     UUID REFERENCES utilisateurs(id),
    CHECK (gratuit = TRUE OR paiement_id IS NOT NULL OR statut = 'PENDING')
);

CREATE TABLE tickets (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id        UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    soiree_id             UUID NOT NULL REFERENCES soirees_events(id),
    categorie_id          UUID NOT NULL REFERENCES categories_tickets(id),
    nom_spectateur        VARCHAR(150) NOT NULL,
    telephone_spectateur  VARCHAR(20) NOT NULL,
    statut                VARCHAR(20) NOT NULL DEFAULT 'EMIS' CHECK (statut IN ('EMIS','ANNULE','UTILISE')),
    date_emission         TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_annulation       TIMESTAMPTZ
);

CREATE TABLE qrcodes_tickets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id        UUID NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    code_uuid        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    url_ticket       VARCHAR(500),
    date_generation  TIMESTAMPTZ NOT NULL DEFAULT now(),
    valide           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE scans_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    qrcode_id       UUID NOT NULL REFERENCES qrcodes_tickets(id),
    soiree_id       UUID NOT NULL REFERENCES soirees_events(id),
    agent_id        UUID NOT NULL REFERENCES utilisateurs(id),
    resultat        VARCHAR(20) NOT NULL CHECK (resultat IN ('VALIDE','INVALIDE','DEJA_UTILISE')),
    timestamp_scan  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_agent        VARCHAR(45),
    device_info     VARCHAR(255)
);

-- Anti-duplication : un seul scan VALIDE par (qrcode, soirée)
CREATE UNIQUE INDEX ux_scans_tickets_valide_unique
    ON scans_tickets (qrcode_id, soiree_id)
    WHERE resultat = 'VALIDE';

-- ============================= DOMAINE SUPPORT ==========================================

CREATE TABLE notifications (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id         UUID REFERENCES utilisateurs(id),
    telephone_destinataire VARCHAR(20),
    email_destinataire     VARCHAR(255),
    canal                  VARCHAR(10) NOT NULL CHECK (canal IN ('SMS','EMAIL','IN_APP')),
    type_notification      VARCHAR(40) NOT NULL,
    sujet                  VARCHAR(255),
    corps_message          TEXT NOT NULL,
    statut_envoi           VARCHAR(15) NOT NULL DEFAULT 'EN_ATTENTE'
                               CHECK (statut_envoi IN ('EN_ATTENTE','ENVOYE','ECHOUE')),
    nb_tentatives          SMALLINT NOT NULL DEFAULT 0 CHECK (nb_tentatives <= 3),
    lu                     BOOLEAN NOT NULL DEFAULT FALSE,
    date_creation          TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_envoi             TIMESTAMPTZ,
    reference_externe      VARCHAR(150)
);

CREATE TABLE audit_logs (
    id                 BIGSERIAL PRIMARY KEY,
    utilisateur_id     UUID REFERENCES utilisateurs(id),
    action             VARCHAR(100) NOT NULL,
    entite_concernee   VARCHAR(100) NOT NULL,
    entite_id          UUID,
    donnees_avant      JSONB,
    donnees_apres      JSONB,
    ip_source          VARCHAR(45),
    user_agent         VARCHAR(255),
    "timestamp"        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parametres_plateforme (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cle                   VARCHAR(100) NOT NULL UNIQUE,
    valeur                TEXT NOT NULL,
    type_valeur           VARCHAR(10) NOT NULL CHECK (type_valeur IN ('INTEGER','DECIMAL','STRING','BOOLEAN')),
    description           TEXT,
    modifiable_par_admin  BOOLEAN NOT NULL DEFAULT TRUE,
    date_modification     TIMESTAMPTZ,
    modifie_par           UUID REFERENCES utilisateurs(id)
);

-- ============================= INDEX ====================================================

CREATE INDEX idx_candidats_edition_statut ON candidats (edition_id, statut_profil);
CREATE INDEX idx_candidats_code ON candidats (code_candidat);
CREATE INDEX idx_votes_candidat_phase ON votes (candidat_id, phase_id);
CREATE INDEX idx_votes_type_date ON votes (type_vote, date_vote);
CREATE INDEX idx_votes_telephone_phase ON votes (source_telephone, phase_id, type_vote);
CREATE INDEX idx_notes_jury_candidat_soiree ON notes_jury (candidat_id, soiree_id);
CREATE INDEX idx_paiements_statut_date ON paiements (statut, date_creation);
CREATE INDEX idx_paiements_idempotency ON paiements (idempotency_key);
CREATE INDEX idx_tickets_telephone ON tickets (telephone_spectateur);
CREATE INDEX idx_scans_qrcode_soiree ON scans_tickets (qrcode_id, soiree_id);
CREATE INDEX idx_notifications_statut_tentatives ON notifications (statut_envoi, nb_tentatives);
CREATE INDEX idx_audit_logs_timestamp_utilisateur ON audit_logs ("timestamp", utilisateur_id);
CREATE INDEX idx_classements_edition_rang ON classements (edition_id, rang_global);
CREATE INDEX idx_reservations_telephone ON reservations (telephone_reservant);

-- ============================= PERMISSIONS AUDIT (append-only) =========================
-- Empêche toute modification/suppression des logs d'audit au niveau base de données.
-- Le rôle applicatif (ex: nks_app) doit être créé séparément par l'équipe infra ;
-- cette instruction est un no-op silencieux si le rôle n'existe pas encore en dev.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nks_app') THEN
        EXECUTE 'REVOKE DELETE, UPDATE ON audit_logs FROM nks_app';
    END IF;
END $$;

-- ============================= SEED : RÔLES =============================================

INSERT INTO roles (nom, description) VALUES
    ('VISITEUR', 'Internaute non authentifié'),
    ('CANDIDAT', 'Candidat inscrit et actif'),
    ('VOTANT_PUBLIC', 'Spectateur votant, identifié par téléphone'),
    ('JURY', 'Membre officiel du jury'),
    ('PARTENAIRE', 'Sponsor ou partenaire commercial'),
    ('ADMIN', 'Membre du comité d''organisation'),
    ('SUPER_ADMIN', 'Rôle technique de niveau supérieur'),
    ('AGENT_ACCUEIL', 'Opérateur de contrôle d''entrée (scan QR)');

-- ============================= SEED : PARAMÈTRES PLATEFORME =============================
-- Valeurs par défaut correspondant aux décisions arbitrées avec le client (voir README).

INSERT INTO parametres_plateforme (cle, valeur, type_valeur, description, modifiable_par_admin) VALUES
    ('PRIX_INSCRIPTION_FCFA', '0', 'INTEGER', 'Tarif des frais d''inscription candidat en FCFA — à configurer par l''admin avant ouverture des inscriptions (Q06 non tranchée par défaut)', TRUE),
    ('PRIX_VOTE_FCFA', '100', 'INTEGER', 'Prix d''un vote payant en FCFA (RM-18)', TRUE),
    ('DELAI_PRERESA_MINUTES', '15', 'INTEGER', 'Durée de la pré-réservation billetterie avant expiration', TRUE),
    ('MAX_TAILLE_VIDEO_MO', '100', 'INTEGER', 'Taille maximale des vidéos uploadées (Mo) — décision client', TRUE),
    ('MAX_TAILLE_PHOTO_MO', '5', 'INTEGER', 'Taille maximale des photos de profil (Mo)', TRUE),
    ('MAX_VOTES_PAYANTS_PAR_TELEPHONE_PAR_HEURE', '20', 'INTEGER', 'Seuil anti-fraude : nombre max de votes payants par numéro de téléphone et par heure — décision client', TRUE),
    ('REMBOURSEMENT_BILLETTERIE_AUTOMATIQUE', 'false', 'BOOLEAN', 'Si faux, les remboursements billetterie sont traités manuellement par l''admin — décision client', FALSE),
    ('AGE_MINIMUM_CANDIDAT', '20', 'INTEGER', 'Âge minimum requis à l''inscription (RM-01)', FALSE);
