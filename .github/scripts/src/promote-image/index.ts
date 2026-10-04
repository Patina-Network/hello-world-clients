import { DockerClient } from "@tahminator/pipeline";
import yargs from "yargs";
import { hideBin } from "yargs/helpers";

import {
  dockerRepository,
  parseReleaseTag,
  requireEnv,
  shortSha,
} from "../release";

const { releaseTag, sha } = await yargs(hideBin(process.argv))
  .option("releaseTag", {
    type: "string",
    describe: "For example, v1.2.3 or go-v1.2.3",
    demandOption: true,
  })
  .option("sha", {
    type: "string",
    describe: "Full SHA of the tagged commit; its image must already exist",
    demandOption: true,
  })
  .strict()
  .parse();

async function main() {
  const { languages, version } = parseReleaseTag(releaseTag);

  await using dockerClient = await DockerClient.create(
    requireEnv("DOCKER_HUB_USERNAME"),
    requireEnv("DOCKER_HUB_PAT"),
  );

  for (const language of languages) {
    await dockerClient.promoteDockerImage({
      originalTag: shortSha(sha),
      newGithubTags: [version, "latest"],
      repository: dockerRepository(language),
    });
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
