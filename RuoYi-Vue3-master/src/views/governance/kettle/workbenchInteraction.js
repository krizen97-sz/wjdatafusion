import { runActive, runFinishing, runNeedsReview } from './runRules.js'

export function executionProtectsDraft(run, pendingSubmission = null) {
  return Boolean(pendingSubmission || runActive(run?.state) || runFinishing(run) || runNeedsReview(run))
}

export function definitionMutationAllowed({ canEdit, busy = false, run = null, pendingSubmission = null }) {
  return Boolean(canEdit && !busy && !executionProtectsDraft(run, pendingSubmission))
}

export function historySelectionAllowed(current, target, pendingSubmission = null) {
  if (!target?.id) return false
  if (!executionProtectsDraft(current, pendingSubmission)) return true
  return Boolean(current?.id && current.id === target.id)
}

export function protectedHistoryRun(history = []) {
  return history.find(run => runActive(run.state) || runNeedsReview(run)) || history.find(run => runFinishing(run)) || null
}

export function sameEditorContext(expected, actual) {
  return Boolean(expected?.id && actual?.id && expected.id === actual.id && expected.revision === actual.revision && expected.documentVersion === actual.documentVersion)
}

export function connectionDraftBelongsTo(owner, definitionId) {
  return Boolean(owner && definitionId && owner === definitionId)
}

export function restoredTaskId(query, definitions) {
  return typeof query === 'string' && definitions.some(item => item.id === query) ? query : ''
}
