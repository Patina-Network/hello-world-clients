import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function comment(records, sha) {
  if (!/^[0-9a-f]{40}$/.test(sha)) throw new Error('A full commit SHA is required');
  const sorted = [...records].sort((a, b) => a.language.localeCompare(b.language));
  if (!sorted.length) throw new Error('Image artifacts are required');
  return sorted.map(record => {
    const name = `hello-world-client-${record.language}`;
    if (!['go', 'rust', 'java'].includes(record.language) || record.image !== `patinanetwork/${name}`) {
      throw new Error('Invalid image identity');
    }
    if (!record.version) throw new Error('Image version is required');
    return [
      `The image has been uploaded to https://hub.docker.com/r/patinanetwork/${name}/tags under the following tags:`,
      '',
      `- \`${name}:${record.version}\``,
      `- \`${name}:sha-${sha}\``,
    ].join('\n');
  }).join('\n\n');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [artifacts, sha] = process.argv.slice(2);
  const records = readdirSync(artifacts).filter(name => name.endsWith('.json')).map(name => JSON.parse(readFileSync(resolve(artifacts, name), 'utf8')));
  process.stdout.write(`${comment(records, sha)}\n`);
}
