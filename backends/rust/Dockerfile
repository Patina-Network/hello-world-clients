# syntax=docker/dockerfile:1.7
FROM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
ARG CLIENT_LANGUAGE=rust
ENV VITE_CLIENT_LANGUAGE=${CLIENT_LANGUAGE}
RUN npm run build

FROM rust:1.93-bookworm AS rust-build
COPY .cargo/ /src/.cargo/
WORKDIR /src/backends/rust
COPY backends/rust/ ./
COPY backends/rust/vendor.toml .cargo/config.toml
RUN --mount=type=cache,target=/src/backends/rust/target \
    cargo build --locked --offline --release && cp target/release/hello-world-client-rust /server

FROM gcr.io/distroless/cc-debian12:nonroot AS rust
WORKDIR /app
COPY --from=rust-build /server /app/server
COPY --from=frontend /src/frontend/dist /app/static
ENV STATIC_DIR=/app/static HTTP_ADDR=0.0.0.0:8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/server"]

