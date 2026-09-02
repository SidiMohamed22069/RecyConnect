# RecyConnect — API

RecyConnect est une application qui motive les gens à recycler.

Cette API est la place de marché du recyclage : les utilisateurs publient des matériaux
recyclables, les acheteurs font des offres, et une file d'offres classée par montant
permet au vendeur de choisir. L'authentification se fait par numéro de téléphone
mauritanien avec vérification par SMS, et les notifications sont livrées en temps réel
par WebSocket ou par push Firebase.

**Stack :** Spring Boot 3.5.6 · Java 21 · PostgreSQL · JWT · WebSocket/STOMP · Firebase Cloud Messaging

---

## Prérequis

| Outil | Version | Note |
|---|---|---|
| JDK | **21** | Obligatoire — le projet ne compile pas avec le JDK 17 |
| PostgreSQL | 16+ | Ou utiliser Docker Compose (voir plus bas) |
| Maven | — | Inutile de l'installer : le wrapper `./mvnw` s'en charge |

Si `java -version` affiche autre chose que 21, pointez `JAVA_HOME` sur un JDK 21 :

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

---

## Démarrage rapide

### 1. Créer la base

```bash
createdb RecyConnect
```

### 2. Créer la configuration locale

`src/main/resources/application.properties` **n'est pas versionné** : il contient des
secrets. Partez du modèle fourni :

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

### 3. Générer un secret JWT

Il n'y a pas de valeur par défaut : l'application refuse de démarrer sans secret.

```bash
openssl rand -base64 48
```

Renseignez le résultat dans `JWT_SECRET` (variable d'environnement ou valeur de repli
dans votre `application.properties` local). Le secret doit faire **au moins 32 octets**.

### 4. Lancer

```bash
./mvnw spring-boot:run
```

L'API écoute sur **http://localhost:8081**.

---

## Configuration

Toute la configuration passe par des variables d'environnement. Le profil `prod`
(`application-prod.properties`) n'accepte **aucune valeur de repli** pour les secrets :
l'application échoue au démarrage si l'une manque, plutôt que de tourner avec une valeur
connue.

| Variable | Requis | Défaut (dev) | Rôle |
|---|---|---|---|
| `JWT_SECRET` | **oui** | — | Clé de signature des tokens. Min. 32 octets. La changer déconnecte tous les utilisateurs |
| `SPRING_DATASOURCE_PASSWORD` | **oui** en prod | `1234` | Mot de passe PostgreSQL |
| `SPRING_DATASOURCE_URL` | non | `jdbc:postgresql://localhost:5432/RecyConnect` | URL JDBC |
| `SPRING_DATASOURCE_USERNAME` | non | `postgres` | Utilisateur PostgreSQL |
| `JWT_EXPIRATION_MINUTES` | non | `1380` (23 h) | Durée de validité d'un token d'accès |
| `JWT_REFRESH_EXPIRATION_DAYS` | non | `30` | Durée de validité d'un jeton de rafraîchissement. La révocation ne dépend pas de cette durée mais de la session |
| `SERVER_PORT` | non | `8081` | Port d'écoute |
| `APP_CLIENT_URL` | **oui** en prod | `http://localhost:4200` | Origines autorisées par le CORS, séparées par des virgules. **Ne jamais mettre `*`** |
| `APP_SERVER_URL` | **oui** en prod | `http://localhost` | URL publique, utilisée pour construire les liens de fichiers |
| `SMS_VALIDATION_KEY` | **oui** | — | Clé API Chinguisoft |
| `SMS_TOKEN` | **oui** | — | Token API Chinguisoft |
| `FILE_UPLOAD_DIR` | non | `uploads` | Dossier de stockage des fichiers |
| `FCM_PROJECT_ID` | non | — | Projet Firebase. Vide = notifications push désactivées |
| `FCM_SERVICE_ACCOUNT_KEY` | non | `service-account-key.json` | Clé de service Firebase, à placer dans `src/main/resources/` |
| `CATEGORY_SEED_ENABLED` | non | `true` | Crée le catalogue des catégories au démarrage (voir *Données d'amorçage*) |
| `ADMIN_SEED_ENABLED` / `ADMIN_SEED_PASSWORD` | non | `true` / — | Compte administrateur d'amorçage. Désactivé en prod |
| `DEMO_SEED_ENABLED` / `DEMO_SEED_PASSWORD` | non | `false` / — | Jeu de démonstration : 4 comptes, 10 annonces situées, 9 offres |
| `APP_VERSION_MINIMUM` | non | — (vide) | Version mobile minimale supportée. En deçà, l'application affiche un écran de mise à jour **bloquant**. Vide = aucun blocage |
| `APP_VERSION_LATEST` | non | — (vide) | Dernière version publiée. Au-dessus, l'application propose une mise à jour facultative |
| `APP_VERSION_ANDROID_URL` / `APP_VERSION_IOS_URL` | non | fiche Play / — | Liens ouverts par le bouton « Mettre à jour ». `https` obligatoire |

Fichiers **jamais versionnés** : `application.properties`, `.env`,
`service-account-key.json`, `uploads/`.

---

## Données d'amorçage

Trois `ApplicationRunner` remplissent la base au démarrage. Tous sont **idempotents** :
ils ne créent que ce qui manque et ne modifient jamais une ligne existante.

| Seeder | Actif par défaut | Ce qu'il crée |
|---|---|---|
| `CategorySeeder` | **oui** (prod comprise) | Les 5 catégories de déchets, traduites en fr/ar/en |
| `AdminSeeder` | oui en dev, non en prod | Le premier compte `ADMIN` |
| `DemoSeeder` | **non** | 4 comptes mauritaniens, 10 annonces situées dans Nouakchott et leurs offres |

### Catalogue des catégories

Donnée de **référence**, pas de démonstration : sans elle l'application mobile n'a aucun
filtre à l'accueil et le dépôt d'annonce n'offre aucun choix. Le seeder est donc actif
partout.

Chaque catégorie porte un `code` stable (`PLASTIC`, `PAPER`, `IRON`, `WOOD`,
`ELECTRONICS`) et ses trois libellés. Sur une base antérieure au champ `code`, les
catégories existantes sont **reconnues par leur nom et complétées sur place** — jamais
dupliquées, pour ne pas détacher les annonces déjà classées.

### Jeu de démonstration

```bash
DEMO_SEED_ENABLED=true DEMO_SEED_PASSWORD=<mot-de-passe> ./mvnw spring-boot:run
```

Crée trois vendeurs — `Sidi Mohamed Ould Ahmed`, `Mariem Mint Abdellahi`,
`Ahmedou Ould Cheikhna` — et un collecteur qui achète, `Khadijetou Mint Baba`. Les quatre
comptes partagent `DEMO_SEED_PASSWORD` ; la connexion se fait au format `222XXXXXXXX`.

Le jeu de données couvre les écrans que des annonces seules laissent vides :

- **Dix annonces situées**, réparties sur les cinq catégories et trois statuts. Chacune
  porte sa moughataa **et** son point GPS — placé par rapport au centre du quartier
  déclaré, jamais en dehors. Sans coordonnées, `/api/products/map` et
  `/api/products/nearby` répondent une liste vide.
- **Les huit moughataas de Nouakchott**, jamais `autre` : cette zone-là n'a pas de centre,
  donc rien à afficher sur la carte.
- **Les deux niveaux de `geoPrecision`**, pour voir la différence entre un point exact et
  une zone floutée à 300 m sans créer d'annonce à la main.
- **Neuf offres** dans les quatre états (`pending`, `accepted`, `rejected`, `cancelled`),
  avec leur fil de négociation et deux contre-propositions. Le stock restant tient compte
  des offres acceptées, exactement comme le ferait `NegotiationService.acceptBySeller`.
- **Trois langues de notification** (fr, ar, en) réparties sur les comptes.

Sur une base de démonstration remplie avant la carte, les annonces déjà présentes sont
**situées après coup** au démarrage : seuls les champs manquants sont complétés, une
annonce déplacée à la main pendant une démonstration n'est jamais ramenée en arrière.

À laisser à `false` partout ailleurs : des annonces fictives sur un environnement ouvert
au public passeraient pour de vraies offres. Si le numéro d'un compte de démonstration
appartient déjà à quelqu'un, ce compte est ignoré plutôt que réutilisé, et les annonces
d'un compte ne sont créées que s'il n'en possède aucune.

---

## Mise à jour de l'application mobile

`GET /api/app/version` publie la politique de version que l'application lit à
chaque démarrage (voir `lib/core/version/` côté mobile) :

```json
{
  "latestVersion": "1.3.0",
  "minimumVersion": "1.2.0",
  "androidUrl": "https://play.google.com/store/apps/details?id=com.recyconnect.app.neyan",
  "iosUrl": "https://apps.apple.com/app/id123456789"
}
```

| Situation | Effet côté mobile |
|---|---|
| `installée < minimumVersion` | Écran **bloquant**, sans bouton « Plus tard » |
| `minimumVersion ≤ installée < latestVersion` | Proposition **facultative**, refusable |
| `installée ≥ latestVersion` | Rien |
| Endpoint injoignable, illisible ou non configuré | Rien — l'application démarre normalement |

Endpoint **public** : la version qui ne sait plus s'authentifier est justement
celle qui doit apprendre qu'elle est périmée. Réponse mise en cache 5 minutes.

### Publier une mise à jour obligatoire

1. Publier le build sur le magasin et attendre qu'il soit **réellement
   disponible en téléchargement** ;
2. renseigner `.env` puis redémarrer l'API :

   ```bash
   APP_VERSION_LATEST=1.3.0
   APP_VERSION_MINIMUM=1.3.0
   ```

Les deux valeurs sont **vides par défaut**, et c'est délibéré : ce sont les
seules lignes de configuration capables de rendre l'application inutilisable
pour tout le parc installé. Bloquer un parc doit être un acte d'exploitation
explicite, pas l'effet de bord d'un déploiement.

`AppVersionService` pose trois garde-fous, vérifiés par
`AppVersionServiceTest` :

- une version illisible (`1.2..0`) est **omise** de la réponse, avec un
  avertissement au journal, plutôt que transmise telle quelle ;
- un `minimum` supérieur à `latest` est **ramené à `latest`** : exiger une
  version absente du magasin enfermerait l'utilisateur devant un bouton sans
  effet ;
- une URL de magasin qui n'est pas en `https` absolu est **omise** : c'est un
  lien qu'un écran bloquant ouvre hors de l'application, sur simple appui.

Le journal de démarrage indique toujours la politique effectivement servie.

---

## Tests

```bash
./mvnw test
```

**332 tests.** Ils utilisent une base H2 en mémoire (`src/test/resources/application.properties`),
donc **aucune installation de PostgreSQL n'est nécessaire** — utile en intégration continue.
Le test `contextLoads` démarre le contexte Spring complet et valide le câblage de la
configuration de sécurité.

---

## Docker

```bash
cp .env.example .env    # puis renseigner les valeurs
docker compose up --build
```

Le `docker-compose.yml` exige explicitement `DB_PASSWORD`, `JWT_SECRET`,
`SMS_VALIDATION_KEY`, `SMS_TOKEN`, `APP_CLIENT_URL` et `APP_SERVER_URL` : le démarrage
échoue si l'une manque. Le port PostgreSQL n'est volontairement pas publié sur l'hôte.

---

## Structure du projet

```
src/main/java/com/project/RecyConnect/
├── Config/       Sécurité HTTP, filtre JWT, CORS, WebSocket/STOMP
├── Controller/   Endpoints REST
├── DTO/          Objets de transfert (les entités ne sortent jamais telles quelles)
├── Model/        Entités JPA
├── Repository/   Spring Data JPA
├── Security/     Génération et validation des JWT
└── Service/      Logique métier
```

---

## Authentification

### Inscription — 3 étapes

```
1. POST /api/auth/send-code      { "phone": "22212345678" }
   → un code à 6 chiffres est envoyé par SMS

2. POST /api/auth/verify-code    { "phone": "22212345678", "code": "123456" }
   → vérifie la saisie, sans consommer le code

3. POST /api/auth/register       { "username", "password", "phone",
                                   "verificationCode", "deviceId", "fcmToken" }
   → consomme le code et crée le compte
```

Les numéros sont acceptés au format local (`12345678`) comme international
(`+22212345678` ou `22212345678`) et stockés sans le préfixe `222`.

**Un code est à usage unique** et expire au bout de 10 minutes. Deux demandes de code
pour le même numéro à moins de 60 secondes d'intervalle sont refusées.

Le champ `role` du corps de `/register` est **ignoré** : tout compte créé est un `USER`.
La création d'un administrateur passe par `POST /api/auth/register-admin`, réservé aux
administrateurs existants.

### Connexion

`POST /api/auth/login` exige `phone`, `password`, **`deviceId`** et **`fcmToken`**.

### Session unique par appareil

Une seule session active par utilisateur. Se connecter sur un nouvel appareil invalide
la précédente et lui envoie un push de déconnexion forcée.

Toute requête authentifiée doit donc porter **deux** en-têtes :

```http
Authorization: Bearer <token>
X-Device-Id: <le deviceId de la session>
```

### Renouvellement du jeton d'accès

Un jeton d'accès dure 23 heures. À son expiration, l'application mobile recevait
un 401 et renvoyait l'utilisateur à l'écran de connexion — au milieu d'une
négociation le cas échéant. C'est le point **H4** de l'audit mobile.

La connexion et l'inscription renvoient désormais un `refreshToken` à côté du
`token` :

```jsonc
{
  "token": "eyJhbGciOi…",        // jeton d'accès, 23 h
  "refreshToken": "kQ7f…",       // jeton de rafraîchissement, 30 j
  "userId": 7, "username": "…", "phone": 22233445566, "role": "USER"
}
```

À l'expiration, l'application échange le second contre un nouveau couple :

```http
POST /api/auth/refresh
X-Device-Id: <le deviceId de la session>

{ "refreshToken": "kQ7f…" }
```

| Réponse | Quand | Ce que fait l'application |
|---|---|---|
| `200` | Jeton valide, bon appareil | Remplace ses deux jetons et rejoue la requête d'origine |
| `401` | Jeton inconnu, périmé, ou appareil différent | Efface la session et repart sur l'écran de connexion |
| `400` | `refreshToken` absent du corps | Défaut d'appelant, pas une session invalide |

Trois propriétés tiennent la sécurité du mécanisme :

- **Rien en clair en base.** Seule l'empreinte SHA-256 est stockée
  (`user_sessions.refresh_token_hash`). Un jeton de rafraîchissement vaut un mot
  de passe : une fuite de la base ne doit pas rouvrir les sessions.
- **Rotation à chaque usage.** Le jeton présenté est invalidé et remplacé. Un
  jeton intercepté puis rejoué ne vaut plus rien — et l'appareil légitime perd sa
  session, ce qui rend le vol visible plutôt que silencieux.
- **Lié à l'appareil.** `X-Device-Id` est vérifié contre la session, comme sur
  toute requête authentifiée. Voler le jeton ne suffit pas.

Le jeton vit sur la session : il meurt donc exactement quand elle meurt —
déconnexion, connexion depuis un autre appareil, réinitialisation du mot de
passe. Aucune liste de révocation à maintenir.

### Réinitialisation du mot de passe

```
1. POST /api/auth/send-code       { "phone": "...", "isForgetPassword": true }
2. POST /api/auth/reset-password  { "phone", "verificationCode", "password" }
```

---

## Vue d'ensemble de l'API

### 🔓 Public

| Endpoint | Description |
|---|---|
| `POST /api/auth/**` | Inscription, connexion, code SMS, réinitialisation, renouvellement de jeton |
| `GET /api/categories/**` | Catalogue des catégories, libellés `nameFr` / `nameAr` / `nameEn` inclus |
| `GET /api/products` · `/{id}` · `/search` | Catalogue des produits |
| `GET /api/products/category/{id}` · `/user/{id}` | Produits par catégorie ou vendeur |
| `GET /api/products/{id}/similar` | Autres annonces de la même catégorie |
| `GET /api/products/locations` | Codes de moughataa acceptés par `location` |
| `GET /api/products/map` | Annonces d'un rectangle de carte (`minLat`, `maxLat`, `minLng`, `maxLng`, `limit`, `excludeUserId`, `categoryId`) |
| `GET /api/products/nearby` | Annonces les plus proches d'un point (`lat`, `lng`, `radiusKm`, `limit`, `excludeUserId`) |
| `GET /api/users/{id}/public` | Fiche vendeur — ni numéro, ni rôle |
| `GET /api/reviews/user/{id}` · `/{id}/summary` | Avis reçus et note moyenne |
| `GET /p/{id}` | Page web d'une annonce, balises OpenGraph pour le partage |
| `GET /api/files/{filename}` | Téléchargement d'un fichier |
| `GET /api/app/version` | Politique de version de l'application mobile (`latestVersion`, `minimumVersion`, `androidUrl`, `iosUrl`) |
| `/ws/**` | Poignée de main WebSocket (le CONNECT STOMP exige un JWT) |

`GET /api/products/search` accepte `query`, `categoryId`, `excludeUserId`,
`minPrice`, `maxPrice`, `minQuantity`, `unit`, `location`, `lat`, `lng`,
`maxDistanceKm`, `sort`, `page` et `size`. Le tri vaut `recent` (défaut),
`price_asc`, `price_desc`, `quantity_desc`, `oldest` ou `distance` ; sans
`size`, le catalogue part entier. La requête reste servie sans jeton, mais
**signée elle en dit plus** : le serveur écarte alors les annonces des comptes
bloqués et marque celles que l'appelant a enregistrées en favori.

`lat`, `lng` et `maxDistanceKm` vont ensemble : un rayon sans centre est ignoré
plutôt que de vider le catalogue. Le filtre accepte le centre de la moughataa
comme point de repli pour les annonces sans coordonnées — il ordonne et
retranche, il n'affirme aucune distance ; sans ce repli, un rayon ferait
disparaître presque tout le catalogue.

### 📍 Position d'une annonce

Une annonce porte, en plus de sa moughataa (obligatoire), un point GPS
facultatif : `latitude`, `longitude` et `geoPrecision` (`exact` ou `approx`,
`approx` par défaut et en cas de valeur inconnue).

**Le serveur stocke le point exact, mais ne le rend pas à tout le monde.** Le
lot est souvent chez un particulier : publier son point exact revient à publier
son adresse. Pour une annonce en `approx`, les lectures rendent le centre d'une
case d'environ 300 m. Le point précis n'est servi qu'à trois conditions :

* l'appelant est l'auteur de l'annonce — il doit pouvoir vérifier son point ;
* le vendeur a choisi `exact` — une cour, un atelier, un entrepôt qui veut être
  trouvé ;
* l'appelant a une offre **acceptée** sur cette annonce, c'est-à-dire au moment
  où les deux parties ont déjà échangé leurs numéros.

Ce filtrage a lieu à l'écriture de la réponse (`ProductService.applyGeoVisibility`),
et non dans l'application : une protection appliquée par le client n'en est pas
une, la valeur précise ayant déjà quitté le serveur.

`GET /api/products/nearby` ajoute `distanceKm` à chaque ligne — mais seulement
pour les annonces qui ont leurs propres coordonnées : une distance calculée
depuis le centre d'un quartier annoncerait une précision que personne n'a
mesurée.

### ⚠️ Statut à la création

`POST /api/products` **ignore le `status` demandé** s'il n'est pas l'un de ceux
qu'un vendeur choisit lui-même (`available`, `paused`, `recycled`) et retient
`available`.

C'est la correction d'une annonce invisible : l'application mobile envoyait
`pending`, la recherche ne rend que les annonces `available`, et rien nulle part
ne faisait passer une annonce de l'un à l'autre — il n'existe aucune file de
modération. Toute annonce publiée depuis l'application était enregistrée, payée
en données mobiles, et introuvable, y compris pour son auteur.

Les annonces déjà bloquées ne se débloquent pas seules. Leur auteur peut les
rendre disponibles depuis « mes annonces » ; en base, d'un coup :

```sql
UPDATE product SET status = 'AVAILABLE' WHERE status = 'PENDING';
```

### 🔐 Authentifié

| Endpoint | Description |
|---|---|
| `POST/PUT/PATCH/DELETE /api/products/**` | Gestion de ses propres produits |
| `POST /api/negotiations` | Faire une offre |
| `POST /api/negotiations/{id}/accept` · `/reject` | Réservé au vendeur |
| `POST /api/negotiations/{id}/cancel` | Réservé à l'acheteur |
| `POST /api/negotiations/{id}/counter` | Contre-proposition, réservée au vendeur |
| `GET /api/negotiations/{id}/history` | Fil des montants successifs, réservé aux parties |
| `GET /api/negotiations/product/{id}/queue` | File des offres classée |
| `GET /api/negotiations/earnings/me` | Revenus du vendeur connecté |
| `GET /api/negotiations/pending/count` | Nombre d'offres qui attendent ma réponse (`{"count": 3}`) — la pastille de l'onglet « Offres » |
| `GET /api/negotiations/history/me` | Journal des transactions conclues |
| `PATCH /api/products/{id}/status` | `available` · `paused` · `recycled` |
| `GET/POST/DELETE /api/favorites` · `/{productId}` | Annonces enregistrées |
| `GET/POST/PUT/DELETE /api/search-alerts` | Veilles de recherche |
| `POST /api/reviews` · `GET /api/reviews/pending/me` | Noter un vendeur |
| `GET/PUT /api/users/me/notification-preferences` | Trois interrupteurs de notification |
| `GET /api/notifications/**` | Ses notifications |
| `GET /api/users/{id}` · `/{id}/stats` · `/by-phone/{phone}` | Profils |
| `PUT/PATCH/DELETE /api/users/{id}` | Son propre compte uniquement |
| `POST /api/files/upload` · `/upload-multiple` | Téléversement |

### 👑 Administrateur

| Endpoint | Description |
|---|---|
| `POST /api/auth/register-admin` | Créer un administrateur |
| `GET /api/users` · `POST /api/users` | Lister ou créer des comptes |
| `PUT /api/users/{id}/role` | Changer le rôle d'un utilisateur |
| `PUT /api/products/admin/{id}` | Modifier n'importe quel produit |
| `POST /api/admin/notifications/broadcast` | Notification à tous les utilisateurs |
| `/api/fcm-test/**` | Diagnostic Firebase |

Une collection Postman complète est fournie : `RecyConnect_Postman_Collection.json`.

---

### Ce que le serveur refuse, et par quel code

Les refus sont distingués par leur code HTTP plutôt que par leur message :
l'application mobile déconnecte l'utilisateur sur un 401/403 d'un appel signé,
et confondre « pas encore autorisé » avec « session morte » le renverrait sur
l'écran de connexion sans un mot d'explication.

| Cas | Code |
|---|---|
| Lire le numéro d'une offre dont on n'est pas partie | `403` |
| Lire le numéro d'une offre pas encore acceptée | `409` |
| Noter une transaction sans en être l'acheteur | `403` |
| Noter une offre non acceptée, ou déjà notée | `409` |
| Note hors de l'échelle de 1 à 5 | `400` |
| Contre-proposer sur une offre qui n'est plus en attente | `400` |
| Veille de recherche appartenant à quelqu'un d'autre | `404` — répondre `403` confirmerait qu'elle existe |

---

### Partage et liens profonds

`GET /p/{id}` sert une page web publique portant les balises OpenGraph : le
titre, le prix et la photo apparaissent dans la conversation WhatsApp. Son
bouton renvoie vers `recyconnect://product/{id}`, que l'application mobile
intercepte.

Pour que le lien `https://…/p/{id}` ouvre directement l'application, il reste à
déposer deux fichiers signés par l'empreinte du certificat de publication :

* `src/main/resources/static/.well-known/assetlinks.json` (Android) ;
* `src/main/resources/static/.well-known/apple-app-site-association` (iOS).

Sans eux, Android propose le choix entre le navigateur et l'application, et le
bouton de la page assure le même service.

---

### Traduction des catégories

Une catégorie transporte ses libellés, un par langue :

```json
{ "id": 3, "code": "IRON", "name": "Iron",
  "nameFr": "Fer", "nameAr": "الحديد", "nameEn": "Iron" }
```

Le serveur ne choisit pas : il ne connaît pas la langue de l'appelant, et une même réponse
peut être mise en cache pour des utilisateurs de langues différentes. **C'est au client de
prendre le champ de sa locale**, avec `nameEn` puis `name` en recours — une catégorie créée
sans son libellé arabe reste ainsi lisible plutôt que d'afficher une ligne vide.

Conséquence pratique : une catégorie ajoutée depuis l'admin s'affiche traduite **sans
republier l'application mobile**. Le `code` est un identifiant stable, non modifiable par
l'API, auquel les clients rattachent leurs libellés de secours.

---

## Notifications temps réel

Si le destinataire est connecté en WebSocket, la notification part par STOMP ;
sinon elle part en push Firebase.

```
Endpoint STOMP : /ws          (SockJS)
Abonnement     : /user/{userId}/notifications
```

Le token JWT se transmet dans l'en-tête `Authorization` de la frame **CONNECT**.
Un client ne peut s'abonner qu'à **son propre** canal.

---

## Sécurité

L'API applique :

- mots de passe hachés en **BCrypt** ;
- **JWT** signé en HS256, avec session unique par appareil vérifiée à chaque requête ;
- **CORS** restreint à une liste blanche d'origines ;
- **contrôles de propriété** : on ne modifie que ses propres produits, offres et compte ;
- **codes SMS** générés par `SecureRandom`, à usage unique, avec anti-spam.

⚠️ **Avant tout déploiement public**, lisez [CODE_REVIEW.md](CODE_REVIEW.md) : il
recense les correctifs déjà appliqués, les vulnérabilités restantes, et les
**rotations de secrets à effectuer manuellement** (les identifiants de l'API SMS et le
mot de passe PostgreSQL sont présents dans l'historique Git et doivent être révoqués).

---

## Documentation

| Fichier | Contenu |
|---|---|
| [CODE_REVIEW.md](CODE_REVIEW.md) | Revue de code : failles, correctifs, travail restant |
| [API_USER_ACCESS.md](API_USER_ACCESS.md) | Détail des droits d'accès par endpoint |
| `RecyConnect_Postman_Collection.json` | Collection Postman |
