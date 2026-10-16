// Used only for a user's toggle in the history editor, never when loading a record.
export function switchHistoryCreditPrice(price, useCredit) {
  if (price === '' || price == null) return price
  const amount = Number(price)
  if (!Number.isFinite(amount)) return price
  const [coefficient, exponent = '0'] = String(amount).split('e')
  return Number(`${coefficient}e${Number(exponent) + (useCredit ? 2 : -2)}`)
}
