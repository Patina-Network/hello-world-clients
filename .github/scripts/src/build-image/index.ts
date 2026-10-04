import {
  DockerClient,
  type Environment,
  GitHubClient,
} from "@tahminator/pipeline";
import { $ } from "bun";
import yargs from "yargs";
import { hideBin } from "yargs/helpers";

import {
  ARCHITECTURES,
  dockerRepository,
  GITHUB_OWNER,
  GITHUB_REPOSITORY,
  LANGUAGES,
  requireEnv,
  shortSha,
} from "../release";
import { vendorDependencies } from "../vendor/deps";
import { smokeTest } from "./smoke";

const { language, environment, sha, prId, arch } = await yargs(
  hideBin(process.argv),
)
  .option("language", {
    choices: LANGUAGES,
    demandOption: true,
  })
  .option("environment", {
    choices: ["staging", "production"] satisfies Environment[],
    describe: "staging images are tagged staging-<sha>; production are <sha>",
    demandOption: true,
  })
  .option("sha", {
    type: "string",
    describe: "Full commit SHA the image is built from",
    demandOption: true,
  })
  .option("prId", {
    type: "string",
    describe: "Pull request to comment the pushed tags on; empty skips it",
    default: "",
  })
  .option("arch", {
    choices: ARCHITECTURES,
    describe:
      "Target architecture, built natively on a matching runner. arm64 pushes to a separate -arm repository",
    default: "amd64" as const,
  })
  .strict()
  .parse();

async function main() {
  const dockerHubUsername = requireEnv("DOCKER_HUB_USERNAME");
  const dockerHubPat = requireEnv("DOCKER_HUB_PAT");

  const repository = dockerRepository(language, arch);
  const image = `${dockerHubUsername}/${repository}`;
  const short = shortSha(sha);
  const tags =
    environment === "staging" ?
      [`staging-${short}`, `sha-${sha}`]
    : [short, `sha-${sha}`];

  await vendorDependencies(language);

  await using dockerClient = await DockerClient.create(
    dockerHubUsername,
    dockerHubPat,
  );

  await dockerClient.buildImage({
    dockerRepository: repository,
    dockerFileLocation: `backends/${language}/Dockerfile`,
    tags,
    shouldUpload: false,
    platforms: [`linux/${arch}`],
  });

  await smokeTest(`${image}:${tags[0]}`);

  for (const tag of tags) {
    await $`docker push ${image}:${tag}`;
  }

  if (prId) {
    const ghClient = await GitHubClient.createWithDefaultCiToken();
    await ghClient.sendPrMessage({
      prId: Number(prId),
      owner: GITHUB_OWNER,
      repository: GITHUB_REPOSITORY,
      message: `The ${language} client image has been uploaded to https://hub.docker.com/r/${image}/tags under the following tags:

${tags.map((t) => `- \`${repository}:${t}\``).join("\n")}
`,
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
