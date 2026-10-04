import { GitHubClient } from "@tahminator/pipeline";
import yargs from "yargs";
import { hideBin } from "yargs/helpers";

import {
  dockerRepository,
  GITHUB_OWNER,
  GITHUB_REPOSITORY,
  type Language,
  LANGUAGES,
  parseReleaseTag,
  requireEnv,
  shortSha,
} from "../release";

const { releaseTag, sha } = await yargs(hideBin(process.argv))
  .option("releaseTag", {
    type: "string",
    describe: "Deploy the clients and version named by a release tag",
  })
  .option("sha", {
    type: "string",
    describe: "Deploy every client's image for this commit",
  })
  .check(({ releaseTag, sha }) => {
    if (!releaseTag === !sha) {
      throw new Error("Pass exactly one non-empty --releaseTag or --sha");
    }
    return true;
  })
  .strict()
  .parse();

async function main() {
  const { languages, version } = resolveTarget();

  const ghClient = await GitHubClient.createWithGithubAppToken({
    appId: requireEnv("_GITHUB_APP_APP_ID"),
    installationId: requireEnv("_GITHUB_APP_INSTALLATION_ID"),
    privateKey: requireEnv("_GITHUB_APP_PEM_CONTENT"),
  });

  for (const language of languages) {
    const repository = dockerRepository(language);
    await ghClient.updateK8sTagWithPR({
      manifestRepo: [GITHUB_OWNER, "k8s-manifests"],
      originRepo: [GITHUB_OWNER, GITHUB_REPOSITORY],
      kustomizationFilePath: `base/production/${repository}/kustomization.yaml`,
      imageName: `patinanetwork/${repository}`,
      newTag: version,
      environment: "production",
    });
  }
}

function resolveTarget(): { languages: Language[]; version: string } {
  if (releaseTag) {
    return parseReleaseTag(releaseTag);
  }
  return { languages: [...LANGUAGES], version: shortSha(sha ?? "") };
}

main()
  .then(() => {
    process.exit(0);
  })
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
