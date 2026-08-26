import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const userStoreSource = readFileSync(new URL('../user.js', import.meta.url), 'utf8')
const avatar = readFileSync(new URL('../../../assets/images/profile.jpg', import.meta.url))

test('users without a configured avatar use the bundled platform portrait', () => {
  assert.ok(userStoreSource.includes("import defAva from '@/assets/images/profile.jpg'"))
  assert.ok(userStoreSource.includes('(isEmpty(avatar)) ? defAva'))
  assert.equal(avatar[0], 0xff)
  assert.equal(avatar[1], 0xd8)
  assert.ok(avatar.length > 10 * 1024)
  assert.ok(avatar.length < 200 * 1024)
})
