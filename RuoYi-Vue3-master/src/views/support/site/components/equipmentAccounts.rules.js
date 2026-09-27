export function accountKey(account) {
  return account.credentialId != null ? 'account:' + account.credentialId : 'legacy:' + normalizeAccountName(account.username)
}

export function normalizeAccountName(username) {
  const value = String(username || '').trim()
  return value.toLowerCase() === 'hik' ? 'hik' : value
}

export function displayAccountName(username) {
  return normalizeAccountName(username) || '-'
}

export function displayAccountDescription(value) {
  return String(value || '').replace(/\bhik\b/gi, 'hik')
}

export function orderAccounts(accounts) {
  const rank = username => ({ hik: 0, root: 1 }[String(username || '').trim().toLowerCase()] ?? 2)
  return [...accounts].sort((a, b) => rank(a.username) - rank(b.username))
}

export function accountMutation(serverId, draft) {
  return {
    credentialId: draft.credentialId || null, serverId,
    legacyRegistration: draft.legacyRegistration === true,
    username: normalizeAccountName(draft.username),
    credentialName: draft.credentialName || displayAccountName(draft.username) + '账号',
    password: draft.password || null,
    status: draft.status || '0', isDefault: draft.isDefault || '0',
    purpose: draft.purpose || '', remark: draft.remark || ''
  }
}
