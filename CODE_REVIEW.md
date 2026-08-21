# Code Review — RecyConnect Backend

**Date :** 2026-08-21
**Périmètre :** `backend/` — Spring Boot 3.5.6, Java 21, PostgreSQL, JWT, WebSocket/STOMP, Firebase FCM
**Volume :** 63 fichiers Java, ~6 500 lignes (dont ~1 700 de tests)
**Build :** `mvn compile` passe sans erreur ni warning avec JDK 21.
**Tests :** non exécutés — le plugin `maven-surefire` n'est pas présent dans le dépôt Maven local et l'environnement est hors ligne. L'analyse des tests ci-dessous est statique.

---

> **État au 2026-08-21** — Branche `fix/securite-critique`.
> Les **8 failles critiques** et **9 constats majeurs** sont corrigés (détail en fin de document).
> Suite de tests : **208 tests, 0 échec**. Le contexte Spring complet démarre.
> ⚠️ **Action manuelle requise et non effectuée : révoquer le token API SMS et changer le mot de passe PostgreSQL.**

## Synthèse

L'architecture générale est saine et lisible : découpage Controller / Service / Repository / DTO cohérent, usage correct de JPA, verrou pessimiste bien placé sur l'acceptation d'offre (`findByIdForUpdate`), et une vraie logique métier de file d'offres. Le projet est fonctionnel.

En revanche, **la couche de sécurité est la faiblesse majeure et bloquante**. Plusieurs failles permettent, sans authentification ou avec un simple compte utilisateur, de prendre le contrôle de n'importe quel compte, de devenir administrateur, ou d'extraire l'intégralité des données personnelles des utilisateurs. Le projet ne doit pas être exposé publiquement en l'état.

| Sévérité | Nombre | Nature |
|---|---|---|
| 🔴 Critique | 8 | Prise de contrôle de compte, élévation de privilège, fuite de données |
| 🟠 Majeur | 13 | Contrôle d'accès, gestion des secrets, logique métier |
| 🟡 Moyen | 22 | Robustesse, performance, qualité, tests |

---

## 🔴 Critiques — à corriger avant toute mise en ligne

### C1. N'importe qui peut créer un compte ADMIN

[AuthController.java:145-148](backend/src/main/java/com/project/RecyConnect/Controller/AuthController.java#L145-L148)

```java
Role userRole = Role.USER;
if (request.getRole() != null && request.getRole().equalsIgnoreCase("ADMIN")) {
    userRole = Role.ADMIN;   // ← champ contrôlé par le client
}
```

L'endpoint `/api/auth/register` est public. Le rôle provient du corps de la requête. Un simple `{"role": "ADMIN", ...}` suffit pour obtenir les privilèges administrateur.

**Correction :** forcer `Role.USER` dans `/register`. La création d'admin doit passer exclusivement par `/register-admin` (déjà protégé — voir aussi M1 qui casse cette protection).

---

### C2. Le code de vérification SMS est renvoyé dans la réponse HTTP

[AuthController.java:52-54](backend/src/main/java/com/project/RecyConnect/Controller/AuthController.java#L52-L54) et [PhoneVerificationService.java:155](backend/src/main/java/com/project/RecyConnect/Service/PhoneVerificationService.java#L155)

```java
return ResponseEntity.ok(new AuthDTO.AuthResponse(
        "Code de vérification envoyé. Code (dev only): " + code));
```

Chaîne d'attaque complète et non authentifiée :

1. `POST /api/auth/send-code` avec `{"phone": "<numéro victime>", "isForgetPassword": true}` → le serveur **retourne le code OTP**
2. `POST /api/auth/reset-password` avec ce code → mot de passe de la victime changé
3. `POST /api/auth/login` → compte pris

Le commentaire « en production, ne pas retourner le code » est présent mais le code l'est aussi. C'est la faille la plus directement exploitable du projet.

**Correction :** `sendVerificationCode` doit retourner `void`. Aucun code OTP ne doit jamais transiter dans une réponse HTTP.

---

### C3. `/api/fcm-test/**` est public et expose tout

[WebSecurityConfiguration.java:44-45](backend/src/main/java/com/project/RecyConnect/Config/WebSecurityConfiguration.java#L44-L45) + [FCMTestController.java](backend/src/main/java/com/project/RecyConnect/Controller/FCMTestController.java)

Sans aucun token :

| Endpoint | Impact |
|---|---|
| `GET /api/fcm-test/users` | Dump de **tous** les utilisateurs : id, username, **numéro de téléphone**, aperçu du token FCM |
| `POST /api/fcm-test/send/{userId}` | Envoi d'une notification push à n'importe quel utilisateur (phishing, spam) |
| `POST /api/fcm-test/send-direct` | Envoi vers un token FCM arbitraire |
| `POST /api/fcm-test/register-token/{userId}` | **Écrasement du token FCM de n'importe qui** → détournement de ses notifications |

**Correction :** supprimer `FCMTestController` du build de production, ou le placer derrière `hasRole('ADMIN')` et retirer la ligne `permitAll`.

---

### C4. Les codes OTP de tous les utilisateurs sont lisibles et forgeables

[PhoneVerificationController.java:16-17,41-44](backend/src/main/java/com/project/RecyConnect/Controller/PhoneVerificationController.java#L16-L44)

```java
@GetMapping
public List<PhoneVerificationDTO> getAll() { return service.findAll(); }   // tous les codes, en clair

@PostMapping
public PhoneVerificationDTO create(@RequestBody PhoneVerificationDTO dto) { return service.save(dto); }
```

Ce contrôleur est mappé sur `/api/phone-verifications` (pluriel), alors que la règle de sécurité vise `/api/phone-verification/**` (singulier) — voir M2. La règle ne s'applique donc pas et ces routes tombent dans `anyRequest().authenticated()`.

Résultat : **tout utilisateur authentifié** (donc n'importe qui, il suffit de s'inscrire) peut soit lire tous les codes OTP actifs, soit s'en créer un pour le numéro de son choix via `POST`, puis appeler `/api/auth/reset-password`. Deuxième chemin complet de prise de contrôle de compte.

**Correction :** supprimer ce contrôleur CRUD. La vérification téléphonique n'a pas à être exposée en REST — elle est déjà pilotée par `AuthController`.

---

### C5. Secrets en clair dans le dépôt Git

| Secret | Emplacement |
|---|---|
| Mot de passe PostgreSQL | [application.properties:7](backend/src/main/resources/application.properties#L7) |
| Clé de validation API SMS Chinguisoft | [application.properties:27](backend/src/main/resources/application.properties#L27) |
| Token API SMS Chinguisoft | [application.properties:28](backend/src/main/resources/application.properties#L28) |
| Secret de signature JWT | [JwtUtil.java:21](backend/src/main/java/com/project/RecyConnect/Security/JwtUtil.java#L21) (constante `SECRET` en dur) |

`.gitignore` liste bien `src/main/resources/application.properties`, mais **le fichier est déjà suivi par Git** — un `.gitignore` n'a aucun effet sur un fichier déjà indexé. Vérification :

```
$ git ls-files backend/src/main/resources/application.properties
backend/src/main/resources/application.properties   ← suivi
```

Les secrets sont présents dans l'historique **depuis le commit initial** (`24574d8`), et le token SMS a même été modifié dans un commit dédié (`42a3dda`).

Le secret JWT en dur est particulièrement grave : quiconque lit le dépôt peut **forger un token valide pour n'importe quel utilisateur**, y compris un admin.

**Correction :**
1. Révoquer immédiatement le token SMS et changer le mot de passe DB — ils sont à considérer comme compromis.
2. `git rm --cached backend/src/main/resources/application.properties`.
3. Externaliser via variables d'environnement (`${JWT_SECRET}`, `${SMS_TOKEN}`…), comme le fait déjà correctement `application-prod.properties`.
4. Purger l'historique (`git filter-repo`) ou considérer le dépôt comme définitivement fuité.

---

### C6. WebSocket : authentification désactivée, canal de notifications ouvert

[WebSocketConfig.java:39-44](backend/src/main/java/com/project/RecyConnect/Config/WebSocketConfig.java#L39-L44)

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    // Temporairement désactivé pour éviter la boucle infinie
    // TODO: Réactiver une fois le problème résolu
    // registration.interceptors(authInterceptor);
}
```

`WebSocketAuthInterceptor` est écrit, testé… et **jamais branché**. Combiné à :

- `/ws/**` en `permitAll` ([WebSecurityConfiguration.java:38](backend/src/main/java/com/project/RecyConnect/Config/WebSecurityConfiguration.java#L38))
- `JwtRequestFilter` qui ignore explicitement `/ws` ([JwtRequestFilter.java:39-43](backend/src/main/java/com/project/RecyConnect/Config/JwtRequestFilter.java#L39-L43))
- un broker `enableSimpleBroker("/user")` avec destination `/user/{userId}/notifications` ([WebSocketService.java:27](backend/src/main/java/com/project/RecyConnect/Service/WebSocketService.java#L27))

`/user/{id}/notifications` n'est **pas** une destination utilisateur Spring (qui nécessite `convertAndSendToUser` + `/user/queue/...`), c'est un simple topic public. N'importe qui peut se connecter à `/ws` sans token et s'abonner à `/user/1/notifications`, `/user/2/notifications`… pour lire les notifications privées de tous les utilisateurs en temps réel.

**Correction :** réactiver l'intercepteur (le `TODO` sur la boucle infinie est déjà traité — l'intercepteur ne filtre que `StompCommand.CONNECT`), et utiliser `convertAndSendToUser(username, "/queue/notifications", dto)` avec `enableSimpleBroker("/queue", "/topic")`.

---

### C7. Toutes les notifications de tous les utilisateurs sont publiques

[WebSecurityConfiguration.java:57](backend/src/main/java/com/project/RecyConnect/Config/WebSecurityConfiguration.java#L57) — `/api/notifications/**` est en `permitAll` sur GET.

```
GET /api/notifications                      → toutes les notifications de la plateforme
GET /api/notifications/receiver/{id}        → boîte de réception de n'importe qui
```

Les messages contiennent les noms d'utilisateur, les titres de produits et l'activité commerciale complète. Sans aucun token.

Le même problème existe, à un degré moindre, pour `/api/negotiations/**` (prix et quantités de toutes les offres de tous les vendeurs — donnée commerciale sensible) et `/api/users/by-phone/**` (énumération : permet de tester si un numéro est inscrit).

Le fichier [API_USER_ACCESS.md](API_USER_ACCESS.md) documente ces routes comme volontairement publiques. C'est un choix de conception à revoir : rendre publics les produits est légitime, rendre publiques les notifications et les négociations ne l'est pas.

**Correction :** retirer `/api/notifications/**` et `/api/negotiations/**` des routes publiques, et filtrer par utilisateur courant côté service.

---

### C8. IDOR sur les comptes utilisateurs

[UserController.java:45-90](backend/src/main/java/com/project/RecyConnect/Controller/UserController.java#L45-L90)

```java
@PutMapping("/{id}")
public ResponseEntity<UserDTO> update(@PathVariable Long id, @RequestBody UserDTO dto) { ... }

@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);            // aucune vérification
    return ResponseEntity.noContent().build();
}
```

Contrairement à `ProductController` et `NegotiationController` qui vérifient correctement la propriété, `UserController` ne fait **aucun contrôle**. Tout utilisateur authentifié peut modifier le username/téléphone/photo de n'importe quel compte, ou le supprimer.

La suppression est destructrice en cascade : `User` porte `cascade = CascadeType.ALL, orphanRemoval = true` sur `products`, `negotiationsSent` et `negotiationsReceived` ([User.java:40-47](backend/src/main/java/com/project/RecyConnect/Model/User.java#L40-L47)). Supprimer un utilisateur efface aussi tous ses produits et toutes ses négociations — y compris celles de ses contreparties.

**Correction :** vérifier `currentUser.getId().equals(id) || isAdmin` sur `PUT`, `PATCH` et `DELETE`, à l'image de ce qui est déjà fait pour les produits.

---

## 🟠 Majeurs

### M1. Les rôles ne sont jamais chargés → toutes les protections admin sont inopérantes

[UserService.java:34-40](backend/src/main/java/com/project/RecyConnect/Service/UserService.java#L34-L40)

```java
return new org.springframework.security.core.userdetails.User(
        optionalUser.getUsername(), optionalUser.getPassword(), new ArrayList<>());
                                                                //  ↑ aucune autorité
```

L'entité `User` implémente pourtant correctement `getAuthorities()` en renvoyant `ROLE_ADMIN` / `ROLE_USER` ([User.java:52-55](backend/src/main/java/com/project/RecyConnect/Model/User.java#L52-L55)) — mais cette méthode n'est jamais utilisée, car `loadUserByUsername` construit un `UserDetails` Spring vide.

`JwtRequestFilter` propage ces autorités vides dans le `SecurityContext` ([JwtRequestFilter.java:87](backend/src/main/java/com/project/RecyConnect/Config/JwtRequestFilter.java#L87)). Conséquence : **tous** les contrôles de rôle échouent, même pour un vrai admin :

- `@PreAuthorize("hasRole('ADMIN')")` sur `/register-admin`, `/api/users/{id}/role`, `/api/products/admin/{id}`, `/api/admin/notifications/broadcast`
- `.requestMatchers(...).hasRole("ADMIN")` dans la config

Toutes ces fonctionnalités renvoient 403 pour tout le monde. **L'espace admin est entièrement cassé.** À noter : le rôle est bien mis dans le JWT et `ProductController` le lit via `userService.getCurrentUser().getRole()` — c'est pour cela que les vérifications admin sur les produits fonctionnent, contrairement aux `@PreAuthorize`.

**Correction :** `return optionalUser;` — l'entité est déjà un `UserDetails` valide.

---

### M2. Règle de sécurité inopérante : singulier vs pluriel

La config protège `/api/phone-verification/**`, le contrôleur est mappé sur `/api/phone-verifications`. La règle ne matche jamais. Cause directe de C4.

---

### M3. Gestion des JWT

[JwtUtil.java](backend/src/main/java/com/project/RecyConnect/Security/JwtUtil.java)

- **Liste noire en mémoire** (`invalidatedTokens`, ligne 22) : fuite mémoire (aucune purge, elle grossit indéfiniment), perdue au redémarrage (les tokens « déconnectés » redeviennent valides), et inopérante dès qu'il y a plus d'une instance. Le mécanisme `UserSession` en base est bien plus solide — cette liste fait doublon et devrait disparaître.
- **Durée de vie de 23 h** (ligne 52) sans refresh token : fenêtre d'exploitation très large en cas de vol.
- `extractEmail` / `hello` : nommage trompeur (c'est un username, pas un email) et méthode `hello` morte.

---

### M4. CORS permissif

[SimpleCorsFilter.java:29-33](backend/src/main/java/com/project/RecyConnect/Config/SimpleCorsFilter.java#L29-L33)

```java
String originHeader = request.getHeader("origin");
response.setHeader("Access-Control-Allow-Origin", originHeader);   // reflète n'importe quelle origine
response.setHeader("Access-Control-Allow-Headers", "*");
```

Le champ `clientAppUrl` est injecté depuis `app.client.url` puis **jamais utilisé**. En production, `app.client.url=*` ([application-prod.properties:24](backend/src/main/resources/application-prod.properties#L24)) avec le commentaire « Allow all origins in prod ». Le filtre ne pose pas `Allow-Credentials`, ce qui limite l'impact, mais toute API publique reste appelable depuis n'importe quel site.

**Correction :** utiliser `CorsConfigurationSource` de Spring Security avec une liste blanche d'origines, et supprimer ce filtre manuel.

---

### M5. `PATCH /api/negotiations/{id}` permet de contourner le flux d'acceptation

[NegotiationService.java:196-208](backend/src/main/java/com/project/RecyConnect/Service/NegotiationService.java#L196-L208)

```java
if (dto.getStatus() != null) existing.setStatus(dto.getStatus());       // statut libre
if (dto.getSenderId() != null) userRepo.findById(...).ifPresent(existing::setSender);
if (dto.getReceiverId() != null) userRepo.findById(...).ifPresent(existing::setReceiver);
if (dto.getProductId() != null) productRepo.findById(...).ifPresent(existing::setProduct);
```

Le contrôleur autorise l'acheteur **ou** le vendeur. Un acheteur peut donc passer sa propre offre à `"accepted"` sans passer par `acceptBySeller` — donc sans décrément de stock, sans verrou pessimiste, sans vérification de disponibilité. Or `sumAcceptedAmountBySellerId` compte les offres `accepted` : les revenus vendeur deviennent falsifiables. Il peut aussi réaffecter l'offre à un autre produit ou à un autre utilisateur.

**Correction :** retirer `status`, `senderId`, `receiverId` et `productId` du `patch`. Les transitions d'état doivent passer uniquement par `/accept`, `/reject`, `/cancel`.

---

### M6. Les catégories sont modifiables par tout utilisateur

[CategoryController.java:25-50](backend/src/main/java/com/project/RecyConnect/Controller/CategoryController.java#L25-L50) — `POST`, `PUT`, `PATCH`, `DELETE` ne demandent qu'une authentification. N'importe quel compte peut renommer ou supprimer les catégories du référentiel. `API_USER_ACCESS.md` mentionne un `/api/admin/categories` qui **n'existe pas dans le code**.

---

### M7. Notifications forgeables

[NotificationController.java:30-31](backend/src/main/java/com/project/RecyConnect/Controller/NotificationController.java#L30-L31) — `POST /api/notifications` accepte `senderId` et `receiverId` du client sans contrôle. Tout utilisateur peut usurper l'identité d'un autre, envoyer un push arbitraire (`NotificationService.save` déclenche WebSocket ou FCM), lire/modifier/supprimer la notification de n'importe qui via `PUT`/`PATCH`/`DELETE`.

---

### M8. Upload de fichiers sans aucune validation

[FileController.java:56-83](backend/src/main/java/com/project/RecyConnect/Controller/FileController.java#L56-L83)

- Aucun contrôle du type MIME ni du contenu — seule l'extension d'origine est conservée telle quelle.
- La lecture renvoie `Content-Disposition: inline` avec un `Content-Type` déduit par `Files.probeContentType` : un `.html` ou `.svg` uploadé est **servi et exécuté sur l'origine de l'API** → XSS stocké.
- `DELETE /api/files/{filename}` ([ligne 141](backend/src/main/java/com/project/RecyConnect/Controller/FileController.java#L141)) : tout utilisateur authentifié peut supprimer **n'importe quel fichier**, y compris les images des produits d'autrui.
- `uploadPath.resolve(filename).normalize()` n'est pas suivi d'une vérification `startsWith(uploadPath)`. Le `StrictHttpFirewall` de Spring Security bloque aujourd'hui les `..` dans l'URL, mais la défense en profondeur manque.

**Correction :** liste blanche d'extensions et de types MIME, `Content-Disposition: attachment`, contrôle de propriété sur la suppression, et validation explicite du chemin résolu.

---

### M9. OTP : pas de limitation, pas de consommation, générateur non sécurisé

[PhoneVerificationService.java](backend/src/main/java/com/project/RecyConnect/Service/PhoneVerificationService.java)

- `new Random()` (ligne 29) au lieu de `SecureRandom` : générateur prédictible pour un secret d'authentification.
- Aucune limite de tentatives : un code à 6 chiffres se brute-force en ~1 M de requêtes, sans throttling.
- Aucune limite d'envoi : `send-code` est appelable en boucle (coût SMS, harcèlement du destinataire).
- `verifyCodeBeforeRegistration` (ligne 234) **ne consomme pas** le code : il reste réutilisable pendant 10 minutes.
- `cleanupExpiredCodes` (ligne 255) ne supprime que les codes **déjà expirés** — le code qui vient de servir survit.

---

### M10. Validation du numéro incohérente avec sa documentation

[PhoneVerificationService.java:178-188](backend/src/main/java/com/project/RecyConnect/Service/PhoneVerificationService.java#L178-L188)

```java
/**
 * Format attendu: 222XXXXXXXX (11 chiffres au total)
 */
private boolean isValidMauritanianPhone(String phone) {
    return phone.matches("^[0-9]{8}$");   // ← 8 chiffres, pas 11
}
```

`normalizePhoneNumber` renvoie `22212345678` (11 chiffres) pour une entrée `+22212345678`, qui échoue alors sur cette regex. **Un utilisateur saisissant son numéro au format international est rejeté** avec le message « Le numéro doit être un numéro mauritanien valide (+222XXXXXXXX) » — précisément le format qu'il a utilisé.

Corollaire : le bloc `startsWith("222")` des lignes 121-124 est du code mort, puisque seuls 8 chiffres passent le filtre.

---

### M11. Les échecs d'envoi SMS sont silencieux

[PhoneVerificationService.java:223-228](backend/src/main/java/com/project/RecyConnect/Service/PhoneVerificationService.java#L223-L228)

```java
} catch (Exception e) {
    System.err.println("Erreur lors de l'envoi du SMS: " + e.getMessage());
    System.out.println("⚠️ Mode DEV: SMS non envoyé, mais code généré: " + code);
    // throw new RuntimeException(...);   ← désactivé
}
```

Si l'API SMS est en panne, le code est quand même enregistré et l'API répond `200 OK`. L'utilisateur attend indéfiniment un SMS qui n'arrivera jamais, sans aucun signal côté serveur. Le code OTP est en outre écrit en clair dans les logs.

---

### M12. Schéma de base géré par `ddl-auto=update`

[application.properties:10](backend/src/main/resources/application.properties#L10), [application-prod.properties:13](backend/src/main/resources/application-prod.properties#L13), [docker-compose.yml:37](backend/docker-compose.yml#L37)

Hibernate génère le schéma en production. Aucun outil de migration (Flyway/Liquibase). `update` n'applique jamais de suppression ni de modification de colonne : le schéma dérive silencieusement et les évolutions deviennent irréversibles et non traçables.

**Correction :** Flyway + `ddl-auto=validate` en production.

---

### M13. Mot de passe par défaut dans Docker Compose

[docker-compose.yml:12,35](backend/docker-compose.yml#L12) — `${DB_PASSWORD:-securepassword123}`. Le fallback fait qu'un `docker compose up` sans `.env` démarre avec un mot de passe connu, sur un port `5432` publié sur l'hôte. Il faut retirer le défaut pour que le démarrage échoue explicitement si la variable manque, et ne pas exposer le port de la base.

---

## 🟡 Moyens — robustesse, performance, qualité

### Robustesse

| # | Constat | Emplacement |
|---|---|---|
| R1 | **Aucune validation d'entrée** : la dépendance `spring-boot-starter-validation` est absente, aucun `@Valid` / `@NotNull` / `@Size` dans le projet. Toute la validation est manuelle et incomplète. | [pom.xml](backend/pom.xml) |
| R2 | **Aucun `@ControllerAdvice`** : les `RuntimeException` non capturées remontent en 500 avec une trace par défaut. Les `catch (RuntimeException)` renvoient souvent `404` là où `400`/`403` conviendrait (ex. `NegotiationController.patch`). | tout le projet |
| R3 | **Aucune pagination** : `findAll()` charge intégralement les tables `products`, `negotiations`, `notifications`, `users`. Ne tient pas la montée en charge. | tous les services |
| R4 | `updateQuantity` : NPE si `quantityAvailable` est `null`, et aucune borne inférieure — la quantité peut devenir négative. | [ProductService.java:161-172](backend/src/main/java/com/project/RecyConnect/Service/ProductService.java#L161-L172) |
| R5 | `ProductService.update` écrase tous les champs sans test de nullité : un `PUT` partiel efface les données absentes du corps. | [ProductService.java:123-137](backend/src/main/java/com/project/RecyConnect/Service/ProductService.java#L123-L137) |
| R6 | `POST /api/products/{id}/accept-offer` décrémente le stock hors du flux de négociation : pas de verrou pessimiste, pas de mise à jour du statut de l'offre. Doublonne `acceptBySeller` avec des garanties moindres. | [ProductController.java:124-146](backend/src/main/java/com/project/RecyConnect/Controller/ProductController.java#L124-L146) |
| R7 | `adminUpdateProduct` : le commentaire annonce « Admin peut tout modifier, y compris changer le propriétaire », mais `service.update()` ne touche ni `user` ni `category`. Fonctionnalité annoncée non implémentée. | [ProductController.java:183](backend/src/main/java/com/project/RecyConnect/Controller/ProductController.java#L183) |
| R8 | `JwtRequestFilter` : en cas de token invalide ou de session non concordante, la requête continue **sans authentification** au lieu de renvoyer 401. Sur une route publique en GET, l'appel réussit silencieusement — comportement difficile à diagnostiquer côté client. | [JwtRequestFilter.java:51-94](backend/src/main/java/com/project/RecyConnect/Config/JwtRequestFilter.java#L51-L94) |
| R9 | `sendBroadcastToAllUsers` : boucle de `save()` unitaires, sans `@Transactional` ni traitement par lot. Sur 10 000 utilisateurs, 10 000 INSERT et un échec à mi-parcours laisse un état partiel. | [NotificationService.java:217-235](backend/src/main/java/com/project/RecyConnect/Service/NotificationService.java#L217-L235) |
| R10 | `httpBasic()` activé en plus du JWT : second chemin d'authentification non nécessaire, à retirer. | [WebSecurityConfiguration.java:63](backend/src/main/java/com/project/RecyConnect/Config/WebSecurityConfiguration.java#L63) |
| R11 | DSL Spring Security en chaîne (`.csrf().disable().and()...`) dépréciée depuis 6.1, supprimée en 7.0. Migrer vers la forme lambda. | [WebSecurityConfiguration.java:29-64](backend/src/main/java/com/project/RecyConnect/Config/WebSecurityConfiguration.java#L29-L64) |

### Performance

| # | Constat | Emplacement |
|---|---|---|
| P1 | `search()` charge **toute** la table produits puis filtre en mémoire (4 `filter` successifs). À remplacer par une requête JPQL ou une `Specification`. | [ProductService.java:93-102](backend/src/main/java/com/project/RecyConnect/Service/ProductService.java#L93-L102) |
| P2 | Même schéma dans `findByUserId`, `findByUserIdWithStatus` et `getUnreadNotifications` : filtrage post-chargement alors qu'une clause `WHERE` suffirait. `countUnreadByReceiverId` fait pourtant bien les choses — incohérence. | services |
| P3 | Tous les `@ManyToOne` sont en `EAGER` (défaut JPA) : `Product` charge systématiquement `User` **et** `Category`. Un `findAll()` déclenche une cascade de requêtes (N+1). Passer en `LAZY` + `JOIN FETCH` ciblé. | [Product.java:35-41](backend/src/main/java/com/project/RecyConnect/Model/Product.java#L35-L41), [Negotiation.java:20-30](backend/src/main/java/com/project/RecyConnect/Model/Negotiation.java#L20-L30) |
| P4 | `notifyOutbidUsers` rappelle `getQueueByProductId` (requête + tri) à chaque création/modification d'offre, puis envoie une notification par offre concurrente. Coût en O(n) requêtes et notifications à chaque enchère. | [NegotiationService.java:376-396](backend/src/main/java/com/project/RecyConnect/Service/NegotiationService.java#L376-L396) |
| P5 | `notifyQueueUpdated` déclenche une notification « file mise à jour » à **chaque** événement d'offre, en plus des notifications métier. Génère du bruit et du volume FCM inutile. | [NegotiationService.java:398-412](backend/src/main/java/com/project/RecyConnect/Service/NegotiationService.java#L398-L412) |
| P6 | `spring.jpa.show-sql=true` en développement : verbeux et coûteux. | [application.properties:11](backend/src/main/resources/application.properties#L11) |

### Qualité et maintenance

| # | Constat |
|---|---|
| Q1 | **Aucun logger** : 12 `System.out/err.println` et 0 usage de SLF4J. Pas de niveaux, pas de corrélation, pas de rotation. Lombok est déjà présent — `@Slf4j` suffirait. |
| Q2 | **Code dupliqué** : `UserService.save` / `update` / `patch` / `patchAndGetUser` sont quatre variantes quasi identiques du même bloc. `NotificationService.save` et `sendNotification` dupliquent la logique d'envoi. Les blocs de parsing du préfixe `222` sont recopiés **cinq fois** dans `AuthController`. |
| Q3 | **Code mort** : `JwtUtil.hello()` (calcule `username` et ne l'utilise pas), `PhoneVerificationService.fromDTO`/`update`/`findAll`, `StaticResourceConfiguration` entièrement commenté, `Map<String,String> map` inutilisé dans `SimpleCorsFilter`, `objectMapper` jamais utilisé dans `WebSocketService`, `NotificationRepository.findBySenderId` sans appelant. |
| Q4 | `Negotiation.status` est une `String` libre alors que `ProductStatus` est un enum avec convertisseur JPA. Comparaisons par `equalsIgnoreCase` partout, valeurs invalides acceptées en base. À aligner sur le modèle de `ProductStatus`. |
| Q5 | Types incohérents : `Product.quantityAvailable` est `Long`, `Negotiation.quantity` est `Integer`. Casts et conversions dispersés dans `acceptBySeller`. |
| Q6 | Le téléphone est stocké en `Long` : perd les zéros initiaux, interdit le `+`, et impose le décorticage manuel du préfixe partout. Un `String` avec contrainte d'unicité serait adapté. |
| Q7 | Aucune contrainte d'unicité en base sur `username` ni `phone` — seulement des vérifications applicatives sujettes aux conditions de course (deux inscriptions simultanées passent). |
| Q8 | Packages en PascalCase (`Controller`, `Service`, `Model`, `DTO`) au lieu de la convention Java en minuscules. `UserRepo` détonne face aux autres `*Repository`. |
| Q9 | `upload-multiple` construit ses URLs avec `http://localhost:` en dur, alors que `upload` utilise `serverUrl` — les fichiers multiples sont inaccessibles depuis un client distant. |
| Q10 | `backend/uploads/0a344234-....jpg` est versionné dans Git. Le dossier d'uploads doit être dans `.gitignore`. |
| Q11 | `pom.xml` : métadonnées laissées au modèle par défaut (`<description>Demo project for Spring Boot</description>`, balises `<licenses>`, `<developers>`, `<scm>` vides). |
| Q12 | [API_USER_ACCESS.md](API_USER_ACCESS.md) documente des endpoints inexistants : `POST /api/users/{id}/fcm-token` et `/api/admin/categories`. Le fichier affirme aussi que « Spring Security bloque ces requêtes » pour les catégories, ce que la configuration ne fait pas (voir M6). |
| Q13 | `README.md` fait 2 lignes : ni prérequis, ni procédure de démarrage, ni variables d'environnement requises. |

### Tests

**202 méthodes `@Test`, mais la couverture réelle est proche de zéro sur le code qui compte.**

```
ProductTest        48 tests  ─┐
UserTest           41 tests   │
NegotiationTest    35 tests   ├─ 201 tests : uniquement getters/setters,
NotificationTest   30 tests   │  builders, equals/hashCode de POJO Lombok
CategoryTest       26 tests   │
RoleTest           21 tests  ─┘
RecyConnectApplicationTests  1 test  (contextLoads)
```

Ces tests valident du code **généré par Lombok**. Aucun test ne couvre :

- Les contrôleurs (aucun `@WebMvcTest`, aucun `MockMvc`)
- Les services (aucun mock, aucune vérification de la logique métier)
- La sécurité — alors que `spring-security-test` est **déjà déclaré** dans le `pom.xml` et jamais utilisé
- `JwtUtil` : génération, validation, expiration, extraction de claims
- La logique de file d'offres : `sortAndRank`, `cancelIncompatibleOffers`, `acceptBySeller`, gestion du stock
- `PhoneVerificationService` : ce qui aurait immédiatement révélé M10

Priorité : des tests `@WebMvcTest` avec `@WithMockUser` sur les règles d'autorisation auraient détecté C1, C8, M1 et M6.

---

## Points positifs

Ce qui est bien fait mérite d'être noté :

- **Verrou pessimiste** (`findByIdForUpdate`, `LockModeType.PESSIMISTIC_WRITE`) sur l'acceptation d'offre, avec `@Transactional` : le point de concurrence critique est correctement traité.
- **Session unique par appareil** : `UserSession` avec `sessionVersion` + `deviceId` vérifiés à chaque requête est un mécanisme solide et bien pensé, largement supérieur à la liste noire en mémoire qu'il rend inutile.
- **Contrôles de propriété** rigoureux et cohérents dans `ProductController` et `NegotiationController` (propriétaire ou admin, avec les bons codes 401/403).
- `ProductStatus` : enum + `@JsonCreator`/`@JsonValue` + `AttributeConverter` — la bonne façon de faire, à généraliser à `NegotiationStatus`.
- **Stratégie de notification** intelligente : WebSocket si l'utilisateur est en ligne, FCM sinon — économise les quotas push.
- Mots de passe correctement hachés avec **BCrypt**.
- Bonne séparation DTO / entité : les entités ne fuitent pas dans les réponses (le mot de passe n'est jamais sérialisé).
- `application-prod.properties` externalise correctement la configuration — le modèle à appliquer au profil de développement.

---

## Plan d'action recommandé

### Immédiat — avant toute exposition publique

1. **Révoquer** le token API SMS et changer le mot de passe PostgreSQL (compromis, cf. C5)
2. Forcer `Role.USER` dans `/api/auth/register` (C1)
3. Cesser de renvoyer le code OTP dans les réponses HTTP (C2)
4. Supprimer `FCMTestController` et `PhoneVerificationController` (C3, C4)
5. Sortir les secrets du dépôt et du code source ; externaliser le secret JWT (C5)
6. Réactiver l'authentification WebSocket et passer aux destinations utilisateur Spring (C6)
7. Retirer `/api/notifications/**` et `/api/negotiations/**` des routes publiques (C7)
8. Ajouter les contrôles de propriété sur `UserController` (C8)
9. Corriger `loadUserByUsername` — **une ligne qui débloque tout l'espace admin** (M1)

### Court terme

10. Restreindre le CORS à une liste blanche (M4)
11. Verrouiller `PATCH /api/negotiations/{id}` (M5)
12. Protéger les écritures sur les catégories et les notifications (M6, M7)
13. Valider les uploads, contrôler la propriété à la suppression (M8)
14. `SecureRandom` + limitation de débit + consommation du code OTP (M9)
15. Corriger la validation du numéro de téléphone (M10) — **bug fonctionnel visible par les utilisateurs**
16. Ajouter `spring-boot-starter-validation` + `@Valid` et un `@ControllerAdvice` (R1, R2)

### Moyen terme

17. Flyway + `ddl-auto=validate` (M12)
18. Pagination sur tous les `findAll` (R3)
19. Requêtes JPQL au lieu du filtrage en mémoire ; `FetchType.LAZY` (P1, P2, P3)
20. Tests d'intégration sur la sécurité et la logique métier — en réutilisant `spring-security-test`, déjà présent
21. SLF4J à la place de `System.out` (Q1)
22. Refactoriser les duplications : extraction du parsing téléphone, unification des méthodes de mise à jour (Q2)

---

## Suivi des corrections — branche `fix/securite-critique`

### ✅ Corrigé

| Réf | Constat | Correctif appliqué |
|---|---|---|
| C1 | Création de compte ADMIN par n'importe qui | Rôle forcé à `USER` dans `/register` ; le champ `role` du corps est ignoré |
| C2 | Code OTP renvoyé dans la réponse HTTP | `sendVerificationCode` retourne `void` ; le code ne transite plus que par SMS |
| C3 | `/api/fcm-test/**` public | `@PreAuthorize("hasRole('ADMIN')")` au niveau classe + règle `hasRole` dans la config |
| C4 | Tous les OTP lisibles et forgeables | `PhoneVerificationController` supprimé (le vrai flux est dans `AuthController`) |
| C5 | Secrets dans Git | Externalisés en variables d'environnement ; `application.properties` désormais non suivi ; `.example` fourni |
| C6 | WebSocket sans authentification | Intercepteur réactivé + autorisation sur `SUBSCRIBE` (canal personnel uniquement) |
| C7 | Notifications et négociations publiques | Retirées des routes en lecture publique |
| C8 | IDOR sur les comptes utilisateurs | Contrôle *self-or-admin* sur `PUT`/`PATCH`/`DELETE` ; `GET /api/users` et `POST` réservés aux admins |
| M1 | Autorités vides → espace admin inopérant | `loadUserByUsername` retourne l'entité `User` ; repli sur `ROLE_USER` si le rôle est absent |
| M2 | Règle de sécurité morte (singulier/pluriel) | Disparue avec la suppression du contrôleur |
| M4 | CORS permissif | `app.client.url=*` retiré de la production (valeur explicite requise) |
| M9 | OTP faible | `SecureRandom` ; code à usage unique ; un seul code actif par numéro ; anti-spam de 60 s |
| M10 | Validation du numéro incohérente | Regex `^(222)?[0-9]{8}$` : le format international est enfin accepté |
| M11 | Échecs SMS silencieux | L'erreur remonte ; le code n'est plus journalisé |
| M12 | `ddl-auto` en production | Partiellement : les tests utilisent H2 `create-drop` ; Flyway reste à faire |
| M13 | Mot de passe Docker par défaut | Secrets requis explicitement ; port PostgreSQL non publié |
| R11 | DSL Spring Security dépréciée | Migration vers la forme lambda |
| R10 | `httpBasic()` superflu | Retiré |
| — | 2 tests erronés (`getAuthorities`) | Alignés sur la convention `ROLE_` ; échouaient déjà avant ces travaux |
| — | `contextLoads` inexécutable sans PostgreSQL | H2 en portée test + `src/test/resources/application.properties` |

### ⏳ Restant

Tous les autres constats : **M3** (liste noire JWT en mémoire), **M5** (`PATCH /negotiations` trop permissif), **M6** (catégories), **M7** (notifications forgeables), **M8** (uploads non validés), ainsi que l'ensemble des points 🟡 (validation d'entrée, `@ControllerAdvice`, pagination, N+1, logger, duplications).

### ⚠️ Actions manuelles indispensables

Le code ne peut pas les réaliser :

1. **Révoquer le token API SMS Chinguisoft** et en générer un nouveau.
2. **Changer le mot de passe PostgreSQL** de production.
3. Définir `JWT_SECRET` en production (`openssl rand -base64 48`). Changer ce secret **invalide tous les tokens en circulation** : les utilisateurs devront se reconnecter.
4. Décider du sort de l'historique Git : les secrets restent lisibles dans tous les commits depuis `24574d8`. Purge via `git filter-repo` (réécrit l'historique, nécessite un `push --force` coordonné) ou acceptation du risque après rotation.

### 📱 Changements visibles par le client mobile

À vérifier côté application Flutter avant déploiement :

- `POST /api/auth/send-code` ne renvoie plus le code — l'app doit s'appuyer uniquement sur le SMS reçu.
- `GET /api/notifications/**`, `/api/negotiations/**` et `/api/users/**` exigent désormais un token.
- Un code OTP n'est plus valable qu'une fois : rejouer le même code échoue.
- Deux demandes de code à moins de 60 s d'intervalle sont refusées.
- Un échec d'envoi SMS renvoie maintenant une erreur au lieu d'un `200`.
- Les numéros au format `+222XXXXXXXX` sont acceptés (ils étaient auparavant rejetés à tort).
- L'abonnement WebSocket exige un token valide, et uniquement sur son propre canal `/user/{sonId}/notifications`.
