import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { pbkdf2Sync, createDecipheriv } from 'node:crypto'
import { encryptBackup, decryptBackup } from '../utils/backupEncryption.js'
import { parseAppBackup } from '../utils/appBackup.js'

const fixture = JSON.parse(readFileSync(new URL(
  '../../../app/src/androidTest/assets/backup-encryption-v1.json', import.meta.url,
), 'utf8'))
const encrypted = JSON.stringify(fixture.encrypted)

test('permanent v1 fixture remains readable independently of app version and device', async () => {
  assert.equal(await decryptBackup(encrypted, fixture.password), fixture.plain)
  const restored = parseAppBackup(await decryptBackup(encrypted, fixture.password))
  assert.equal(restored.settings.profile.username, 'Portable backup fixture')
  assert.equal(restored.settings.storage['crticket-train-query-history'], '["G123"]')
})

test('new exports decrypt with an independent Node implementation and fresh randomness', async () => {
  const first = JSON.parse(await encryptBackup(fixture.plain, fixture.password))
  const second = JSON.parse(await encryptBackup(fixture.plain, fixture.password))
  assert.notEqual(first.kdf.salt, second.kdf.salt)
  assert.notEqual(first.cipher.iv, second.cipher.iv)
  assert.notEqual(first.data, second.data)
  assert.equal(JSON.stringify(first).includes('Portable backup fixture'), false)
  const key = pbkdf2Sync(Buffer.from(fixture.password, 'utf8'),
    Buffer.from(first.kdf.salt, 'base64'), first.kdf.iterations, 32, 'sha256')
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(first.cipher.iv, 'base64'))
  decipher.setAAD(Buffer.from('CRTicketEncryptedBackup:1'))
  const bytes = Buffer.from(first.data, 'base64')
  decipher.setAuthTag(bytes.subarray(-16))
  const plain = Buffer.concat([decipher.update(bytes.subarray(0, -16)), decipher.final()])
  assert.equal(plain.toString('utf8'), fixture.plain)
})

test('wrong password and tampering fail authentication before returning any plaintext', async () => {
  await assert.rejects(decryptBackup(encrypted, 'wrong password'), { code: 'backup_crypto_failed' })
  for (const field of ['data', 'salt', 'iv']) {
    const value = structuredClone(fixture.encrypted)
    const owner = field === 'salt' ? value.kdf : field === 'iv' ? value.cipher : value
    const bytes = Buffer.from(owner[field], 'base64')
    bytes[0] ^= 1
    owner[field] = bytes.toString('base64')
    await assert.rejects(decryptBackup(JSON.stringify(value), fixture.password),
      { code: 'backup_crypto_failed' })
  }
})

test('unsupported versions and hostile parameters are rejected without key derivation', async () => {
  for (const iterations of [-1, 0, 99999, 2000001, 1e12, 600000.5, '600000']) {
    const value = structuredClone(fixture.encrypted)
    value.kdf.iterations = iterations
    await assert.rejects(decryptBackup(JSON.stringify(value), fixture.password),
      { code: 'backup_crypto_invalid' })
  }
  const value = { ...fixture.encrypted, version: 999 }
  await assert.rejects(decryptBackup(JSON.stringify(value), fixture.password),
    { code: 'backup_crypto_version' })
  await assert.rejects(encryptBackup(fixture.plain, 'short'), { code: 'backup_password_invalid' })
})

test('all historical plaintext formats still bypass encryption without a password', async () => {
  for (const text of ['[]', '{"app":"CRTicket","schemaVersion":1,"tickets":[]}', fixture.plain]) {
    assert.equal(await decryptBackup(text), text)
    parseAppBackup(await decryptBackup(text))
  }
})

test('password whitespace and non-ASCII characters are preserved exactly', async () => {
  const password = '  中文密码 e\u0301  '
  const text = await encryptBackup(fixture.plain, password)
  assert.equal(await decryptBackup(text, password), fixture.plain)
  await assert.rejects(decryptBackup(text, password.trim()), { code: 'backup_crypto_failed' })
  await assert.rejects(decryptBackup(text, password.normalize('NFC')), { code: 'backup_crypto_failed' })
})
