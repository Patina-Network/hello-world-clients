#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-all}" in
  go)
    cd backends/go
    test -z "$(gofmt -l cmd internal)"
    go test -race -count=1 ./...
    go vet ./...
    ;;
  rust)
    cargo fmt --manifest-path backends/rust/Cargo.toml --check
    cargo clippy --manifest-path backends/rust/Cargo.toml --locked --all-targets -- -D warnings
    cargo test --manifest-path backends/rust/Cargo.toml --locked
    ;;
  java) mvn -B -ntp -f backends/java/pom.xml spotless:check checkstyle:check verify ;;
  frontend)
    cd frontend
    npm ci
    npm test
    npm run build
    ;;
  all)
    for language in go rust java frontend; do bash scripts/test.sh "$language"; done
    ;;
  *) echo "Usage: $0 [go|rust|java|frontend|all]" >&2; exit 2 ;;
esac
