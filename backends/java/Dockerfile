# syntax=docker/dockerfile:1.7
FROM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
ARG CLIENT_LANGUAGE=java
ENV VITE_CLIENT_LANGUAGE=${CLIENT_LANGUAGE}
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS java-build
WORKDIR /src/backends/java
COPY backends/java/ ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS java
WORKDIR /app
COPY --from=java-build /src/backends/java/target/hello-world-client-java-0.1.0.jar /app/server.jar
COPY --from=frontend /src/frontend/dist /app/static
ENV STATIC_DIR=/app/static HTTP_PORT=8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/server.jar"]
