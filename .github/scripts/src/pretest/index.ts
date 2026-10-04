import { $ } from "bun";
import yargs from "yargs";
import { hideBin } from "yargs/helpers";

import { goEnv } from "../toolchain";

const { language } = await yargs(hideBin(process.argv))
  .option("language", {
    choices: ["frontend", "go", "rust", "java"] as const,
    describe: "Format, lint, and compile checks to run (no tests)",
    demandOption: true,
  })
  .strict()
  .parse();

async function main() {
  switch (language) {
    case "frontend": {
      await $`npm ci`.cwd("frontend");
      // `build` typechecks before bundling.
      await $`npm run build`.cwd("frontend");
      return;
    }
    case "go": {
      const unformatted = (
        await $`gofmt -l cmd internal`.cwd("backends/go").text()
      ).trim();
      if (unformatted) {
        throw new Error(`gofmt found unformatted files:\n${unformatted}`);
      }
      await $`go vet ./...`.cwd("backends/go").env(goEnv());
      return;
    }
    case "rust": {
      await $`cargo fmt --check`.cwd("backends/rust");
      await $`cargo clippy --locked --all-targets -- -D warnings`.cwd(
        "backends/rust",
      );
      return;
    }
    case "java": {
      await $`mvn -B -ntp spotless:check checkstyle:check test-compile`.cwd(
        "backends/java",
      );
      return;
    }
  }
}

main()
  .then(() => {
    process.exit(0);
  })
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
