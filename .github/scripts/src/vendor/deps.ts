import { $ } from "bun";
import { resolve } from "node:path";

import type { Language } from "../release";

import { goEnv } from "../toolchain";

export async function vendorDependencies(language: Language) {
  switch (language) {
    case "go": {
      await $`go mod vendor`.cwd("backends/go").env(goEnv());
      return;
    }
    case "rust": {
      const config = await $`cargo vendor --locked vendor`
        .cwd("backends/rust")
        .text();
      await Bun.write("backends/rust/vendor.toml", config);
      return;
    }
    case "java": {
      const repository = resolve("backends/java/.m2/repository");
      await $`mvn -B -ntp -Dmaven.repo.local=${repository} dependency:go-offline package -DskipTests`.cwd(
        "backends/java",
      );
      return;
    }
  }
}
