# APIs Accessibles aux Utilisateurs avec le Rôle USER

## 🔓 Endpoints Publics (Accessibles sans authentification)

### Authentification (`/api/auth/**`)
- `POST /api/auth/send-code` - Envoyer un code de vérification
- `POST /api/auth/verify-code` - Vérifier le code
- `POST /api/auth/register` - Créer un compte
- `POST /api/auth/login` - Se connecter
- `POST /api/auth/logout` - Se déconnecter
- `GET /api/auth/me` - Obtenir les informations de l'utilisateur connecté

### Catégories (`/api/categories/**`)
- `GET /api/categories` - Lister toutes les catégories
- `GET /api/categories/{id}` - Obtenir une catégorie par ID

### Produits (`/api/products`)
- `GET /api/products` - Lister tous les produits
- `GET /api/products/{id}` - Obtenir un produit par ID
- `GET /api/products/search` - Rechercher des produits
- `GET /api/products/category/{categoryId}` - Obtenir les produits d'une catégorie
- `GET /api/products/user/{userId}` - Obtenir les produits d'un utilisateur
- `GET /api/products/user/{userId}/status` - Obtenir les produits d'un utilisateur par statut

### Négociations (`/api/negotiations`)
- `GET /api/negotiations` - Lister toutes les négociations
- `GET /api/negotiations/{id}` - Obtenir une négociation par ID
- `GET /api/negotiations/product/{productId}` - Obtenir les négociations d'un produit
- `GET /api/negotiations/sender/{senderId}` - Obtenir les négociations envoyées
- `GET /api/negotiations/receiver/{receiverId}` - Obtenir les négociations reçues

### Utilisateurs (`/api/users`)
- `GET /api/users/{id}` - Obtenir un utilisateur par ID
- `GET /api/users/by-phone/{phone}` - Obtenir un utilisateur par téléphone
- `GET /api/users/{id}/stats` - Obtenir les statistiques d'un utilisateur

### Notifications (`/api/notifications`)
- `GET /api/notifications` - Lister toutes les notifications
- `GET /api/notifications/{id}` - Obtenir une notification par ID
- `GET /api/notifications/receiver/{receiverId}` - Obtenir les notifications d'un utilisateur
- `GET /api/notifications/receiver/{receiverId}/unread` - Obtenir les notifications non lues
- `GET /api/notifications/receiver/{receiverId}/unread/count` - Compter les notifications non lues

### Fichiers (`/api/files`)
- `GET /api/files/{filename}` - Télécharger un fichier

### Vérification téléphone (`/api/phone-verification`)
- `GET /api/phone-verification/**` - Endpoints de vérification (lecture uniquement)

---

## 🔐 Endpoints Authentifiés (Nécessitent un token JWT - Rôle USER)

### Produits (`/api/products`)
- `POST /api/products` - **Créer un produit** (utilisateur connecté = propriétaire)
- `PUT /api/products/{id}` - **Modifier un produit** (uniquement si propriétaire)
- `PATCH /api/products/{id}` - **Modifier partiellement un produit** (uniquement si propriétaire)
- `DELETE /api/products/{id}` - **Supprimer un produit** (uniquement si propriétaire)
- `POST /api/products/{id}/accept-offer` - **Accepter une offre** (uniquement si propriétaire)

### Négociations (`/api/negotiations`)
- `POST /api/negotiations` - **Créer une négociation** (utilisateur connecté = expéditeur)
- `PUT /api/negotiations/{id}` - **Modifier une négociation** (uniquement si expéditeur ou destinataire)
- `PATCH /api/negotiations/{id}` - **Modifier partiellement une négociation** (uniquement si expéditeur ou destinataire)
- `DELETE /api/negotiations/{id}` - **Supprimer une négociation** (uniquement si expéditeur ou destinataire)

### Utilisateurs (`/api/users`)
- `GET /api/users` - Lister tous les utilisateurs (nécessite authentification)
- `PUT /api/users/{id}` - Modifier un utilisateur
- `PATCH /api/users/{id}` - Modifier partiellement un utilisateur
- `DELETE /api/users/{id}` - Supprimer un utilisateur
- `POST /api/users/{id}/fcm-token` - Mettre à jour le token FCM

### Notifications (`/api/notifications`)
- `POST /api/notifications` - Créer une notification
- `PUT /api/notifications/{id}` - Modifier une notification
- `PATCH /api/notifications/{id}` - Modifier partiellement une notification
- `PATCH /api/notifications/{id}/read` - Marquer comme lu
- `DELETE /api/notifications/{id}` - Supprimer une notification

### Catégories (`/api/categories`)
- `POST /api/categories` - Créer une catégorie (réservé aux ADMIN via `/api/admin/categories`)
- `PUT /api/categories/{id}` - Modifier une catégorie (réservé aux ADMIN via `/api/admin/categories`)
- `PATCH /api/categories/{id}` - Modifier partiellement une catégorie (réservé aux ADMIN via `/api/admin/categories`)
- `DELETE /api/categories/{id}` - Supprimer une catégorie (réservé aux ADMIN via `/api/admin/categories`)

### Fichiers (`/api/files`)
- `POST /api/files/upload` - **Uploader un fichier**
- `POST /api/files/upload-multiple` - **Uploader plusieurs fichiers**
- `DELETE /api/files/{filename}` - **Supprimer un fichier**

---

## 🚫 Endpoints NON Accessibles aux Utilisateurs USER (Réservés aux ADMIN)

### Admin Notifications (`/api/admin/notifications`)
- `POST /api/admin/notifications/broadcast` - ❌ **RÉSERVÉ AUX ADMIN** - Envoyer une notification broadcast à tous les utilisateurs

---

## 📝 Notes Importantes

1. **Authentification** : 
   - Tous les endpoints authentifiés nécessitent un header `Authorization: Bearer <token>`
   - **Seuls les endpoints GET (lecture) sont publics** - tous les POST/PUT/PATCH/DELETE nécessitent l'authentification
   - Si vous tentez d'accéder à un endpoint protégé sans token, vous recevrez une erreur `401 Unauthorized`

2. **Permissions** :
   - Un utilisateur ne peut modifier/supprimer que **ses propres produits**
   - Un utilisateur ne peut modifier/supprimer que les négociations où il est **expéditeur OU destinataire**
   - Si une tentative d'accès non autorisée est détectée, le serveur retourne :
     - `401 Unauthorized` si non authentifié (pas de token ou token invalide)
     - `403 Forbidden` si authentifié mais sans permission (utilisateur n'est pas propriétaire)

3. **Création automatique** :
   - Lors de la création d'un produit, l'utilisateur connecté est automatiquement défini comme propriétaire (vous n'avez pas besoin de spécifier `userId`)
   - Lors de la création d'une négociation, l'utilisateur connecté est automatiquement défini comme expéditeur (vous n'avez pas besoin de spécifier `senderId`)

4. **Endpoints GET publics** : 
   - Tous les endpoints de lecture (GET) sont publics pour permettre la consultation sans authentification
   - Cela permet aux utilisateurs non connectés de parcourir les produits, négociations, etc.

5. **Endpoints POST/PUT/DELETE protégés** :
   - Tous les endpoints de modification (POST/PUT/PATCH/DELETE) nécessitent un token JWT valide
   - Spring Security bloque ces requêtes au niveau du filtre de sécurité avant même d'atteindre le contrôleur
   - Les contrôleurs vérifient également les permissions (propriétaire, expéditeur/destinataire)

6. **WebSocket** : Les connexions WebSocket (`/ws/**`) sont publiques pour permettre les notifications en temps réel


