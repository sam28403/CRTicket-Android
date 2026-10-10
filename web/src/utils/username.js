export function normalizeUsername(value) {
    if (typeof value !== 'string') throw new Error('请输入用户名')
    const username = value.trim()
    if (!username || username.length > 64 || /[\u0000-\u001f\u007f]/.test(username)) {
        throw new Error('用户名须为 1–64 个字符，不能包含换行或控制字符')
    }
    return username
}
