# Handoff — Module « Vote sur place » (NKS)

Document de passation pour la suite de l'implémentation. Écrit le 13/09/2026, à la suite
d'une session de travail avec Claude portant sur la sécurisation du vote public sur place.

## 1. Contexte métier

Le vote public sur place (soirées karaoké) devait être sécurisé contre trois risques
identifiés en discussion avec le client :

1. **Double vote** — un même client votant plusieurs fois pour le même candidat.
2. **Vote sans consommation réelle** — des personnes amenées uniquement pour voter, sans
   rien acheter au bar, utilisées pour gonfler artificiellement le score d'un candidat.
3. **Interception du jeton de vote** — un tiers (staff ou autre) récupérant le lien de vote
   destiné à un client et votant à sa place.

La solution retenue **ne crée pas de nouveau système de bracelet/badge**. Elle réutilise
l'infrastructure billetterie existante (`Ticket` + `QRCodeTicket`, déjà anti-double-scan)
comme identité "1 personne physique = 1 billet", et y adosse un droit de vote unique
déclenché par une consommation réelle.

## 2. Vue d'ensemble du flux (état actuel, après la dernière itération)

Une seule action, tenue par le rôle **HOTESSE** (pas un caissier fixe au bar — voir §7) :

1. L'hôtesse, en salle, à chaque service, saisit l'UUID du billet du client dans l'écran
   `/hotesse` (`POST /caisse/consommations`).
2. Le backend (`VoteSurPlaceService.validerConsommation`) :
   a. Appelle `ScanService.scanner(...)` (service de scan **déjà existant**, réutilisé tel
      quel) — si le billet n'était pas encore scanné à l'entrée, il l'est à cet instant ; s'il
      l'était déjà (service suivant de la même soirée), l'appel est sans effet. Dans les deux
      cas, on obtient un `Ticket` garanti `UTILISE`. Le compteur d'entrées existant
      (`/scan/soiree/{id}/compteur`) continue de fonctionner sans modification.
   b. Vérifie qu'aucun `DroitVoteSurPlace` n'existe déjà pour ce billet (contrainte `UNIQUE`
      en base sur `ticket_id` — un seul droit possible, pour toujours, quel que soit le
      nombre de consommations rachetées).
   c. Vérifie que `soiree.voteSurPlaceActif` est `true`.
   d. Crée le `DroitVoteSurPlace` (statut `DISPONIBLE`).
   e. Envoie **automatiquement** un message WhatsApp (gateway déjà intégrée dans le projet,
      template pré-approuvé `karaoke_info`) au numéro déjà associé à la réservation du
      billet (`ticket.telephoneSpectateur`), contenant le lien de vote personnel. L'hôtesse
      ne voit ni ne manipule ce lien — son écran affiche juste une confirmation d'envoi.
      **Envoi best-effort** : un échec (gateway simulée en dev, réseau, numéro invalide)
      n'empêche jamais la validation de la consommation.
3. Le client ouvre le lien reçu sur son propre téléphone (`/vote-sur-place/:soireeId/:qrUuid`,
   page 100 % publique, aucune authentification — la connaissance du `qrUuid` suffit, même
   modèle de confiance que le QR du billet lui-même).
4. Le client voit la liste des candidats de la soirée, peut (facultativement, jamais
   bloquant) laisser son numéro et autoriser sa géolocalisation, choisit un candidat, confirme.
5. Le backend (`VoteSurPlaceService.voter`) verrouille la ligne du droit de vote (verrou
   pessimiste), vérifie qu'il est encore `DISPONIBLE`, crée le `Vote` réel
   (`TypeVote.PUBLIC_SUR_PLACE`), passe le droit à `UTILISE`. Le lien devient alors
   définitivement inutilisable.

**Garantie obtenue** : 1 billet réellement entré + 1 consommation réellement validée = au
plus 1 vote sur place pour cette soirée, quel que soit le nombre de téléphones détenus par
la personne, et sans dépendre d'une reconnaissance manuelle du staff ("qui a déjà voté ?").

## 3. Modèle de données

### Nouvelle table `droits_vote_sur_place` (migration `V13`, complétée par `V14`)

| Colonne | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `ticket_id` | UUID, **UNIQUE**, FK `tickets(id)` | Le cœur de la garantie anti-doublon |
| `soiree_id` | UUID, FK `soirees_events(id)` | Dénormalisé pour simplifier les requêtes |
| `caissier_id` | UUID, FK `utilisateurs(id)` | ⚠️ nom de colonne **conservé tel quel** malgré le renommage du rôle en HOTESSE (voir §7 — choix délibéré pour limiter le churn) |
| `statut` | VARCHAR, CHECK `DISPONIBLE`/`UTILISE` | |
| `date_emission` | TIMESTAMPTZ | |
| `lien_whatsapp_envoye` | BOOLEAN | true si l'envoi WhatsApp a réussi |
| `candidat_id` | UUID, FK `candidats(id)`, nullable | rempli seulement au vote |
| `date_vote` | TIMESTAMPTZ, nullable | rempli seulement au vote |
| `telephone_votant` | VARCHAR(20), nullable | **audit uniquement**, jamais vérifié ni requis |
| `position_latitude` / `position_longitude` / `position_precision_m` | NUMERIC, nullable | **audit uniquement**, capturés via Geolocation API navigateur, jamais bloquants |

Contrainte CHECK : `statut='DISPONIBLE'` ⇒ `candidat_id`/`date_vote` NULL, et vice-versa.

### Rôle `HOTESSE`

- Introduit en `V13` sous le nom `CAISSIER`, **renommé en `HOTESSE` par `V14`** (`UPDATE roles
  SET nom = 'HOTESSE' ... WHERE nom = 'CAISSIER'` + mise à jour du `CHECK` constraint sur
  `roles.nom`). **Les deux migrations doivent être appliquées dans l'ordre** — ne jamais
  sauter `V13` en pensant que `V14` suffit.
- Ajouté à `Enums.RoleName` et à `UtilisateurAdminService.ROLES_AUTORISES` — un compte
  HOTESSE se crée via l'écran admin existant "Utilisateurs & rôles"
  (`POST /admin/utilisateurs`), aucun écran de gestion de comptes spécifique n'a été créé.

## 4. Endpoints API

| Méthode & route | Rôle | Description |
|---|---|---|
| `POST /caisse/consommations` | `HOTESSE`, `ADMIN`, `SUPER_ADMIN` | Body `{ qrUuid, soireeId }`. Scanne le billet si besoin + crée le droit de vote + déclenche l'envoi WhatsApp. Retourne `DroitVoteResponse`. |
| `GET /vote-sur-place/{soireeId}/{qrUuid}` | public | Retourne le statut du droit + liste des candidats (si `DISPONIBLE`). |
| `POST /vote-sur-place/{soireeId}/{qrUuid}/voter` | public | Body `{ candidatId, telephoneVotant?, positionLatitude?, positionLongitude?, positionPrecisionM? }`. Vote définitif, unique. |

`SecurityConfig.PUBLIC_ALL_METHODS_ENDPOINTS` contient `/vote-sur-place/**` — ne pas
oublier ce détail si la route est un jour renommée, sinon Spring Security renverra 401 sur
un endpoint censé être public.

## 5. Fichiers créés

**Backend**
- `domain/DroitVoteSurPlace.java`
- `repository/DroitVoteSurPlaceRepository.java` (dont `findByTicketIdForUpdate` — verrou
  pessimiste, à conserver impérativement pour l'anti-double-vote concurrent)
- `service/VoteSurPlaceService.java` — **le cœur de la logique métier**, Javadoc de classe
  à jour avec la cinématique complète
- `dto/votesurplace/{DroitVoteResponse,CaisseValiderRequest,VoterSurPlaceRequest}.java`
- `controller/{CaisseController,VoteSurPlaceController}.java`
- `db/migration/V13__caissier_droit_vote_sur_place.sql`
- `db/migration/V14__renommer_caissier_en_hotesse.sql`

**Frontend**
- `modules/billetterie/caisse/caisse.component.{ts,html,scss}` — écran hôtesse (mirroir de
  `scan.component.ts`), route `/hotesse`
- `modules/public/vote-sur-place/vote-sur-place.component.{ts,html,scss}` — page publique
  de vote, route `/vote-sur-place/:soireeId/:qrUuid`

## 6. Fichiers modifiés (hors migrations)

**Backend** : `domain/enums/Enums.java` (rôle `HOTESSE`, enum `StatutDroitVote`),
`service/UtilisateurAdminService.java`, `config/SecurityConfig.java`.

**Frontend** : `core/auth/rbac.ts`, `core/services/admin.service.ts`,
`core/services/billetterie.service.ts`, `core/models/index.ts` (ajout `soireeId` sur
`Reservation`, `DroitVoteResponse`, `CandidatVoteSurPlace`), `app.routes.ts`,
`modules/public/public.routes.ts`, `modules/admin/utilisateurs/utilisateurs.component.ts`,
`modules/billetterie/tickets/tickets.component.html` (lien "Voter sur place").

## 7. Décisions prises et pourquoi (pour éviter de les remettre en question sans relire l'historique)

- **Pas de bracelet/badge séparé** : le `Ticket` existant sert d'identité physique
  unique — moins de développement, moins de matériel, réutilise l'anti-double-scan déjà
  audité.
- **Pas d'OTP par téléphone comme mécanisme principal** : jugé peu fiable dans un contexte
  où une même personne peut détenir plusieurs puces (remarque explicite du client).
- **WhatsApp plutôt que SMS** : gateway déjà intégrée et fonctionnelle dans le projet
  (`HdrStreamWhatsappGateway`, template `karaoke_info` déjà approuvé Meta) — aucun nouveau
  compte/fournisseur à mettre en place. Fallback simulé si `NKS_WHATSAPP_URL` n'est pas
  configuré (log uniquement, pas d'appel réseau).
- **Téléphone/géolocalisation = audit seulement, jamais bloquant** : décision explicite du
  client pour ne pas risquer de rejeter un vote légitime (GPS imprécis en intérieur, refus
  de permission navigateur, téléphone partagé en famille).
- **Fusion scan-entrée + activation-consommation, rôle CAISSIER→HOTESSE** : décision prise
  en toute fin de session pour éliminer le goulot d'étranglement d'un point de caisse
  unique. Plusieurs hôtesses, chacune avec son propre compte, peuvent désormais activer des
  consommations en parallèle depuis leur téléphone, à table, sans faire la queue au bar.
  Techniquement, `VoteSurPlaceService` appelle directement `ScanService.scanner()` en
  interne — **ne pas dupliquer cette logique**, la réutiliser.
- **Colonne `caissier_id` non renommée** : choix délibéré pour limiter le nombre de fichiers
  touchés lors du renommage de rôle. Si vous renommez cette colonne un jour, mettez à jour
  `DroitVoteSurPlace.java` (champ `caissier`) et toutes les requêtes qui en dépendent.

## 8. Ce qui n'est PAS fait / limites connues

1. **GAP-03 (pré-existant, documenté dans le code)** : la page "Mes tickets"
   (`tickets.component.ts`) ne reçoit pas le vrai `qrUuid` du backend —
   `ReservationPublicResponse` n'expose pas ce champ, et le frontend en génère un faux
   côté client pour la démo (`QRCode.toDataURL('NKS:' + r.qrUuid)` où `r.qrUuid` est en
   réalité toujours `undefined` en usage réel). **Tant que ce gap n'est pas corrigé, le
   lien "Voter sur place" ajouté sur cette page ne fonctionnera pas pour un vrai client** —
   il faut un endpoint qui expose le vrai `qrUuid` par ticket (protégé, car c'est un secret
   d'entrée), probablement l'endpoint `/reservations/*/ticket` déjà référencé comme public
   dans `SecurityConfig` mais **jamais implémenté**. C'est la priorité n°1 avant la
   prochaine soirée réelle.
2. **Aucune vérification de compilation effectuée par Claude** durant une bonne partie de
   la session (sandbox bash indisponible) — l'utilisateur a confirmé avoir compilé
   manuellement à un moment, mais **tout ce qui a été ajouté après le rôle HOTESSE (la
   fusion scan+activation) n'a pas encore été recompilé/testé**. À faire en priorité.
3. **Migration V14 pas encore appliquée** en base (à la connaissance de Claude) — attention
   à l'ordre V13 puis V14 lors du déploiement.
4. **Pas de test automatisé** écrit pour ce module (ni unitaire ni intégration) — à prévoir.
5. **Pas de reconciliation/audit dashboard** pour croiser `telephone_votant` vs
   `tickets.telephone_spectateur`, ni pour visualiser les positions capturées — les données
   sont stockées mais rien ne les exploite encore côté admin. Un écran ou export CSV serait
   utile pour que l'équipe puisse réellement s'en servir en cas de contestation.
6. **Pas de contrôle de capacité par point de vente/hôtesse** : n'importe quel compte
   HOTESSE peut valider une consommation pour n'importe quelle soirée — pas de restriction
   par soirée assignée (contrairement au rôle JURY qui a un système d'affectation aux
   soirées). À évaluer si c'est un problème métier (ex. une hôtesse d'une autre soirée qui
   se trompe de soirée dans le formulaire).
7. **Le message WhatsApp est en dur dans `VoteSurPlaceService.envoyerLienWhatsapp`** (pas de
   gestion de traduction/i18n, pas de personnalisation par édition).

## 9. Comment tester manuellement le flux complet

1. Créer/avoir une soirée avec `vote_sur_place_actif = true` et des candidats affectés
   (via poule ou duo).
2. Créer un compte `HOTESSE` via l'écran admin "Utilisateurs & rôles".
3. Faire une réservation de billet (gratuite via
   `POST /admin/billetterie/tickets-gratuits` est le plus simple pour tester) pour obtenir
   un `Ticket` + `QRCodeTicket`. Récupérer le `codeUuid` directement en base
   (`SELECT code_uuid FROM qrcodes_tickets WHERE ticket_id = ...`) puisque le frontend ne
   l'expose pas encore correctement (cf. GAP-03 ci-dessus).
4. Se connecter avec le compte HOTESSE, aller sur `/hotesse`, sélectionner la soirée,
   saisir ce `codeUuid`. Vérifier en base que `tickets.statut = 'UTILISE'` et qu'une ligne
   `droits_vote_sur_place` a été créée avec `statut = 'DISPONIBLE'`.
5. Vérifier les logs backend pour le message WhatsApp (mode simulé si
   `NKS_WHATSAPP_URL` n'est pas configuré — cherchez `WhatsApp simulé vers ...`) et en
   extraire le lien.
6. Ouvrir ce lien dans un navigateur (mode privé, pour simuler un autre appareil), vérifier
   l'affichage de la liste des candidats, voter.
7. Réessayer de rouvrir le même lien → doit afficher "vote déjà enregistré". Réessayer de
   valider une nouvelle consommation avec le même `codeUuid` côté hôtesse → doit renvoyer
   une erreur 409 "Ce billet a déjà un droit de vote pour cette soirée".

## 10. Historique de la conversation (pour contexte, pas à ré-implémenter)

Dans la même session, avant ce module, la scoping des votes "sur place" dans le calcul du
classement officiel (`ClassementService`) et dans la grille de délibération
(`DeliberationService`) a été corrigée pour être spécifique à la soirée du candidat plutôt
que cumulée sur toute la phase — sans changement de schéma, en résolvant la soirée de
chaque candidat via `AffectationPoule`/`Duo`. Cette partie est indépendante du module vote
sur place mais partage certains repositories (`AffectationPouleRepository`,
`DuoRepository`) — les deux `resoudreSoireeId`/`candidatsDeLaSoiree` sont volontairement
dupliqués plutôt que factorisés, à la suite du même choix déjà fait ailleurs dans le code
(cf. `JuryController.candidatsANoter`).
