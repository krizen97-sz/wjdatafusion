import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { after, test } from 'node:test'

const root = fileURLToPath(new URL('../../', import.meta.url))
const socket = process.env.RYNEW_TEST_SOCKET
assert.match(socket || '', /^\/tmp\/rynew-v4170-pool-[a-zA-Z0-9]+\/mysql\.sock$/)
const mysql = '/usr/local/mysql/bin/mysql'
const args = ['--no-defaults', '--protocol=SOCKET', '--socket=' + socket, '--user=root', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
function execute(sql, db) { return spawnSync(mysql, [...args, ...(db ? [db] : [])], { input: sql, encoding: 'utf8' }) }
function query(sql, db) {
  const result = execute(sql, db)
  assert.equal(result.status, 0, result.stderr)
  return result.stdout.trimEnd()
}
assert.equal(query('SELECT @@datadir').replace(/^\/private/, '').replace(/\/$/, ''), resolve(socket, '../data'))
assert.equal(query('SELECT @@skip_networking'), '1')
console.log('Isolated MySQL: ' + query('SELECT VERSION()'))
const source = file => execFileSync('git', ['show', 'cd08046:' + file], { cwd: root, encoding: 'utf8' })
const support = source('WDF100.0/sql/support_v1.sql')
const system = source('WDF100.0/sql/ry_20260320.sql')
const original = source('WDF100.0/sql/support_upgrade_20260929_server_credential_scan_v4_16_0.sql')
const upgrade = readFileSync(resolve(root, 'WDF100.0/sql/support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql'), 'utf8')
const tables = ['sup_site', 'sup_server', 'sys_menu', 'sys_role_menu']
const schema = tables.map(table => {
  const match = (table.startsWith('sys_') ? system : support).match(new RegExp('^CREATE TABLE (?:IF NOT EXISTS )?' + table + '\\s*\\([\\s\\S]*?^\\)[^;]*;', 'im'))
  assert.ok(match, table)
  return match[0]
}).join('\n')
const owned = []
function fixture() {
  const db = 'rynew_pool_test_' + process.pid + '_' + owned.length
  query('CREATE DATABASE ' + db + ' CHARACTER SET utf8mb4')
  owned.push(db)
  query(schema + original + `
    INSERT INTO sup_site(site_id,site_name,site_code) VALUES(1,'fixture','FIXTURE');
    INSERT INTO sup_server(server_id,site_id,server_name,server_address,ssh_port,root_direct_login,os_password_cipher)
    VALUES(1,1,'fixture','192.0.2.1',2343,1,'old-server-cipher');
    INSERT INTO sys_role_menu VALUES(2,2203);
    INSERT INTO sup_server_scan_credential(credential_id,site_id,credential_name,login_mode,hik_password_cipher,root_password_cipher,create_by,create_time)
    VALUES(1,1,'direct','ROOT_DIRECT','','root-cipher','fixture','2026-09-29 10:00:00'),
          (2,1,'pair','HIK_SU_ROOT','hik-cipher','second-root-cipher','fixture','2026-09-29 11:00:00');
  `, db)
  return db
}
function snapshot(db, names) { return names.map(table => query('SELECT * FROM ' + table + ' ORDER BY 1', db)) }
after(() => { for (const db of owned) query('DROP DATABASE ' + db) })
test('splits paired pool without decrypting, losing audit data or changing old servers and grants', () => {
  const db = fixture(), before = snapshot(db, tables)
  assert.match(query(upgrade, db), /SUCCESS/)
  assert.equal(query('SELECT credential_id,password_cipher FROM sup_server_scan_credential ORDER BY credential_id', db), '1\troot-cipher\n2\tsecond-root-cipher\n3\thik-cipher')
  assert.equal(query('SELECT create_by,create_time FROM sup_server_scan_credential WHERE credential_id=3', db), 'fixture\t2026-09-29 11:00:00')
  assert.equal(query('SELECT hik_password_cipher,root_password_cipher FROM sup_server_scan_credential WHERE credential_id=2', db), 'hik-cipher\tsecond-root-cipher')
  assert.deepEqual(snapshot(db, tables), before)
})
test('repeat migration preserves edits and does not resurrect deleted split passwords', () => {
  const db = fixture()
  query(upgrade, db)
  query("UPDATE sup_server_scan_credential SET password_cipher='edited-cipher' WHERE credential_id=2; DELETE FROM sup_server_scan_credential WHERE credential_id=3", db)
  const before = snapshot(db, [...tables, 'sup_server_scan_credential'])
  query(upgrade, db)
  assert.deepEqual(snapshot(db, [...tables, 'sup_server_scan_credential']), before)
})
test('fresh initializer includes the independent password column', () => {
  for (const name of ['support_v1.sql', 'support_deploy_all.sql']) {
    const sql = readFileSync(resolve(root, 'WDF100.0/sql/' + name), 'utf8')
    const table = sql.match(/CREATE TABLE IF NOT EXISTS sup_server_scan_credential \([\s\S]*?;/)[0]
    assert.match(table, /password_cipher VARCHAR\(1024\) NOT NULL DEFAULT '' COMMENT 'Independent/)
  }
})
test('incompatible column and incomplete legacy pool report errors without changing pool records', () => {
  for (const [setup, message] of [
    ['ALTER TABLE sup_server_scan_credential ADD password_cipher INT DEFAULT 0', /Incompatible password_cipher/],
    ["UPDATE sup_server_scan_credential SET hik_password_cipher='' WHERE credential_id=2", /Incomplete legacy pool/]
  ]) {
    const db = fixture()
    query(setup, db)
    const before = query('SELECT credential_id,login_mode,hik_password_cipher,root_password_cipher FROM sup_server_scan_credential ORDER BY credential_id', db)
    const result = execute(upgrade, db)
    assert.notEqual(result.status, 0); assert.match(result.stderr, message)
    assert.equal(query('SELECT credential_id,login_mode,hik_password_cipher,root_password_cipher FROM sup_server_scan_credential ORDER BY credential_id', db), before)
  }
})
test('failed data conversion rolls back both insertion and backfill', () => {
  const db = fixture()
  query("ALTER TABLE sup_server_scan_credential ADD password_cipher VARCHAR(1024) NOT NULL DEFAULT '', ADD CONSTRAINT fixture_constraint CHECK (password_cipher <> 'second-root-cipher')", db)
  const before = snapshot(db, ['sup_server_scan_credential'])
  const result = execute(upgrade, db)
  assert.notEqual(result.status, 0)
  assert.deepEqual(snapshot(db, ['sup_server_scan_credential']), before)
})
