// Portable envelope v1. Keep this reader when adding future formats or app versions.
export const BACKUP_FORMAT = 'CRTicketEncryptedBackup'
export const MAX_BACKUP_BYTES = 20 * 1024 * 1024
export const MAX_BACKUP_FILE_BYTES = 28 * 1024 * 1024
const ITERATIONS = 600000
const encoder = new TextEncoder()
const aad = encoder.encode(`${BACKUP_FORMAT}:1`)

export class BackupEncryptionError extends Error {
  constructor(code) {
    super(code)
    this.code = code
  }
}
const fail = code => { throw new BackupEncryptionError(code) }

function cryptoApi() {
  if (!globalThis.crypto?.subtle) fail('backup_crypto_unavailable')
  return globalThis.crypto
}

function base64(bytes) {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 8192) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 8192))
  }
  return btoa(binary)
}

function unbase64(text, length) {
  if (typeof text !== 'string' || text.length > MAX_BACKUP_FILE_BYTES
      || text.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(text)) {
    fail('backup_crypto_invalid')
  }
  let bytes
  try {
    bytes = Uint8Array.from(atob(text), char => char.charCodeAt(0))
  } catch {
    fail('backup_crypto_invalid')
  }
  if (length && bytes.length !== length) fail('backup_crypto_invalid')
  return bytes
}

async function deriveKey(password, salt, iterations) {
  if (typeof password !== 'string' || !password.length || password.length > 1024) {
    fail('backup_password_invalid')
  }
  const api = cryptoApi()
  // UTF-8, with no trimming or Unicode normalization, is part of the v1 format.
  const bytes = encoder.encode(password)
  try {
    const material = await api.subtle.importKey('raw', bytes, 'PBKDF2', false, ['deriveKey'])
    return await api.subtle.deriveKey(
      { name: 'PBKDF2', hash: 'SHA-256', salt, iterations },
      material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'],
    )
  } finally {
    bytes.fill(0)
  }
}

export function isEncryptedBackup(value) {
  return value !== null && typeof value === 'object' && value.format === BACKUP_FORMAT
}

export async function encryptBackup(text, password) {
  if (typeof password !== 'string' || [...password].length < 8 || password.length > 1024) {
    fail('backup_password_invalid')
  }
  const plain = encoder.encode(text)
  if (plain.length > MAX_BACKUP_BYTES) fail('backup_crypto_size')
  const api = cryptoApi()
  const salt = api.getRandomValues(new Uint8Array(16))
  const iv = api.getRandomValues(new Uint8Array(12))
  try {
    const key = await deriveKey(password, salt, ITERATIONS)
    const ciphertext = await api.subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 }, key, plain,
    )
    return JSON.stringify({
      format: BACKUP_FORMAT,
      version: 1,
      kdf: { name: 'PBKDF2-HMAC-SHA256', iterations: ITERATIONS, salt: base64(salt) },
      cipher: { name: 'AES-256-GCM', iv: base64(iv) },
      data: base64(new Uint8Array(ciphertext)),
    })
  } finally {
    plain.fill(0)
  }
}

export async function decryptBackup(text, password) {
  if (encoder.encode(text).length > MAX_BACKUP_FILE_BYTES) fail('backup_crypto_size')
  let value
  try { value = JSON.parse(text) } catch { fail('backup_crypto_invalid') }
  if (!isEncryptedBackup(value)) {
    if (encoder.encode(text).length > MAX_BACKUP_BYTES) fail('backup_crypto_size')
    return text // Legacy arrays and schemaVersion 1/2 remain readable without a password.
  }
  if (value.version !== 1) fail('backup_crypto_version')
  if (value.kdf?.name !== 'PBKDF2-HMAC-SHA256' || value.cipher?.name !== 'AES-256-GCM'
      || !Number.isSafeInteger(value.kdf.iterations)
      || value.kdf.iterations < 100000 || value.kdf.iterations > 2000000) {
    fail('backup_crypto_invalid')
  }
  const salt = unbase64(value.kdf.salt, 16)
  const iv = unbase64(value.cipher.iv, 12)
  const data = unbase64(value.data)
  if (data.length < 16 || data.length > MAX_BACKUP_BYTES + 16) fail('backup_crypto_invalid')
  const key = await deriveKey(password, salt, value.kdf.iterations)
  let plain
  try {
    plain = new Uint8Array(await cryptoApi().subtle.decrypt(
      { name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 }, key, data,
    ))
  } catch {
    fail('backup_crypto_failed')
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(plain)
  } finally {
    plain.fill(0)
  }
}
