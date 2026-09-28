#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools/bin
export GOBIN="$PWD/.tools/bin"
export PATH="$GOBIN:$PATH"
go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.11
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.6.1
protoc -I proto --go_out=backends/go --go_opt=module=github.com/Patina-Network/hello-world-clients/backends/go \
 --go_opt=Mv1/helloworld.proto=github.com/Patina-Network/hello-world-clients/backends/go/gen/helloworld \
 --go-grpc_out=backends/go --go-grpc_opt=module=github.com/Patina-Network/hello-world-clients/backends/go \
 --go-grpc_opt=Mv1/helloworld.proto=github.com/Patina-Network/hello-world-clients/backends/go/gen/helloworld \
 proto/v1/helloworld.proto
