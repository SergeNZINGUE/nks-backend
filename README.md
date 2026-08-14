# NKS — Night Karaoke Stars — Backend

API Spring Boot pour la plateforme de gestion de la compétition Night Karaoke Stars
(Restaurant La Terrasse × Bright Group, Ouagadougou). Générée à partir du *Rapport de
Conception Phase 0* (v1.0, 2026-06-22) après arbitrage des points bloquants avec le client.

Stack : **Java 17 · Spring Boot 3.3 · PostgreSQL 16 · Flyway · Spring Security (JWT RS256) ·
Cloudinary · LigdiCash · Africa's Talking**. Architecture monolithe modulaire (ADR-07).

---

## 1. Décisions arbitrées avec le client (avant génération du code)

Le rapport de conception listait 7 incohérences bloquantes (§21.2) et 20 questions ouvertes
(§18). Les points suivants ont été tranchés avec le client avant de démarrer le backend :

| Sujet | Décision retenue | Impact code |
|---|---|---|
| Taille max vidéo | **100 Mo** (tranche RM-05, contradiction 100/500 Mo du CdC) | `nks.media.max-video-size-bytes`, contrainte CHECK sur `videos` |
| Votes sociaux Facebook/TikTok | **Intégration API réelle** (remplace la saisie manuelle H3 du rapport) | `SocialVoteProvider` (Facebook Graph + TikTok), `SocialVoteIngestionService`, colonnes `candidats.post_id_facebook/post_id_tiktok` (migration V2) |
| Jury en finale | **Obligatoire** (H7 confirmée) | `phases.jury_obligatoire = true` par défaut |
| Vote public sur place | **Interface web mobile via QR code en salle** (H4 confirmée) | `TypeVote.PUBLIC_SUR_PLACE`, pas de module séparé |
| Tarif inscription candidat | **Configurable**, pas de valeur imposée dans le code | `parametres_plateforme.PRIX_INSCRIPTION_FCFA` (seed à `0`, à définir par l'admin avant ouverture des inscriptions) |
| Remboursement billetterie | **Traitement manuel**, aucune automatisation | `BilletterieService.annulerReservation()` ne déclenche jamais `PaymentGateway.rembourserTransaction()` |
| Anti-fraude votes payants | **Configurable**, défaut 20 votes/heure/numéro | `parametres_plateforme.MAX_VOTES_PAYANTS_PAR_TELEPHONE_PAR_HEURE` |
| Nombre de jurés par soirée | **Variable**, pas de contrainte de cardinalité | Aucune contrainte fixe sur `jurys_soirees` |

Toutes les autres recommandations du rapport (ADR-01 à ADR-10, hypothèses H1/H2/H5/H8/H9/H10)
ont été adoptées telles quelles : Angular + Spring Boot, PostgreSQL, Cloudinary (MVP),
LigdiCash prioritaire, monolithe modulaire, JWT stateful (refresh tokens révocables),
UUID pour les identifiants métier, une seule édition `EN_COURS` à la fois.

### ⚠️ Votes sociaux — point d'attention majeur

Le passage à une intégration API réelle (au lieu de la saisie manuelle prévue initialement)
est la décision qui ajoute le plus de risque projet. Elle suppose :

1. Une **application Meta (Facebook)** avec les permissions `pages_show_list` et
   `pages_read_engagement` — voir la procédure complète ci-dessous (§2bis). Bonne nouvelle :
   contrairement à ce qu'on pourrait croire, **App Review n'est pas obligatoire** dans notre cas
   (compte perso avec un rôle sur l'app + lecture des Pages qu'il gère déjà = Standard Access
   suffisant). Le processus est donc de l'ordre de l'heure, pas des semaines.
2. Une **application TikTok for Developers** avec accès à l'API vidéo — accès et champs
   exacts à confirmer selon le niveau accordé par TikTok (non traité dans cette phase, voir
   note ci-dessous).
3. Qu'**un post officiel par candidat** soit publié sur chaque plateforme (le like/commentaire
   étant compté sur ce post) — ce champ (`post_id_facebook`, `post_id_tiktok`) doit être
   renseigné par l'admin sur chaque fiche candidat.

Tant que ces accès ne sont pas obtenus, le polling reste **désactivé** par défaut
(`parametres_plateforme.SOCIAL_VOTES_POLLING_ACTIF = false`). L'activer sans token valide ne
casse rien : `FacebookGraphSocialVoteProvider`/`TikTokSocialVoteProvider` renvoient
`Optional.empty()` et sont ignorés silencieusement (log DEBUG).

**Recommandation :** lancer ces démarches d'approbation dès la Phase 1 du planning (cf. rapport
§17), en parallèle du développement, pour ne pas bloquer le lancement de la présélection.

### 2bis. Configuration Meta / Facebook — procédure pas à pas

Ceci concerne uniquement Facebook (le TikTok n'a pas été demandé/traité dans cette phase).
À faire une seule fois avec le compte Facebook administrateur de la Page NKS.

**⚠️ Terminologie Meta (mise à jour 2025-2026) :** il n'y a plus de "produit Pages API" à ajouter
dans une liste de produits — Meta fonctionne maintenant par **use cases**. Le use case à utiliser
s'appelle **"Manage everything on your Page"**.

**Étape 1 — Créer l'app Meta avec le bon use case**
1. Aller sur [developers.facebook.com/apps](https://developers.facebook.com/apps) → *Créer une app*.
2. Lors de la création, choisir le use case **"Manage everything on your Page"** (c'est cette
   étape qui active la Pages API — pas un ajout de produit après coup).
3. Nommer l'app (ex. `NKS - Votes Sociaux`) et la rattacher à votre Business Manager si vous
   en avez un (sinon Meta en crée un automatiquement).
4. Si l'app existe déjà sans ce use case : dans le **Dashboard** de l'app, chercher la section
   listant les use cases ajoutés et l'option pour en ajouter un nouveau → sélectionner
   **"Manage everything on your Page"**.

**Étape 2 — Ajouter les permissions**
1. Dans le **Dashboard** de l'app, cliquer sur le use case **"Manage everything on your Page"**
   pour le personnaliser (*Customize*).
2. Par défaut, ce use case ajoute automatiquement `business_management`, `pages_show_list`,
   `public_profile`. Ajouter manuellement (bouton **Add**) :
   - **`pages_read_engagement`**
   - **`pages_read_user_content`** — ⚠️ **indispensable en pratique**, malgré ce que dit la
     doc Meta. Officiellement `pages_read_engagement` suffit pour lire un nombre de
     likes/commentaires (pas le contenu). En réalité, Meta a un bug/comportement incohérent
     largement rapporté par d'autres développeurs (forums Meta, non résolu à ce jour) où les
     endpoints `likes.summary`/`reactions.summary`/`comments.summary` renvoient l'erreur
     `(#10) This endpoint requires the 'pages_read_engagement' permission...` **même quand
     cette permission est bien présente et vérifiée dans le token**. Ajouter aussi
     `pages_read_user_content` a résolu le problème en pratique lors des tests NKS
     (voir `FacebookGraphSocialVoteProviderTest`).
3. Vérifier que votre compte Facebook personnel a bien un rôle **Admin/Développeur/Testeur**
   sur l'app (Réglages → Rôles de l'app) — c'est ce qui permet d'éviter l'App Review, tant que
   vous ne lisez que des Pages que ce compte gère lui-même.
4. Ces permissions sont accessibles directement en **Standard Access** dans ce contexte.

**Étape 3 — Obtenir un token courte durée via l'Explorateur Graph API**
1. Aller sur [developers.facebook.com/tools/explorer](https://developers.facebook.com/tools/explorer).
2. Sélectionner votre app NKS dans le menu déroulant en haut à droite.
3. *Get Token* → *Get User Access Token* → cocher `pages_show_list`, `pages_read_engagement`
   **et `pages_read_user_content`** → Générer. Si une popup de consentement apparaît, vérifier
   que la Page NKS est bien incluse ; si aucune popup n'apparaît (session déjà autorisée),
   révoquer l'accès existant depuis [facebook.com/settings?tab=business_tools](https://www.facebook.com/settings?tab=business_tools)
   puis regénérer, pour forcer un nouveau consentement complet avec les 3 permissions.
4. Appeler `GET /me/accounts` : la réponse liste vos Pages, avec pour chacune un `id` (= Page ID)
   et un `access_token` (Page Access Token, mais **courte durée**, ~1h).

**Étape 4 — Échanger contre un token longue durée (obligatoire pour la prod)**
1. D'abord, échanger le **User Access Token** courte durée contre un User Access Token longue
   durée (~60 jours) via :
   ```
   GET https://graph.facebook.com/v26.0/oauth/access_token
     ?grant_type=fb_exchange_token
     &client_id={app-id}
     &client_secret={app-secret}
     &fb_exchange_token={short-lived-user-token}
   ```
   (`app-id`/`app-secret` se trouvent dans Réglages → Basique de l'app.)
2. Rappeler `GET /me/accounts?access_token={long-lived-user-token}` : le `access_token` renvoyé
   pour chaque Page est alors un **Page Access Token qui n'expire jamais** (tant que le token
   utilisateur longue durée reste valide et que les rôles ne changent pas).
3. C'est **ce token Page longue durée** qu'il faut mettre dans `FACEBOOK_ACCESS_TOKEN` — pas
   celui de l'étape 3.

**Étape 5 — Renseigner les variables et les posts**
1. Dans `.env` : `FACEBOOK_PAGE_ID` = l'`id` de la Page obtenu à l'étape 3/4,
   `FACEBOOK_ACCESS_TOKEN` = le Page Access Token longue durée de l'étape 4.
2. Pour chaque candidat, l'admin renseigne `candidats.post_id_facebook` (visible dans l'URL du
   post publié sur la Page, ou via l'API `GET /{page-id}/posts`).
3. Activer le polling : `parametres_plateforme.SOCIAL_VOTES_POLLING_ACTIF = true` (via l'admin
   ou directement en base).

**Points de vigilance**
- Le token Page longue durée peut néanmoins être invalidé si le mot de passe du compte change,
  si l'app perd un rôle, ou si l'utilisateur révoque l'accès — en cas d'erreur Graph API
  code `190` dans les logs (`FacebookGraphSocialVoteProvider` le journalise explicitement en
  `ERROR`), il faut refaire l'étape 4.
- La version d'API Graph n'est garantie que ~2 ans après sa sortie ; le code est actuellement
  calé sur **v26.0** (`nks.social-votes.facebook.graph-api-version` dans `application.yml`) —
  à surveiller sur [developers.facebook.com/docs/graph-api/guides/versioning](https://developers.facebook.com/docs/graph-api/guides/versioning).
- Par défaut, seuls les "J'aime" classiques comptent comme like (conforme au libellé RM-19 du
  rapport). Pour compter toute réaction (Love/Haha/Wow/Sad/Angry/Care), passer
  `nks.social-votes.facebook.compter-toutes-reactions` à `true`.

### 2ter. Configuration Africa's Talking (SMS) — ✅ validée

Config testée et fonctionnelle en sandbox (envoi réel confirmé, `status: Success`).

1. Dashboard [account.africastalking.com](https://account.africastalking.com) → app **Sandbox**
   → `Username` est toujours `sandbox` (`AT_USERNAME=sandbox`), clé sous **Settings → API Key**.
2. **⚠️ Piège à connaître :** un Sender ID alphanumérique (ex. `NKS`) doit être **enregistré sur
   le compte** avant de pouvoir être utilisé comme `from` — sinon l'API répond `200 OK` avec
   `"Message":"InvalidSenderId","Recipients":[]` (échec silencieux, pas d'exception levée). À
   enregistrer dans le dashboard, section **SMS → Sender ID** (instantané en sandbox, peut
   prendre plusieurs jours en production le temps de la validation télécom).
3. Après génération d'une nouvelle clé API, **attendre ~5 minutes** avant de tester — sinon
   401 Unauthorized même avec une clé valide (propagation côté Africa's Talking).
4. En production, remplacer `AT_USERNAME=sandbox` par le username réel du compte/app de
   production, et refaire la validation du Sender ID `NKS` pour cet environnement.

Test de bout en bout disponible : `AfricasTalkingSmsGatewayTest#envoi_reel_sandbox` (voir
Javadoc de la classe pour les variables d'environnement à définir).

---

## 2. Démarrage local

### Prérequis
- JDK 17+, Maven 3.9+
- Docker (pour PostgreSQL local) ou une instance PostgreSQL 14+ existante

### Étapes

```bash
cp .env.example .env        # puis renseigner les valeurs (voir §3 pour les clés JWT)
docker compose up -d        # démarre PostgreSQL sur localhost:5432
mvn clean compile           # vérifie que tout compile
mvn spring-boot:run         # démarre l'API sur http://localhost:8080/api/v1
```

Flyway applique automatiquement les migrations (`src/main/resources/db/migration`) au
démarrage : schéma complet + seed des 8 rôles + paramètres plateforme par défaut.

Documentation interactive une fois démarré : `http://localhost:8080/api/v1/docs` (Swagger UI).

### ⚠️ Compilation non vérifiée dans cette session

Le code a été généré et relu manuellement (types, requêtes JPQL, cohérence des noms de
propriétés Lombok/Spring Data) mais **`mvn clean compile` n'a pas pu être exécuté** : le
bac à sable d'exécution de l'agent était indisponible pendant toute la session. Merci de
lancer `mvn clean compile` (puis `mvn test`) en premier avant toute autre chose et de me
signaler les éventuelles erreurs — je les corrigerai immédiatement.

## 3. Générer les clés JWT (RS256)

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -out keys/private_key.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in keys/private_key.pem -out keys/public_key.pem
```

En dev, si aucune clé n'est trouvée, une paire éphémère est générée automatiquement au
démarrage (log `WARN`) — pratique mais invalide les tokens à chaque redémarrage. **Obligatoire
en production** (`JwtKeyConfig` lève une exception au démarrage si absent en profil `prod`).

---

## 4. Ce qui est livré

- **Schéma PostgreSQL complet** (`V1__init_schema.sql`, `V2__publications_sociales_candidats.sql`)
  correspondant au MPD du rapport (§10), avec toutes les contraintes CHECK/UNIQUE/index.
- **32 entités JPA** (31 du MCD + `refresh_tokens` pour la révocation de session).
- **Sécurité JWT RS256 + RBAC** à 8 rôles, refresh tokens révocables (ADR-06).
- **Modules fonctionnels complets** : candidature (WF-01/02), paiement + webhook LigdiCash
  idempotent (WF-03), votes payants + sociaux + calcul de classement pondéré (WF-04 à WF-07),
  notation jury (WF-06), billetterie + QR code + scan anti-doublon avec verrou pessimiste
  (WF-09 à WF-11), poules/duos/repêchage (WF-08), back-office admin (dashboard, communication
  groupée, gestion jury/partenaires, exports CSV, audit logs).
- **3 jobs planifiés** : recalcul classement horaire, expiration pré-réservation (15 min),
  relance notifications échouées.
- **Journal d'audit automatique** (`@Auditable` + AOP) sur les actions critiques du §14.11.

## 5. Travail restant / limitations connues

- **Exports PDF** (rapport financier, palmarès — §13.15) : seul le **CSV** est implémenté.
  La génération PDF nécessite d'ajouter une librairie (OpenPDF ou iText) au `pom.xml` et un
  template — non fait pour rester dans un périmètre raisonnable de ce premier scaffold.
- **Intégration LigdiCash** (`LigdiCashGateway`) : implémentée sur la base du schéma REST
  classique des gateways Mobile Money ouest-africaines (invoice + webhook HMAC), mais les
  noms exacts d'endpoints/champs doivent être **vérifiés contre la documentation officielle
  et un environnement sandbox** avant mise en production (risque R3 du rapport).
- **Votes sociaux Facebook** : code et config prêts (v26.0, toggle likes/réactions) — voir
  §2bis pour la procédure d'obtention du `FACEBOOK_PAGE_ID`/`FACEBOOK_ACCESS_TOKEN`, à faire
  par Serge avec le compte admin de la Page. Ne dépend plus d'une App Review longue.
- **Votes sociaux TikTok** : non traité dans cette phase — `TikTokSocialVoteProvider` reste
  en attente de config (`TIKTOK_CLIENT_KEY`/`SECRET`/`ACCESS_TOKEN`), dépend de TikTok for
  Developers dont le processus d'accès n'a pas été investigué.
- **Tests** : aucun test automatisé n'a encore été écrit (JUnit/Testcontainers recommandés au
  §15 du rapport — prochaine étape logique une fois la compilation validée).
- **Rate limiting HTTP** (§14.8, ex. 5 tentatives login/15 min) : le seuil anti-fraude votes
  est implémenté en base (`MAX_VOTES_PAYANTS_PAR_TELEPHONE_PAR_HEURE`), mais un rate limiting
  générique par IP (Redis + Bucket4j, recommandé §14.8) n'est pas encore en place.
- **Frontend Angular** : non commencé — ce dépôt ne couvre que le backend.
- **Mot de passe candidat/jury à la création** : le CdC ne précise pas ce flux ; un mot de
  passe temporaire aléatoire est généré et envoyé par SMS/e-mail (voir
  `CandidatureService.creerUtilisateurCandidat`, `JuryAdminService.creer`). Un flux
  "définir mon mot de passe" dédié serait plus propre à ajouter côté frontend +
  `POST /auth/reinitialiser-mot-de-passe` (endpoint prévu dans `SecurityConfig` mais pas
  encore implémenté côté contrôleur).

## 6. Structure du projet

```
src/main/java/bf/laterrasse/nks/
├── config/        # Propriétés typées (@ConfigurationProperties), SecurityConfig
├── domain/        # Entités JPA + enums (bf.laterrasse.nks.domain.enums.Enums)
├── repository/    # Spring Data JPA
├── service/       # Logique métier
├── controller/    # Endpoints REST (voir rapport §13 pour la correspondance)
├── dto/           # Records de requête/réponse
├── security/      # JWT (RS256), UserDetails, filtre, CurrentUserProvider
├── gateway/        # Abstractions externes : payment (LigdiCash), media (Cloudinary),
│                   # sms (Africa's Talking), email (SMTP), social (Facebook/TikTok)
├── event/         # Événements applicatifs (découplage inter-modules, ADR-07)
├── job/           # @Scheduled : classement, expiration réservation, retry notifications
├── aop/           # @Auditable + AuditAspect (journal d'audit automatique)
└── exception/     # Exceptions métier + GlobalExceptionHandler
```

## 7. Prochaines étapes suggérées

1. `mvn clean compile` et corriger les éventuelles erreurs résiduelles.
2. Écrire les tests unitaires prioritaires du rapport §15.1 (`ClassementService`,
   `PaiementService`, `VoteService`, `CandidatureService`).
3. Lancer les démarches Meta App Review / TikTok for Developers si les votes sociaux API
   restent la cible (délai long — à initier au plus tôt).
4. Ouvrir un compte sandbox LigdiCash et vérifier/adapter `LigdiCashGateway` contre la vraie
   API.
5. Démarrer le frontend Angular (structure de modules déjà documentée au rapport §12.1).

---

## 8. Déploiement sur Jelastic Cloud

Approche retenue : le **template natif "Spring Boot"** de Jelastic (pas de conteneur Docker à
maintenir) + un nœud **PostgreSQL** dans le même environnement. C'est le chemin le plus simple
et le plus proche de ce que Jelastic gère nativement (auto-tuning mémoire, logs, SSL intégré).

### Étape 1 — Créer l'environnement
1. Dashboard Jelastic → **New Environment**.
2. Onglet **Java** → sélectionner le template **Spring Boot** dans la colonne "Application
   Server Layer".
3. Ajouter un nœud **PostgreSQL** (colonne "Database Layer", choisir la version 16 pour matcher
   le `docker-compose.yml` local).
4. Nommer l'environnement (ex. `nks-backend`), choisir les ressources (cloudlets — commencer
   petit, la scalabilité verticale automatique est activée par défaut), **Create**.

### Étape 2 — Build du jar
```bash
mvn clean package -DskipTests
```
Génère `target/nks-backend-0.1.0-SNAPSHOT.jar`.

### Étape 3 — Variables d'environnement
Dans le Dashboard → nœud Spring Boot → **Variables**, ajouter (valeurs issues de `.env`) :

| Variable | Valeur |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` ⚠️ **indispensable** — le jar écoute sur `8082` par défaut (`application.yml`), mais Jelastic route le trafic externe vers le port interne `8080`. Sans ce réglage, l'app tournera mais restera inaccessible depuis l'extérieur. |
| `DB_URL` | `jdbc:postgresql://<hôte-interne-postgres>:5432/nks` — l'hôte interne est visible dans le Dashboard du nœud PostgreSQL (ex. `postgresql.<envName>`) |
| `DB_USERNAME` / `DB_PASSWORD` | ceux définis à la création du nœud PostgreSQL |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` | voir Étape 4 |
| `CORS_ALLOWED_ORIGINS` | domaine du futur frontend |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | valeurs validées lors des tests |
| `LIGDICASH_*` | selon compte LigdiCash (sandbox ou prod) |
| `AT_USERNAME` / `AT_API_KEY` / `AT_SENDER_ID` | valeurs validées lors des tests (utiliser le vrai username de prod si disponible, pas `sandbox`) |
| `FACEBOOK_PAGE_ID` / `FACEBOOK_ACCESS_TOKEN` | valeurs validées lors des tests |

`application-prod.yml` n'a **aucune valeur par défaut** pour `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
— le démarrage échouera explicitement si l'une manque, plutôt que de démarrer mal configuré.

### Étape 4 — Clés JWT
Ne pas committer les clés. Deux options :
- **Générer directement sur le nœud** via SSH Gate Jelastic (menu du nœud → SSH), avec les mêmes
  commandes `openssl` que pour le local (§3), puis pointer `JWT_PRIVATE_KEY_PATH`/`_PUBLIC_KEY_PATH`
  vers leur emplacement (ex. `file:/home/jelastic/keys/private_key.pem`).
- Ou les uploader via le **Configuration Manager** (icône dossier dans le Dashboard) dans un
  dossier `keys/` à la racine du nœud.

### Étape 5 — Déployer le jar
Dashboard → **Deployment Manager** → **Upload** le jar de l'étape 2 → **Deploy to** → sélectionner
l'environnement `nks-backend` → **Deploy**.

### Étape 6 — Vérifier
- Suivre `run.log` (Dashboard → nœud → Logs) pendant le démarrage — Flyway doit appliquer les
  migrations automatiquement (schéma + seed des 8 rôles) au premier lancement.
- **Open in Browser**, puis tester `https://<envName>.<domaine-jelastic>/api/v1/docs` (Swagger).
- Si erreur de connexion DB : vérifier l'hôte interne PostgreSQL exact dans `DB_URL` (le nom
  peut différer légèrement selon le provider Jelastic utilisé).

### Étape 7 (optionnel) — Domaine personnalisé + SSL
- SSL sur le domaine interne Jelastic : gratuit et automatique (wildcard SSL Jelastic).
- Domaine personnalisé (ex. `api.laterrasse-nks.com`) : configurer un CNAME vers le domaine
  Jelastic, puis activer le add-on **Let's Encrypt** (nécessite un load balancer certifié dans
  l'environnement).
