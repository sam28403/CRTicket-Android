export function readBackupChunks(read) {
  const parts = []
  let offset = 0
  for (;;) {
    const chunk = read(offset)
    if (!chunk) return parts.join('')
    parts.push(chunk)
    offset += chunk.length
  }
}
