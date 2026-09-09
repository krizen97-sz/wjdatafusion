// Existing callers keep legacy lower-field defaults; workflows may opt into independent controls.
export function shouldApplyCronUpdate(name, from, preserveFieldValues = false) {
  if (!preserveFieldValues || name === from) return true
  return (name === 'week' && from === 'day') || (name === 'day' && from === 'week')
}
