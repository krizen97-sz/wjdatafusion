export const serverIntakeRowFields = [
  'rowNumber', 'serverName', 'serverAddress', 'sshPort', 'osType',
  'hikPassword', 'rootPassword', 'otherUsername', 'otherPassword', 'status'
]

export function serverIntakePayload(context, checkedRows, reuseExisting = false) {
  return {
    siteId: context.siteId, platformId: context.platformId, reuseExisting,
    rows: checkedRows.map((row) => Object.fromEntries(serverIntakeRowFields.map((field) =>
      [field, row.data[field] ?? null])))
  }
}

export function serverIntakeSignature(context, rows, reuseExisting) {
  return JSON.stringify(serverIntakePayload(context, rows, reuseExisting))
}

export function serverIntakeCounts(rows) {
  return rows.reduce((counts, row) => {
    counts.total++
    if (row.state === 'CREATE') counts.create++
    if (row.state === 'REUSE') counts.reuse++
    if (row.state === 'SKIP') counts.skip++
    if (row.state === 'ERROR') counts.error++
    return counts
  }, { total: 0, create: 0, reuse: 0, skip: 0, error: 0 })
}
