# One image: the React build is served as static files by Spring Boot, so there is one service to deploy.

FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS api
WORKDIR /api
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN ./mvnw -B -q package -DskipTests && mv target/radar-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S radar && adduser -S radar -G radar
WORKDIR /app
COPY --from=api /api/app.jar app.jar
USER radar
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
