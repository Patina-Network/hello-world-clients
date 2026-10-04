import { $ } from "bun";
import yargs from "yargs";
import { hideBin } from "yargs/helpers";

import { goEnv } from "../toolchain";

const { language } = await yargs(hideBin(process.argv))
  .option("language", {
    choices: ["frontend", "go", "rust", "java"] as const,
    describe: "Test suite to run",
    demandOption: true,
  })
  .strict()
  .parse();

async function main() {
  switch (language) {
    case "frontend": {
      await $`npm ci`.cwd("frontend");
      await $`npm test`.cwd("frontend");
      return;
    }
    case "go": {
      await $`go test -race -count=1 ./...`.cwd("backends/go").env(goEnv());
      return;
    }
    case "rust": {
      await $`cargo test --locked`.cwd("backends/rust");
      return;
    }
    case "java": {
      await $`mvn -B -ntp verify`.cwd("backends/java");
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
