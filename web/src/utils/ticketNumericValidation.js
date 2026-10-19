export function ticketPriceError(value, credit = false) {
  const text = String(value ?? '').trim()
  const number = Number(text)
  const format = credit ? /^\d*(?:00|50)$/ : /^(?:\d+(?:\.(?:0+|50*))?|\.50*)$/
  if (number > 0 && number <= 10000000 && format.test(text)) return ''
  return credit
    ? '积分须大于0，且为以00或50结尾的整数'
    : '票价须大于0，且为整数或以.5结尾'
}

export function ticketDistanceError(value) {
  if (value == null || value === '') return ''
  const text = String(value ?? '').trim()
  const number = Number(text)
  return /^\d+$/.test(text) && number > 0 && number <= 10000000
    ? '' : '里程须为正整数'
}
