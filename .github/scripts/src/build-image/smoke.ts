import { $ } from "bun";

const BASE_URL = "http://127.0.0.1:8080";

export async function smokeTest(image: string) {
  const container = (
    await $`docker run --detach --publish 127.0.0.1:8080:8080 --env GRPC_TARGET=127.0.0.1:1 --env GRPC_TIMEOUT_MS=1000 ${image}`.text()
  ).trim();

  try {
    await waitForHealthy();

    const index = await fetch(`${BASE_URL}/`);
    if (!index.ok || !(await index.text()).includes('<div id="root">')) {
      throw new Error("GET / did not serve the frontend");
    }

    const echo = await fetch(`${BASE_URL}/api/echo?name=Ada`);
    const body = (await echo.json()) as { error?: string };
    if (echo.status !== 503 || body.error !== "service unavailable") {
      throw new Error(
        `Expected 503 "service unavailable" from /api/echo, got ${echo.status} ${JSON.stringify(body)}`,
      );
    }

    console.log(`Smoke test passed for ${image}`);
  } finally {
    await $`docker logs ${container}`.nothrow();
    await $`docker rm --force ${container}`.quiet().nothrow();
  }
}

async function waitForHealthy() {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const res = await fetch(`${BASE_URL}/healthz`, {
      signal: AbortSignal.timeout(3_000),
    }).catch(() => null);
    if (res?.ok) {
      return;
    }
    await Bun.sleep(1_000);
  }
  throw new Error("/healthz did not return 200 within 30 seconds");
}
