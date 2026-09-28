#!/usr/bin/env bash
set -euo pipefail
container=$(docker run --detach --publish 127.0.0.1:8080:8080 \
  --env GRPC_TARGET=127.0.0.1:1 --env GRPC_TIMEOUT_MS=1000 "${1:?image required}")
trap 'docker logs "$container"; docker rm --force "$container" >/dev/null' EXIT
curl --fail --silent --show-error --retry 20 --retry-delay 1 --retry-connrefused \
  http://127.0.0.1:8080/healthz
curl --fail --silent --show-error http://127.0.0.1:8080/ | grep -q '<div id="root">'
code=$(curl --silent --show-error --output /tmp/client-smoke-error.json \
  --write-out '%{http_code}' 'http://127.0.0.1:8080/api/echo?name=Ada')
test "$code" = 503
jq -e '.error == "service unavailable"' /tmp/client-smoke-error.json >/dev/null
