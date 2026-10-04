/**
 * The Go SDK is served from the Patina package registry, which has no checksum
 * database entry, so `patinanetwork.org` modules skip sumdb verification.
 */
export function goEnv() {
  return {
    ...process.env,
    GOPROXY:
      process.env.GOPROXY ??
      "https://pkg.vpn.patinanetwork.org/go/go,https://proxy.golang.org,direct",
    GONOSUMDB: process.env.GONOSUMDB ?? "patinanetwork.org",
  };
}
