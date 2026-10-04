export function goEnv() {
  return {
    ...process.env,
    GOPROXY:
      process.env.GOPROXY ??
      "https://pkg.vpn.patinanetwork.org/go/go,https://proxy.golang.org,direct",
    GONOSUMDB: process.env.GONOSUMDB ?? "patinanetwork.org",
  };
}
