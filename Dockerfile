# Étape 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Créer le dossier uploads
RUN mkdir -p /app/uploads

# Copier le JAR
COPY --from=build /app/target/*.jar app.jar

# Variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8081

# Exposer le port
EXPOSE 8081

# Lancer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
