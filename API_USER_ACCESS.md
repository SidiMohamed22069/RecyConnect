# Droits d'accès par endpoint

Référence détaillée des autorisations. Pour une vue d'ensemble, voir [README.md](README.md).

Source de vérité : `Config/WebSecurityConfiguration.java`, complété par les annotations
`@PreAuthorize` sur les contrôleurs et par les contrôles de propriété codés dans les
méthodes.

> **Mis à jour le 2026-08-21.** Ce document décrivait auparavant un modèle où les
> notifications, les négociations et les profils utilisateurs étaient lisibles sans
> authentification. Ces routes ont été fermées (voir [CODE_REVIEW.md](CODE_REVIEW.md), C7).

---

## Les trois niveaux

| Niveau | Condition |
|---|---|
| 🔓 **Public** | Aucun token |
| 🔐 **Authentifié** | En-têtes `Authorization: Bearer <token>` **et** `X-Device-Id` |
| 👑 **Admin** | Authentifié avec le rôle `ADMIN` |

Toute route non listée comme publique exige une authentification (`anyRequest().authenticated()`).

---

## 🔓 Public

### Authentification — `/api/auth/**`

| Méthode | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/send-code` | Envoyer un code de vérification par SMS |
| POST | `/api/auth/verify-code` | Vérifier un code (sans le consommer) |
| POST | `/api/auth/register` | Créer un compte |
| POST | `/api/auth/login` | Se connecter |
| POST | `/api/auth/logout` | Se déconnecter |
| POST | `/api/auth/reset-password` | Réinitialiser le mot de passe |
| GET | `/api/auth/me` | Profil du porteur du token |

Le code de vérification **n'est jamais renvoyé dans la réponse** : il ne transite que
par SMS. Le champ `role` de `/register` est ignoré ; tout compte créé est un `USER`.

### Catalogue — lecture seule

| Méthode | Endpoint |
|---|---|
| GET | `/api/categories` · `/api/categories/{id}` |
| GET | `/api/products` · `/api/products/{id}` |
| GET | `/api/products/search` |
| GET | `/api/products/category/{categoryId}` |
| GET | `/api/products/user/{userId}` · `/api/products/user/{userId}/status` |
| GET | `/api/files/{filename}` |

### WebSocket

| Endpoint | Note |
|---|---|
| `/ws/**` | La poignée de main est publique. La frame STOMP **CONNECT** exige un JWT valide dans l'en-tête `Authorization`, et un client ne peut s'abonner qu'à `/user/{sonPropreId}/notifications` |

---

## 🔐 Authentifié

### Produits

| Méthode | Endpoint | Règle |
|---|---|---|
| POST | `/api/products` | L'utilisateur connecté devient propriétaire — inutile d'envoyer `userId` |
| PUT · PATCH | `/api/products/{id}` | Propriétaire ou admin |
| DELETE | `/api/products/{id}` | Propriétaire ou admin |
| POST | `/api/products/{id}/accept-offer` | Propriétaire uniquement |

### Négociations

| Méthode | Endpoint | Règle |
|---|---|---|
| GET | `/api/negotiations` · `/{id}` | Authentifié |
| GET | `/api/negotiations/sender/{id}` · `/receiver/{id}` · `/product/{id}` | Authentifié |
| GET | `/api/negotiations/product/{id}/queue` | File des offres, classée par montant décroissant |
| GET | `/api/negotiations/earnings/me` | Revenus de l'utilisateur connecté |
| POST | `/api/negotiations` | L'utilisateur connecté devient expéditeur |
| PUT | `/api/negotiations/{id}` | Expéditeur (acheteur) uniquement |
| PATCH · DELETE | `/api/negotiations/{id}` | Expéditeur ou destinataire |
| POST | `/api/negotiations/{id}/cancel` | Acheteur uniquement |
| POST | `/api/negotiations/{id}/accept` · `/reject` | Vendeur uniquement |

L'acceptation d'une offre décrémente le stock sous verrou pessimiste et annule
automatiquement les offres devenues incompatibles avec la quantité restante.

### Utilisateurs

| Méthode | Endpoint | Règle |
|---|---|---|
| GET | `/api/users/{id}` · `/{id}/stats` · `/by-phone/{phone}` | Authentifié |
| PUT · PATCH · DELETE | `/api/users/{id}` | **Son propre compte, ou admin** |

⚠️ La suppression d'un compte est destructrice au-delà du compte : l'entité `User`
cascade sur ses produits et ses négociations.

### Notifications

| Méthode | Endpoint |
|---|---|
| GET | `/api/notifications` · `/{id}` |
| GET | `/api/notifications/receiver/{id}` · `/unread` · `/unread/count` |
| POST · PUT · PATCH · DELETE | `/api/notifications` · `/{id}` |
| PATCH | `/api/notifications/{id}/read` |

### Catégories et fichiers

| Méthode | Endpoint |
|---|---|
| POST · PUT · PATCH · DELETE | `/api/categories` · `/{id}` |
| POST | `/api/files/upload` · `/api/files/upload-multiple` |
| DELETE | `/api/files/{filename}` |

---

## 👑 Admin

| Méthode | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register-admin` | Créer un administrateur |
| GET | `/api/users` | Lister tous les comptes |
| POST | `/api/users` | Créer un compte |
| PUT | `/api/users/{id}/role` | Changer le rôle (`USER` / `ADMIN`) |
| PUT | `/api/products/admin/{id}` | Modifier n'importe quel produit |
| POST | `/api/admin/notifications/broadcast` | Notifier tous les utilisateurs |
| GET | `/api/admin/notifications/fcm-status` · `/test-fcm/{userId}` | Diagnostic Firebase |
| * | `/api/fcm-test/**` | Diagnostic Firebase détaillé |

---

## Codes de réponse

| Code | Signification |
|---|---|
| `401` | Non authentifié : token absent, invalide, expiré, ou `X-Device-Id` ne correspondant pas à la session active |
| `403` | Authentifié mais sans les droits (pas propriétaire, ou rôle insuffisant) |
| `404` | Ressource inexistante |
| `409` | Conflit : nom d'utilisateur ou numéro déjà utilisé |

---

## Limites connues

Certaines routes de cette liste restent plus permissives qu'elles ne devraient. Elles
sont recensées dans [CODE_REVIEW.md](CODE_REVIEW.md) :

- **M5** — `PATCH /api/negotiations/{id}` accepte un `status` arbitraire : un acheteur
  peut passer sa propre offre à `accepted` sans passer par le vendeur.
- **M6** — les catégories sont modifiables et supprimables par tout utilisateur authentifié.
- **M7** — `POST /api/notifications` accepte `senderId` et `receiverId` sans contrôle :
  usurpation possible.
- **M8** — `DELETE /api/files/{filename}` permet de supprimer le fichier de n'importe qui,
  et les téléversements ne sont pas validés.
- `GET /api/users/by-phone/{phone}` permet à tout compte authentifié d'énumérer les numéros.
