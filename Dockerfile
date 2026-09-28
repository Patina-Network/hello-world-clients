# syntax=docker/dockerfile:1.7
FROM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
ARG CLIENT_LANGUAGE=local
ENV VITE_CLIENT_LANGUAGE=${CLIENT_LANGUAGE}
RUN npm run build

FROM golang:1.25-bookworm AS go-build
WORKDIR /src/backends/go
COPY backends/go/go.mod backends/go/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY backends/go/ ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

FROM rust:1.93-bookworm AS rust-build
WORKDIR /src/backends/rust
COPY proto/ /src/proto/
COPY backends/rust/ ./
RUN --mount=type=cache,target=/usr/local/cargo/registry --mount=type=cache,target=/src/backends/rust/target \
    cargo build --locked --release && cp target/release/hello-world-client-rust /server

FROM maven:3.9-eclipse-temurin-21 AS java-build
WORKDIR /src/backends/java
COPY proto/ /src/proto/
COPY backends/java/ ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM gcr.io/distroless/static-debian12:nonroot AS go
WORKDIR /app
COPY --from=go-build /out/server /app/server
COPY --from=frontend /src/frontend/dist /app/static
ENV STATIC_DIR=/app/static HTTP_ADDR=:8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/server"]

FROM gcr.io/distroless/cc-debian12:nonroot AS rust
WORKDIR /app
COPY --from=rust-build /server /app/server
COPY --from=frontend /src/frontend/dist /app/static
ENV STATIC_DIR=/app/static HTTP_ADDR=0.0.0.0:8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/server"]

FROM eclipse-temurin:21-jre-jammy AS java
WORKDIR /app
COPY --from=java-build /src/backends/java/target/hello-world-client-java-0.1.0.jar /app/server.jar
COPY --from=frontend /src/frontend/dist /app/static
ENV STATIC_DIR=/app/static HTTP_PORT=8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/server.jar"]
