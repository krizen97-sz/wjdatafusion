import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { after, test } from 'node:test'

const root = fileURLToPath(new URL('../../', import.meta.url))
const socket = process.env.RYNEW_TEST_SOCKET
assert.match(socket || '', /^\/tmp\/rynew-v4151-v4160-[a-zA-Z0-9]+\/mysql\.sock$/, 'Only an owned, isolated MySQL socket is accepted')
const mysql = process.env.MYSQL_TEST_CLIENT || '/usr/local/mysql/bin/mysql'
const args = ['--no-defaults', '--protocol=SOCKET', '--socket=' + socket, '--user=root', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
function query(sql, db) {
  const result = spawnSync(mysql, [...args, ...(db ? [db] : [])], { input: sql, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 })
  assert.equal(result.status, 0, result.stderr)
  return result.stdout.replace(/\r?\n$/, '')
}
assert.equal(query('SELECT @@datadir').replace(/^\/private/, '').replace(/\/$/, ''), resolve(socket, '../data'))
assert.equal(query('SELECT @@skip_networking'), '1', 'Test server must not listen on TCP')
console.log('Isolated MySQL version: ' + query('SELECT VERSION()'))

const upgrade = readFileSync(resolve(root, 'WDF100.0/sql/support_upgrade_20260929_server_credential_v4_15_1_to_v4_16_0_all.sql'), 'utf8')
const check = readFileSync(resolve(root, 'WDF100.0/sql/support_check_20260929_server_credential_v4_15_1_to_v4_16_0.sql'), 'utf8')
const original = readFileSync(resolve(root, 'WDF100.0/sql/support_upgrade_20260929_server_credential_scan_v4_16_0.sql'), 'utf8')
const baseline = execFileSync('git', ['show', 'b375865:WDF100.0/sql/support_v1.sql'], { cwd: root, encoding: 'utf8' })
const system = execFileSync('git', ['show', 'b375865:WDF100.0/sql/ry_20260320.sql'], { cwd: root, encoding: 'utf8' })
const tables = ['sup_site', 'sup_platform', 'sup_server', 'sup_server_credential', 'sup_hardware_asset', 'sup_platform_server_rel', 'sup_platform_asset_rel', 'sup_equipment_room', 'sup_equipment_cabinet', 'sup_equipment_link', 'sys_menu', 'sys_role_menu']
const schema = tables.map(table => {
  const match = (table.startsWith('sys_') ? system : baseline).match(new RegExp('^CREATE TABLE (?:IF NOT EXISTS )?' + table + '\\s*\\([\\s\\S]*?^\\)[^;]*;', 'im'))
  assert.ok(match, 'Missing baseline DDL: ' + table)
  return match[0]
}).join('\n')
const owned = []
function fixture() {
  const db = 'rynew_v4151_v4160_test_' + process.pid + '_' + owned.length
  query('CREATE DATABASE `' + db + '` CHARACTER SET utf8mb4')
  owned.push(db)
  query(schema + `
    INSERT INTO sup_site(site_id,site_name,site_code) VALUES(1,'fixture site','FIXTURE');
    INSERT INTO sup_server(server_id,site_id,server_name,server_address,ssh_port,root_direct_login,os_password_cipher)
    VALUES(1,1,'fixture server','192.0.2.1',2343,1,'fixture-old-cipher');
    INSERT INTO sup_server_credential(server_id,credential_name,username,password_cipher)
    VALUES(1,'fixture credential','hik','fixture-old-credential-cipher');
    INSERT INTO sys_menu(menu_id,menu_name,parent_id,menu_type,perms)
    VALUES(2203,'server menu',2200,'C','support:server:list'),(8000,'custom menu',0,'C','custom:list');
    INSERT INTO sys_role_menu(role_id,menu_id) VALUES(2,2203),(8,8000);
  `, db)
  return db
}
function snapshot(db, names) {
  return Object.fromEntries(names.map(table => [table, query('SELECT * FROM `' + table + '` ORDER BY 1,2', db)]))
}
const newTables = ['sup_server_scan_credential', 'sup_server_scan_profile']
after(() => { for (const db of owned) query('DROP DATABASE `' + db + '`') })

test('packaged statements exactly match the released v4.16.0 migration', () => {
  const withoutComments = text => text.replace(/^--.*$/gm, '').trim()
  assert.equal(withoutComments(upgrade), withoutComments(original))
})

test('first and repeated execution preserve old rows, flags, credentials, roles and scan records', () => {
  const db = fixture(), before = snapshot(db, tables)
  const precheck = query(check, db)
  assert.doesNotMatch(precheck, /STOP/)
  assert.equal((precheck.match(/NOT_APPLIED/g) || []).length, 3)
  assert.deepEqual(snapshot(db, tables), before)
  query(upgrade, db)
  assert.deepEqual(snapshot(db, tables.filter(t => t !== 'sys_menu')), Object.fromEntries(Object.entries(before).filter(([t]) => t !== 'sys_menu')))
  assert.ok(snapshot(db, ['sys_menu']).sys_menu.startsWith(before.sys_menu + '\n'))
  assert.equal(query("SELECT COUNT(*) FROM sys_menu WHERE perms='support:server:scan'", db), '1')
  assert.equal(query('SELECT COUNT(*) FROM sup_server_scan_credential', db), '0')
  assert.equal(query('SELECT COUNT(*) FROM sup_server_scan_profile', db), '0')
  query(`INSERT INTO sup_server_scan_credential(site_id,credential_name,login_mode,hik_password_cipher,root_password_cipher)
    VALUES(1,'fixture pool','HIK_SU_ROOT','fixture-hik-cipher','fixture-root-cipher');
    INSERT INTO sup_server_scan_profile(server_id,site_id,host_name,os_name,os_version,kernel_version,detected_ports,scan_time)
    VALUES(1,1,'fixture-host','CentOS','7.9','3.10','22,2343,55555',NOW());`, db)
  const retained = snapshot(db, [...tables, ...newTables])
  query(upgrade, db)
  const postcheck = query(check, db)
  assert.doesNotMatch(postcheck, /STOP|NOT_APPLIED|fixture-old-cipher|fixture-root-cipher/)
  assert.match(postcheck, /scan_permission\s+1\s+PASS/)
  assert.equal((postcheck.match(/CASCADE/g) || []).length, 2)
  assert.deepEqual(snapshot(db, [...tables, ...newTables]), retained)
  assert.equal(query('START TRANSACTION; DELETE FROM sup_server WHERE server_id=1; SELECT COUNT(*) FROM sup_server_scan_profile; ROLLBACK;', db), '0')
  assert.equal(query('START TRANSACTION; DELETE FROM sup_site WHERE site_id=1; SELECT COUNT(*) FROM sup_server_scan_credential; ROLLBACK;', db), '0')
  assert.deepEqual(snapshot(db, [...tables, ...newTables]), retained)
})

test('the original single-version migration already applied is preserved', () => {
  const db = fixture()
  query(original, db)
  const before = snapshot(db, [...tables, ...newTables])
  query(upgrade, db)
  assert.deepEqual(snapshot(db, [...tables, ...newTables]), before)
})

test('permission resolves a custom server menu ID without changing role grants', () => {
  const db = fixture()
  query('UPDATE sys_menu SET menu_id=9001 WHERE menu_id=2203', db)
  const before = snapshot(db, ['sys_role_menu'])
  query(upgrade, db)
  assert.equal(query("SELECT parent_id FROM sys_menu WHERE perms='support:server:scan'", db), '9001')
  assert.deepEqual(snapshot(db, ['sys_role_menu']), before)
})

for (const [name, setup, message] of [
  ['missing root policy', 'ALTER TABLE sup_server DROP COLUMN root_direct_login', /STOP: missing prerequisite column/],
  ['missing parent menu', "DELETE FROM sys_menu WHERE perms='support:server:list'", /STOP: exactly one server C menu/],
  ['duplicate parent menu', "INSERT INTO sys_menu(menu_name,menu_type,perms) VALUES('duplicate','C','support:server:list')", /STOP: exactly one server C menu/],
  ['wrong permission parent', "INSERT INTO sys_menu(menu_name,parent_id,menu_type,perms) VALUES('wrong',8000,'F','support:server:scan')", /STOP: duplicate or incompatible scan permission/],
  ['custom menu trigger', "CREATE TRIGGER fixture_trigger BEFORE INSERT ON sys_menu FOR EACH ROW SET NEW.remark='custom'", /STOP: DBA review of custom triggers/]
]) {
  test('read-only precheck reports ' + name + ' without mutation', () => {
    const db = fixture()
    query(setup, db)
    const before = snapshot(db, tables)
    assert.match(query(check, db), message)
    assert.deepEqual(snapshot(db, tables), before)
  })
}
