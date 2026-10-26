import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeServerUrl } from '../src/serverConfig.ts'

test('explicit B-group HTTP server is supported only in test mode', () => {
  assert.equal(normalizeServerUrl(' http://172.16.2.153:1818/api/ ', 'test'), 'http://172.16.2.153:1818/api')
  assert.throws(() => normalizeServerUrl('http://172.16.2.153:1818/api', 'production'))
  assert.throws(() => normalizeServerUrl('http://example.com/api', 'test'))
})
test('HTTPS production address is configurable', () => {
  assert.equal(normalizeServerUrl('https://spd.example.com/api/', 'production'), 'https://spd.example.com/api')
})
test('credentials and ambiguous API addresses are rejected', () => {
  for (const address of ['https://u:p@example.com/api', 'https://example.com', 'https://example.com/api?token=x', 'https://example.com/api#x', 'not-a-url']) {
    assert.throws(() => normalizeServerUrl(address, 'test'))
  }
})
