# syntax=docker/dockerfile:1.7
FROM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
ARG CLIENT_LANGUAGE=go
ENV VITE_CLIENT_LANGUAGE=${CLIENT_LANGUAGE}
RUN npm run build

FROM golang:1.25-bookworm AS go-build
ENV GOFLAGS=-mod=vendor GOPROXY=off
WORKDIR /src/backends/go
COPY backends/go/ ./
RUN --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot AS go
WORKDIR /app
COPY --from=go-build /out/server /app/server
COPY --from=frontend /src/frontend/dist /app/static
ENV STATIC_DIR=/app/static HTTP_ADDR=:8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/server"]

