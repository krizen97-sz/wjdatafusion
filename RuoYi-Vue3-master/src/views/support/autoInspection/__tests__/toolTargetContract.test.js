import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getInspectionToolContractIssue,
  getInspectionToolTargetTypeMap,
  resolveInspectionToolTargetType
} from '../toolTargetContract.js'

test('activity tools resolve to their real queue target forms', () => {
  assert.equal(resolveInspectionToolTargetType('KAFKA_TOPIC_ACTIVITY'), 'KAFKA')
  assert.equal(resolveInspectionToolTargetType('KAFKA_CONSUMER_PROGRESS'), 'KAFKA')
  assert.equal(resolveInspectionToolTargetType('MQTT_TOPIC_ACTIVITY'), 'MQTT')
})

test('all built-in tools have an explicit target contract', () => {
  assert.deepEqual(getInspectionToolTargetTypeMap(), {
    KAFKA_LAG: 'KAFKA',
    KAFKA_TOPIC_ACTIVITY: 'KAFKA',
    KAFKA_CONSUMER_PROGRESS: 'KAFKA',
    MQTT_TOPIC_ACTIVITY: 'MQTT',
    HTTP_COUNT: 'HTTP',
    HTTP_HEALTH: 'HTTP',
    HTTP_API_TEST: 'HTTP',
    DATABASE_QUERY: 'DATABASE',
    FTP_FILE_COUNT: 'FTP',
    SERVER_FILE_COUNT: 'SERVER',
    SERVER_DISK: 'SERVER',
    BIG_DATA_SERVER_DISK: 'BIG_DATA_SERVER',
    TCP_PORT_CHECK: 'SERVER',
    SERVER_SERVICE_STATUS: 'SERVER'
  })
})

test('unknown or mismatched tools fail closed instead of becoming server forms', () => {
  assert.equal(resolveInspectionToolTargetType('UNKNOWN_TOOL'), '')
  assert.match(getInspectionToolContractIssue('UNKNOWN_TOOL'), /尚未配置/)
  assert.equal(resolveInspectionToolTargetType('MQTT_TOPIC_ACTIVITY', 'SERVER'), '')
  assert.match(getInspectionToolContractIssue('MQTT_TOPIC_ACTIVITY', 'SERVER'), /不一致/)
})

test('backend-declared target type can enable a future tool without guessing', () => {
  assert.equal(resolveInspectionToolTargetType('FUTURE_HTTP_TOOL', 'HTTP'), 'HTTP')
  assert.equal(getInspectionToolContractIssue('FUTURE_HTTP_TOOL', 'HTTP'), '')
})
