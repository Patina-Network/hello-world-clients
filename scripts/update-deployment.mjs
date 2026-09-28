import { readFileSync, readdirSync, realpathSync, writeFileSync } from 'node:fs';
import { resolve, relative, isAbsolute, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

function kustomize(args, cwd) {
  return execFileSync('kustomize', args, { cwd, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
}

export function update(repo, artifacts, environment, expected) {
  if (!['production', 'staging'].includes(environment)) throw new Error('Environment must be production or staging');
  repo = realpathSync(repo);
  const records = readdirSync(artifacts).filter(name => name.endsWith('.json')).sort().map(name => JSON.parse(readFileSync(resolve(artifacts, name), 'utf8')));
  if (!expected.length || new Set(expected).size !== expected.length || JSON.stringify(records.map(r => r.language).sort()) !== JSON.stringify([...expected].sort())) {
    throw new Error('Image artifacts must match every selected language exactly once');
  }
  const pending = [];
  for (const record of records) {
    if (!['go', 'rust', 'java'].includes(record.language)) throw new Error('Unknown language');
    const image = `patinanetwork/hello-world-client-${record.language}`;
    if (record.image !== image || !/^sha256:[0-9a-f]{64}$/.test(record.digest)) throw new Error('Invalid image identity or digest');
    if (!/^(?:v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)|(?:staging-)?[0-9a-f]{7})$/.test(record.version) || record.version.startsWith('staging-') !== (environment === 'staging')) {
      throw new Error('Invalid version for deployment environment');
    }
    const target = resolve(repo, 'base', environment, `hello-world-client-${record.language}`, 'kustomization.yaml');
    let path;
    try { path = realpathSync(target); } catch { throw new Error(`Create the client deployment in k8s-manifests first: ${target}`); }
    const rel = relative(repo, path);
    if (rel === '..' || rel.startsWith('../') || isAbsolute(rel)) throw new Error('Deployment path must stay inside the deployment repository');
    const original = readFileSync(path, 'utf8');
    const cwd = dirname(path);
    const rendered = kustomize(['build', '.'], cwd);
    if (!rendered.includes(`image: ${image}:`)) throw new Error(`Expected an existing tagged image for ${image}`);
    pending.push({ path, cwd, original, image, version: record.version });
  }
  try {
    for (const item of pending) {
      kustomize(['edit', 'set', 'image', `${item.image}:${item.version}`], item.cwd);
      const rendered = kustomize(['build', '.'], item.cwd);
      if (!rendered.split('\n').some(line => line.trim().replace(/^- /, '') === `image: ${item.image}:${item.version}`)) {
        throw new Error(`Image update failed for ${item.image}`);
      }
    }
  } catch (error) {
    for (const item of pending) writeFileSync(item.path, item.original);
    throw error;
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [repo, artifacts, environment, expected] = process.argv.slice(2);
  update(repo, artifacts, environment, (expected || '').split(',').filter(Boolean));
}
