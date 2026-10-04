import yargs from "yargs";
import { hideBin } from "yargs/helpers";

import { LANGUAGES } from "../release";
import { vendorDependencies } from "./deps";

const { language } = await yargs(hideBin(process.argv))
  .option("language", {
    choices: LANGUAGES,
    describe: "Client whose dependencies to download for an offline build",
    demandOption: true,
  })
  .strict()
  .parse();

vendorDependencies(language)
  .then(() => {
    process.exit(0);
  })
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
