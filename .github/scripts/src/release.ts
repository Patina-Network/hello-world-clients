export const GITHUB_OWNER = "Patina-Network";
export const GITHUB_REPOSITORY = "hello-world-clients";

export const LANGUAGES = ["go", "rust", "java"] as const;
export type Language = (typeof LANGUAGES)[number];

export const ARCHITECTURES = ["amd64", "arm64"] as const;
export type Architecture = (typeof ARCHITECTURES)[number];

export function dockerRepository(
  language: Language,
  arch: Architecture = "amd64",
) {
  const repository = `hello-world-client-${language}`;
  return arch === "arm64" ? `${repository}-arm` : repository;
}

export function shortSha(sha: string) {
  if (!/^[0-9a-f]{40}$/.test(sha)) {
    throw new Error(`Expected a full commit SHA, got "${sha}"`);
  }
  return sha.slice(0, 7);
}

export function parseReleaseTag(tag: string): {
  languages: Language[];
  version: string;
} {
  const match =
    /^(?:(go|rust|java)-)?(v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))$/.exec(
      tag,
    );
  if (!match?.[2]) {
    throw new Error(
      `Release tag "${tag}" must be vX.Y.Z or <go|rust|java>-vX.Y.Z`,
    );
  }
  const language = match[1] as Language | undefined;
  return {
    languages: language ? [language] : [...LANGUAGES],
    version: match[2],
  };
}

export function requireEnv(name: string) {
  const v = process.env[name];
  if (!v) {
    throw new Error(`Missing ${name} from env`);
  }
  return v;
}
