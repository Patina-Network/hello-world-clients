import { pathToFileURL } from 'node:url';

const languages = ['go', 'rust', 'java'];
export function metadata(tag) {
  const match = /^(?:(go|rust|java)-)?(v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))$/.exec(tag);
  if (!match) throw new Error('Use vX.Y.Z or <go|rust|java>-vX.Y.Z');
  return [match[1] ? [match[1]] : [...languages], match[2]];
}
export function plan(event, ref, sha, sameRepository = false, language = 'all') {
  if (!/^[0-9a-f]{40}$/.test(sha)) throw new Error('A full commit SHA is required');
  let selected = [...languages], environment = 'production', version = sha.slice(0, 7), publish = true, deploy = true;
  if (event === 'pull_request') {
    environment = 'staging'; version = `staging-${version}`; publish = sameRepository; deploy = false;
  } else if (event === 'workflow_dispatch') {
    if (!['all', ...languages].includes(language)) throw new Error('Unknown language');
    selected = language === 'all' ? selected : [language];
    environment = 'staging'; version = `staging-${version}`; deploy = false;
  } else if (event === 'push' && ref.startsWith('refs/tags/')) {
    [selected, version] = metadata(ref.slice('refs/tags/'.length));
  } else if (event !== 'push' || ref !== 'refs/heads/main') {
    throw new Error('Unsupported release event or ref');
  }
  return { matrix: { language: selected }, version, environment, publish, deploy };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const env = process.env;
  const result = plan(env.GITHUB_EVENT_NAME, env.GITHUB_REF, env.GITHUB_SHA, env.SAME_REPOSITORY === 'true', env.CLIENT_LANGUAGE || 'all');
  for (const [key, value] of Object.entries(result)) console.log(`${key}=${typeof value === 'string' ? value : JSON.stringify(value)}`);
}
