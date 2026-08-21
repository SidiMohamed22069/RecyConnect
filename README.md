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
| `JWT_EXPIRATION_MINUTES` | non | `1380` (23 h) | Durée de validité d'un token |
| `SERVER_PORT` | non | `8081` | Port d'écoute |
| `APP_CLIENT_URL` | **oui** en prod | `http://localhost:4200` | Origines autorisées par le CORS, séparées par des virgules. **Ne jamais mettre `*`** |
| `APP_SERVER_URL` | **oui** en prod | `http://localhost` | URL publique, utilisée pour construire les liens de fichiers |
| `SMS_VALIDATION_KEY` | **oui** | — | Clé API Chinguisoft |
| `SMS_TOKEN` | **oui** | — | Token API Chinguisoft |
| `FILE_UPLOAD_DIR` | non | `uploads` | Dossier de stockage des fichiers |
| `FCM_PROJECT_ID` | non | — | Projet Firebase. Vide = notifications push désactivées |
| `FCM_SERVICE_ACCOUNT_KEY` | non | `service-account-key.json` | Clé de service Firebase, à placer dans `src/main/resources/` |

Fichiers **jamais versionnés** : `application.properties`, `.env`,
`service-account-key.json`, `uploads/`.

---

## Tests

```bash
./mvnw test
```

**208 tests.** Ils utilisent une base H2 en mémoire (`src/test/resources/application.properties`),
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
| `POST /api/auth/**` | Inscription, connexion, code SMS, réinitialisation |
| `GET /api/categories/**` | Catalogue des catégories |
| `GET /api/products` · `/{id}` · `/search` | Catalogue des produits |
| `GET /api/products/category/{id}` · `/user/{id}` | Produits par catégorie ou vendeur |
| `GET /api/files/{filename}` | Téléchargement d'un fichier |
| `/ws/**` | Poignée de main WebSocket (le CONNECT STOMP exige un JWT) |

### 🔐 Authentifié

| Endpoint | Description |
|---|---|
| `POST/PUT/PATCH/DELETE /api/products/**` | Gestion de ses propres produits |
| `POST /api/negotiations` | Faire une offre |
| `POST /api/negotiations/{id}/accept` · `/reject` | Réservé au vendeur |
| `POST /api/negotiations/{id}/cancel` | Réservé à l'acheteur |
| `GET /api/negotiations/product/{id}/queue` | File des offres classée |
| `GET /api/negotiations/earnings/me` | Revenus du vendeur connecté |
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
