# Hello World clients

Go, Java (Spring Boot), and Rust HTTP clients for the Hello World gRPC service,
with a shared React frontend.

## Dependencies

Connect to the Patina network before resolving SDK dependencies. All clients use
the published `1.0.1` SDK from `https://pkg.vpn.patinanetwork.org`:

| Client | Package | Registry configuration |
| --- | --- | --- |
| Go | `patinanetwork.org/grpc/hello-world-grpc-service` | `.github/scripts/src/toolchain.ts`, Go Dockerfile |
| Java | `org.patinanetwork.grpc:hello-world-grpc-service` | `backends/java/pom.xml` |
| Rust | `hello-world-grpc-service` | `.cargo/config.toml` |

The Go module path above is the path declared by the published release. The
clients do not generate protobuf code; schema changes and SDK releases belong
in `hello-world-grpc-service`.

For direct Go commands, set these variables in your shell:

```sh
export GOPROXY=https://pkg.vpn.patinanetwork.org/go/go,https://proxy.golang.org,direct
export GONOSUMDB=patinanetwork.org
```

## Build and test

Use Bun, Node 24, Go 1.25, Java 21 with Maven, and Rust 1.93. The make targets
run the same scripts as CI.

```sh
make pretest               # format, lint, and compile checks for everything
make pretest-go            # also: pretest-java, pretest-rust, pretest-frontend
make test                  # all test suites
make test-go               # also: test-java, test-rust, test-frontend
make ci-scripts            # typecheck, lint, and unit test .github/scripts
make frontend
```

Docker builds use the repository root as their context:

```sh
docker build -f backends/go/Dockerfile -t hello-world-client-go .
docker build -f backends/java/Dockerfile -t hello-world-client-java .
docker build -f backends/rust/Dockerfile -t hello-world-client-rust .
```

Docker's build environment must also be able to reach the package registry.
Each image contains the frontend with the corresponding client language label.

## Configuration

All clients accept `GRPC_TARGET` (default `hello-world-grpc-service:50051`),
`GRPC_TLS` (default `false`), `GRPC_TIMEOUT_MS` (1–30000, default 3000), and
`STATIC_DIR` (default `../../frontend/dist`, relative to the backend directory).
Go and Rust use `HTTP_ADDR`; Java uses `HTTP_HOST` and `HTTP_PORT`. The images
listen on port 8080 and serve frontend assets from `/app/static`.

The Go executable also accepts flags such as `--grpc-target` and
`--grpc-timeout-ms`; flags override environment variables. Run it with `--help`
for the complete list. Java's settings live in `application.properties` and its
gRPC channel is managed by Spring.

## CI and deployment

The pipeline follows the Patina Network layout. Workflows in `.github/workflows`
define triggers, permissions, and the job graph. Composite actions in
`.github/composite` each run one Bun script from `.github/scripts/src`, and
every script takes the client language as a flag.

`ci.yml` runs on pull requests and pushes to main:

1. `frontendPretest` and `backendPretest` (one job per language) run format,
   lint, and compile checks.
2. `frontendTest` and `backendTest` run the test suites after their pre-test
   passes.
3. `buildImage` builds each client image, starts it, checks `/healthz`, `/`,
   and the gRPC error response, then pushes it. PRs push `staging-<sha>` and
   comment the tags on the PR. Main pushes `<sha>`. Fork PRs skip this job.
4. On main, `deployProduction` updates the production image tags in
   `k8s-manifests`.

`cd.yml` runs on release tags. `vX.Y.Z` releases every client and `go-vX.Y.Z`,
`java-vX.Y.Z`, or `rust-vX.Y.Z` releases one. It re-tags the `<sha>` images that
CI on main built for the tagged commit, then deploys that version to
production. Push a release tag only after CI on main has finished for that
commit.

Registry access goes through the pipeline setup action's Headscale connection,
using the `HEADSCALE_ADDRESS`, `HEADSCALE_USER`, and `HEADSCALE_PREAUTHKEY`
secrets. Fork pull requests do not receive those credentials, so their backend
checks fail unless the runner can already reach the registry. Images push with
`DOCKER_HUB_USERNAME` and `DOCKER_HUB_PAT`. Deploys use the
`_GITHUB_APP_APP_ID`, `_GITHUB_APP_INSTALLATION_ID`, and
`_GITHUB_APP_PEM_CONTENT` GitHub App secrets.
