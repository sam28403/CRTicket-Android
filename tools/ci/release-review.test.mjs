import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const script = fileURLToPath(new URL('./release-review.mjs', import.meta.url))
const review = (comment, state = 'approved', name = 'release') => ({
  comment, state, environments: [{ name }],
})
const run = history => spawnSync(process.execPath, [script], {
  input: JSON.stringify(history),
  encoding: 'utf8',
})

test('a blank or explicit formal release comment selects release', () => {
  const result = run([review(null), review('release'), review(' ')])
  assert.equal(result.status, 0, result.stderr)
  assert.equal(result.stdout.trim(), 'release')
})

test('a prerelease comment selects prerelease and ignores unrelated reviews', () => {
  const result = run([
    review('release', 'rejected'),
    review('release', 'approved', 'staging'),
    review('  PRErelease  '),
    review('Pre-release'),
  ])
  assert.equal(result.status, 0, result.stderr)
  assert.equal(result.stdout.trim(), 'prerelease')
})

test('missing, invalid and conflicting approvals stop publication', () => {
  for (const [history, message] of [
    [[review('prerelease', 'rejected')], /No approval/],
    [[review('pre-relese')], /Use prerelease/],
    [[review('release'), review('prerelease')], /disagree/],
  ]) {
    const result = run(history)
    assert.equal(result.status, 1)
    assert.equal(result.stdout, '')
    assert.match(result.stderr, message)
  }
})
