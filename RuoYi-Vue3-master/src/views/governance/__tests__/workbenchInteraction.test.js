import test from 'node:test'
import assert from 'node:assert/strict'
import { definitionMutationAllowed, historySelectionAllowed, protectedHistoryRun, sameEditorContext, connectionDraftBelongsTo, restoredTaskId } from '../kettle/workbenchInteraction.js'

const finished = { id: 'old', state: 'SUCCEEDED', finalized: true }

test('all editable entry points share active, finishing and uncertain execution barriers', () => {
  for (const run of [
    { id: 'r', state: 'RUNNING' }, { id: 'r', state: 'STARTING' },
    { id: 'r', state: 'STOPPING' }, { id: 'r', state: 'SUCCEEDED' },
    { id: 'r', state: 'RECOVERY_REQUIRED' }, { id: 'r', state: 'FUTURE_UNKNOWN_STATE' },
    { ...finished, workerStateAvailable: false }
  ]) assert.equal(definitionMutationAllowed({ canEdit: true, run }), false, run.state)
  assert.equal(definitionMutationAllowed({ canEdit: true, pendingSubmission: { requestId: 'uncertain' } }), false)
  assert.equal(definitionMutationAllowed({ canEdit: true, busy: true }), false)
  assert.equal(definitionMutationAllowed({ canEdit: false, run: finished }), false)
  assert.equal(definitionMutationAllowed({ canEdit: true, run: finished }), true)
  assert.equal(definitionMutationAllowed({ canEdit: true, run: { state: 'SUBMISSION_REJECTED' } }), true)
})

test('browsing a successful historical run cannot release an active or unknown execution lock', () => {
  for (const state of ['RUNNING', 'RECOVERY_REQUIRED', 'SUBMISSION_UNKNOWN']) {
    const active = { id: 'active', state }
    assert.equal(historySelectionAllowed(active, finished), false)
    assert.equal(historySelectionAllowed(active, { id: 'active' }), true)
  }
  assert.equal(historySelectionAllowed(null, finished, { requestId: 'pending' }), false)
  assert.equal(historySelectionAllowed(finished, { id: 'another' }), true)
})

test('opening a task locates an older protected run even when its newest run was rejected', () => {
  const active = { id: 'active', state: 'RUNNING' }
  assert.equal(protectedHistoryRun([{ id: 'rejected', state: 'SUBMISSION_REJECTED' }, active, finished]), active)
  assert.equal(protectedHistoryRun([{ id: 'summary-without-final-markers', state: 'SUCCEEDED' }, active]), active)
  assert.equal(protectedHistoryRun([finished]), null)
})

test('a late field result belongs only to its exact task, persisted revision and local graph', () => {
  const request = { id: 'task-a', revision: 3, documentVersion: 7 }
  assert.equal(sameEditorContext(request, { ...request }), true)
  for (const context of [{ ...request, id: 'task-b' }, { ...request, revision: 4 }, { ...request, documentVersion: 8 }, null]) {
    assert.equal(sameEditorContext(request, context), false)
  }
})

test('a connection draft cannot be applied after its owning task changes', () => {
  assert.equal(connectionDraftBelongsTo('task-a', 'task-a'), true)
  assert.equal(connectionDraftBelongsTo('task-a', 'task-b'), false)
  assert.equal(connectionDraftBelongsTo('', ''), false)
})

test('refresh restores only a task in the current visible list', () => {
  const visible = [{ id: 'task-a' }]
  assert.equal(restoredTaskId('task-a', visible), 'task-a')
  assert.equal(restoredTaskId('not-visible', visible), '')
  assert.equal(restoredTaskId(['task-a'], visible), '')
})
