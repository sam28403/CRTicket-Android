// Keep the account receipt if the file picker is cancelled or file export fails.
// A retry in another format must export the same ticket without another insert.
export function createGeneratorSaveFlow({ validate, store, exportFile, readReceipt, writeReceipt, clear }) {
  let busy = false
  return async (format, ticket, owner) => {
    if (busy) return { busy: true }
    busy = true
    try {
      const error = validate(ticket)
      if (error) throw new Error(error)
      if (!owner?.id) throw new Error('无法读取默认用户资料')
      const fingerprint = JSON.stringify([owner.id, Object.keys(ticket).sort().map(key => [key, ticket[key]])])
      if (readReceipt() !== fingerprint) {
        const result = await store(ticket, owner)
        if (!result.success) throw new Error(result.message || '车票保存失败')
        writeReceipt(fingerprint)
      }
      const exported = await exportFile(format, ticket)
      if (exported) clear()
      return { stored: true, exported }
    } finally {
      busy = false
    }
  }
}
