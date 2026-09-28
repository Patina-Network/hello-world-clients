import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { execFileSync } from 'node:child_process';
import { metadata, plan } from './release-metadata.mjs';
import { update } from './update-deployment.mjs';

const sha = '1234567' + 'a'.repeat(33);
test('release tags select clients and reject invalid versions', () => {
  assert.deepEqual(metadata('v1.2.3'), [['go', 'rust', 'java'], 'v1.2.3']);
  assert.deepEqual(metadata('rust-v2.0.1'), [['rust'], 'v2.0.1']);
  for (const tag of ['latest', 'v1', 'go-v01.0.0', 'go-v1.0.0;echo pwned', 'js-v1.0.0']) assert.throws(() => metadata(tag));
});
test('main publishes and deploys production SHA', () => {
  assert.deepEqual(plan('push', 'refs/heads/main', sha), { matrix: { language: ['go', 'rust', 'java'] }, version: '1234567', environment: 'production', publish: true, deploy: true });
});
test('PRs never deploy and forks never publish', () => {
  for (const same of [true, false]) {
    const result = plan('pull_request', 'refs/pull/12/merge', sha, same);
    assert.equal(result.version, 'staging-1234567');
    assert.equal(result.environment, 'staging');
    assert.equal(result.publish, same);
    assert.equal(result.deploy, false);
  }
});
test('manual staging and independent production releases', () => {
  const staging = plan('workflow_dispatch', 'refs/heads/feature', sha, false, 'java');
  assert.deepEqual(staging.matrix.language, ['java']);
  assert.equal(staging.environment, 'staging');
  assert.equal(staging.deploy, true);
  const release = plan('push', 'refs/tags/go-v1.2.3', sha);
  assert.deepEqual(release.matrix.language, ['go']);
  assert.equal(release.version, 'v1.2.3');
  assert.equal(release.environment, 'production');
  assert.throws(() => plan('push', 'refs/heads/feature', sha));
  assert.throws(() => plan('push', 'refs/heads/main', '123'));
});
function fixture(t, environment = 'production', version = 'v1.2.3') {
  const root = mkdtempSync(join(tmpdir(), 'client-ci-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const repo = join(root, 'repo'), artifacts = join(root, 'images');
  const target = join(repo, 'base', environment, 'hello-world-client-go', 'kustomization.yaml');
  mkdirSync(dirname(target), { recursive: true }); mkdirSync(artifacts);
  let original = "apiVersion: kustomize.config.k8s.io/v1beta1\nkind: Kustomization\nresources:\n  - deployment.yaml\nimages:\n  - name: patinanetwork/hello-world-client-go\n    newTag: 'old-version' # retained\n\nreplacements: []\n";
  writeFileSync(target, original);
  writeFileSync(join(dirname(target), 'deployment.yaml'), 'apiVersion: v1\nkind: Pod\nmetadata:\n  name: client\nspec:\n  containers:\n    - name: client\n      image: patinanetwork/hello-world-client-go\n');
  execFileSync('kustomize', ['edit', 'set', 'image', 'patinanetwork/hello-world-client-go:old-version'], { cwd: dirname(target) });
  original = readFileSync(target, 'utf8');
  const record = { language: 'go', image: 'patinanetwork/hello-world-client-go', version, digest: 'sha256:' + 'a'.repeat(64) };
  const artifact = join(artifacts, 'go.json');
  writeFileSync(artifact, JSON.stringify(record));
  return { root, repo, artifacts, target, original, record, artifact };
}
test('Kustomize updates selected images and keeps resources', t => {
  for (const [env, version] of [['production', 'v1.2.3'], ['production', '1234567'], ['staging', 'staging-abcdef1']]) {
    const f = fixture(t, env, version);
    update(f.repo, f.artifacts, env, ['go']);
    const result = readFileSync(f.target, 'utf8');
    assert.ok(result.includes('resources:\n- deployment.yaml'));
    assert.ok(!result.includes('old-version'));
    assert.match(execFileSync('kustomize', ['build', dirname(f.target)], { encoding: 'utf8' }), new RegExp(`image: patinanetwork/hello-world-client-go:${version}`));
  }
});
test('unquoted numeric SHA becomes a YAML string', t => {
  const f = fixture(t, 'production', '1234567');
  writeFileSync(f.target, f.original.replace("'old-version'", 'abcdef1'));
  update(f.repo, f.artifacts, 'production', ['go']);
  assert.match(execFileSync('kustomize', ['build', dirname(f.target)], { encoding: 'utf8' }), new RegExp("image: patinanetwork/hello-world-client-go:1234567"));
});
test('rejects incomplete artifacts and invalid image metadata without edits', t => {
  const f = fixture(t);
  for (const expected of [['rust'], ['go', 'java'], []]) assert.throws(() => update(f.repo, f.artifacts, 'production', expected));
  for (const override of [{ digest: 'latest' }, { version: 'staging-abcdef1' }, { image: 'other/image' }]) {
    writeFileSync(f.artifact, JSON.stringify({ ...f.record, ...override }));
    assert.throws(() => update(f.repo, f.artifacts, 'production', ['go']));
  }
  assert.equal(readFileSync(f.target, 'utf8'), f.original);
});
test('missing second deployment never partially updates the first', t => {
  const f = fixture(t);
  writeFileSync(join(f.artifacts, 'rust.json'), JSON.stringify({ ...f.record, language: 'rust', image: 'patinanetwork/hello-world-client-rust' }));
  assert.throws(() => update(f.repo, f.artifacts, 'production', ['go', 'rust']), /Create the client deployment/);
  assert.equal(readFileSync(f.target, 'utf8'), f.original);
});
test('rejects missing image, invalid environment, and symlink escape', t => {
  const f = fixture(t);
  writeFileSync(join(dirname(f.target), 'deployment.yaml'), 'apiVersion: v1\nkind: Pod\nmetadata:\n  name: other\nspec:\n  containers:\n    - name: other\n      image: other/image:test\n');
  assert.throws(() => update(f.repo, f.artifacts, 'production', ['go']), /existing tagged image/);
  assert.throws(() => update(f.repo, f.artifacts, '../escape', ['go']), /Environment/);
  const outside = join(f.root, 'outside.yaml'); writeFileSync(outside, f.original);
  rmSync(f.target); symlinkSync(outside, f.target);
  assert.throws(() => update(f.repo, f.artifacts, 'production', ['go']), /inside the deployment repository/);
});
