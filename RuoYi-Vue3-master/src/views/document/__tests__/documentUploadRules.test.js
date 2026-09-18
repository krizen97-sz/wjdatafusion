import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_MAX_UPLOAD_SIZE,
  UPLOAD_LIMIT_OPTIONS,
  documentUploadError,
  formatUploadLimit,
  normalizeUploadLimit,
  validateUploadSelection
} from '../workspace/documentWorkspaceRules.js'

const MB = 1024 ** 2
const archive = (size, name = '资料.zip') => ({ name, size })

test('upload policy distinguishes explicit unlimited from missing or invalid data', () => {
  assert.deepEqual(UPLOAD_LIMIT_OPTIONS.map((option) => option.value), [100, 0])
  for (const value of [0, '0']) {
    assert.equal(normalizeUploadLimit(value), 0)
    assert.equal(formatUploadLimit(value), '无限制')
  }
  for (const value of [undefined, null, '', -1, NaN, Infinity, 'invalid', 1.5]) {
    assert.equal(normalizeUploadLimit(value), DEFAULT_MAX_UPLOAD_SIZE)
  }
  assert.equal(formatUploadLimit(100 * MB), '100 MB')
  assert.equal(normalizeUploadLimit(20 * MB), 20 * MB)
})

test('100 MB policy includes the boundary and rejects one extra byte for ZIP and RAR', () => {
  for (const name of ['资料.zip', '资料.RAR']) {
    assert.equal(validateUploadSelection(archive(100 * MB, name), 100 * MB, 500 * MB), '')
    assert.match(validateUploadSelection(archive(100 * MB + 1, name), 100 * MB, 500 * MB), /单文件上限/)
  }
})

test('unlimited file policy still enforces the individual remaining quota', () => {
  assert.equal(validateUploadSelection(archive(250 * MB), 0, 500 * MB), '')
  assert.equal(validateUploadSelection(archive(250 * MB, '资料.rar'), 0, 250 * MB), '')
  assert.match(validateUploadSelection(archive(250 * MB + 1), 0, 250 * MB), /可用空间不足/)
  assert.match(validateUploadSelection(archive(1), 0, 0), /可用空间不足/)
  assert.match(validateUploadSelection(archive(30 * MB), 20 * MB, 500 * MB), /单文件上限/)
})

test('unlimited does not skip selection validation', () => {
  assert.match(validateUploadSelection(archive(0), 0, 500 * MB), /为空/)
  assert.match(validateUploadSelection(archive(-1), 0, 500 * MB), /无效/)
  assert.match(validateUploadSelection(archive(1, 'file.exe'), 0, 500 * MB), /仅支持/)
  assert.match(validateUploadSelection(archive(101 * MB), undefined, 500 * MB), /单文件上限/)
})

test('proxy rejection is distinguishable from file validation and quota failures', () => {
  assert.match(documentUploadError({ response: { status: 413, data: '<html>413</html>' } }), /服务器大小限制拦截（413）/)
  assert.equal(documentUploadError({ response: { data: { msg: '可用空间不足' } } }), '可用空间不足')
  assert.equal(documentUploadError({ response: { status: 413, data: { msg: '超过当前账号的100MB上传上限' } } }), '超过当前账号的100MB上传上限')
  assert.equal(documentUploadError(new Error('文件已损坏')), '文件已损坏')
  assert.equal(documentUploadError('登录已过期'), '登录已过期')
})
