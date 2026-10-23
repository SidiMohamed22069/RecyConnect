# Étape 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Utilisateur non root: un processus Java n'a aucune raison d'etre root dans
# le conteneur. Le dossier uploads (volume) lui appartient.
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/uploads && chown -R app:app /app

# Copier le JAR
COPY --from=build --chown=app:app /app/target/*.jar app.jar

# curl pour le HEALTHCHECK (jre-alpine n'en a pas)
RUN apk add --no-cache curl

# Variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8081

# Exposer le port
EXPOSE 8081

USER app

# /api/app/version est public et ne touche pas la base: repond des que Spring
# est pret. Docker marque le conteneur "unhealthy" si l'application ne demarre
# pas (secret manquant, base injoignable...).
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:${SERVER_PORT:-8081}/api/app/version > /dev/null || exit 1

# Lancer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
