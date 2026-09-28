import { test } from 'node:test';
import assert from 'node:assert/strict';
import { comment } from './publish-comment.mjs';

const sha = 'abcdef1' + 'a'.repeat(33);
test('pull request comment lists every published tag', () => {
  const body = comment([
    { language: 'rust', image: 'patinanetwork/hello-world-client-rust', version: 'staging-abcdef1' },
    { language: 'go', image: 'patinanetwork/hello-world-client-go', version: 'staging-abcdef1' },
  ], sha);
  assert.equal(body, [
    'The image has been uploaded to https://hub.docker.com/r/patinanetwork/hello-world-client-go/tags under the following tags:',
    '',
    '- `hello-world-client-go:staging-abcdef1`',
    `- \`hello-world-client-go:sha-${sha}\``,
    '',
    'The image has been uploaded to https://hub.docker.com/r/patinanetwork/hello-world-client-rust/tags under the following tags:',
    '',
    '- `hello-world-client-rust:staging-abcdef1`',
    `- \`hello-world-client-rust:sha-${sha}\``,
  ].join('\n'));
  assert.throws(() => comment([], sha));
  assert.throws(() => comment([{ language: 'go', image: 'other/image', version: 'staging-abcdef1' }], sha));
});
