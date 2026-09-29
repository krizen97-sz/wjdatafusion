import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { after, test } from 'node:test'

const root = fileURLToPath(new URL('../../', import.meta.url))
const socket = process.env.RYNEW_TEST_SOCKET
assert.match(socket || '', /^\/tmp\/rynew-v484-v4151-[a-zA-Z0-9]+\/mysql\.sock$/, 'Only an owned, isolated MySQL socket is accepted')
const mysql = process.env.MYSQL_TEST_CLIENT || '/usr/local/mysql/bin/mysql'
const args = ['--no-defaults', '--protocol=SOCKET', '--socket=' + socket, '--user=root', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
function execute(sql, db) { return spawnSync(mysql, [...args, ...(db ? [db] : [])], { input: sql, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 }) }
function query(sql, db) {
  const result = execute(sql, db)
  assert.equal(result.status, 0, result.stderr)
  return result.stdout.replace(/\r?\n$/, '')
}
const dataDir = query('SELECT @@datadir')
assert.equal(dataDir.replace(/^\/private/, '').replace(/\/$/, ''), resolve(socket, '../data'))
assert.equal(query('SELECT @@skip_networking'), '1', 'Test server must not listen on TCP')

const upgrade = readFileSync(resolve(root, 'WDF100.0/sql/support_upgrade_20260929_server_credential_v4_8_4_to_v4_15_1_all.sql'), 'utf8')
const verify = readFileSync(resolve(root, 'WDF100.0/sql/support_check_20260929_server_credential_v4_8_4_to_v4_15_1.sql'), 'utf8')
const baseline = execFileSync('git', ['show', 'c0d9efa:WDF100.0/sql/support_v1.sql'], { cwd: root, encoding: 'utf8' })
const system = execFileSync('git', ['show', 'c0d9efa:WDF100.0/sql/ry_20260320.sql'], { cwd: root, encoding: 'utf8' })
const tables = ['sup_site', 'sup_platform', 'sup_server', 'sup_server_credential', 'sup_hardware_asset', 'sup_platform_server_rel', 'sup_platform_asset_rel', 'sup_equipment_room', 'sup_equipment_cabinet', 'sup_equipment_link', 'sys_menu', 'sys_role_menu']
const schema = tables.map(table => {
  const match = (table.startsWith('sys_') ? system : baseline).match(new RegExp('^CREATE TABLE (?:IF NOT EXISTS )?' + table + '\\s*\\([\\s\\S]*?^\\)[^;]*;', 'im'))
  assert.ok(match, 'Missing baseline DDL: ' + table)
  return match[0]
}).join('\n')
const owned = []
function fixture() {
  const db = 'rynew_v484_v4151_test_' + process.pid + '_' + owned.length
  query('CREATE DATABASE `' + db + '` CHARACTER SET utf8mb4')
  owned.push(db)
  query(schema + `
    INSERT INTO sup_server(server_id,site_id,server_name,server_address,ssh_port,os_username,os_password_cipher,status)
    VALUES(1,1,'existing A','192.0.2.1',22,'HIK','fixture-cipher-A','0'),
          (2,1,'existing B','192.0.2.2',55555,'root','fixture-cipher-B','1');
    INSERT INTO sys_menu(menu_id,menu_name,parent_id,menu_type,perms,status)
    VALUES(2203,'existing server menu',2200,'C','support:server:list','0'),
          (2290,'existing plaintext permission',2203,'F','support:credential:viewPlain','0'),
          (8000,'unrelated custom menu',0,'C','custom:list','1');
    INSERT INTO sys_role_menu(role_id,menu_id) VALUES(2,2203),(2,2290),(8,8000);
  `, db)
  return db
}
function snapshot(db) {
  const columns = query("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sup_server' AND column_name<>'root_direct_login' ORDER BY ordinal_position", db).split('\n')
  return {
    servers: query('SELECT ' + columns.map(c => '`' + c + '`').join(',') + ' FROM sup_server ORDER BY server_id', db),
    menus: query('SELECT * FROM sys_menu ORDER BY menu_id', db),
    roles: query('SELECT * FROM sys_role_menu ORDER BY role_id,menu_id', db)
  }
}
function hasFlag(db) { return query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sup_server' AND column_name='root_direct_login'", db) }
function permissionCount(db) { return query("SELECT COUNT(*) FROM sys_menu WHERE perms='support:credential:verify'", db) }
after(() => { for (const db of owned) query('DROP DATABASE `' + db + '`') })

test('fresh and repeated upgrade preserve baseline data, grants and already-saved flags', () => {
  const db = fixture(), before = snapshot(db)
  assert.match(query(upgrade, db), /SUCCESS/)
  const first = snapshot(db)
  assert.equal(first.servers, before.servers)
  assert.equal(first.roles, before.roles)
  assert.ok(first.menus.startsWith(before.menus + '\n'))
  assert.equal(permissionCount(db), '1')
  assert.equal(query('SELECT root_direct_login FROM sup_server ORDER BY server_id', db), '0\n0')
  query('UPDATE sup_server SET root_direct_login=1 WHERE server_id=1', db)
  assert.match(query(upgrade, db), /PRESERVED\s+PRESERVED/)
  assert.deepEqual(snapshot(db), first)
  assert.equal(query('SELECT root_direct_login FROM sup_server ORDER BY server_id', db), '1\n0')
  assert.doesNotMatch(query(verify, db), /MISSING/)
  assert.deepEqual(snapshot(db), first)
})

test('an already-applied original v4.14.0 migration is preserved', () => {
  const db = fixture()
  query(readFileSync(resolve(root, 'WDF100.0/sql/support_upgrade_20260926_server_credential_login_check_v4_14_0.sql'), 'utf8'), db)
  const before = snapshot(db)
  query(upgrade, db)
  assert.deepEqual(snapshot(db), before)
})

test('site-specific parent menu IDs are resolved without stealing IDs or grants', () => {
  const db = fixture()
  query('UPDATE sys_menu SET menu_id=9001 WHERE menu_id=2203', db)
  const before = snapshot(db)
  query(upgrade, db)
  assert.equal(query("SELECT parent_id FROM sys_menu WHERE perms='support:credential:verify'", db), '9001')
  assert.equal(snapshot(db).roles, before.roles)
})

for (const [name, setup, message] of [
  ['missing parent', "DELETE FROM sys_menu WHERE perms='support:server:list'", /exactly one/],
  ['missing SSH port', 'ALTER TABLE sup_server DROP COLUMN ssh_port', /Missing sup_server.ssh_port/],
  ['missing route column', 'ALTER TABLE sys_menu DROP COLUMN route_name', /baseline columns/],
  ['conflicting flag type', 'ALTER TABLE sup_server ADD root_direct_login INT NOT NULL DEFAULT 0', /must be TINYINT/],
  ['invalid saved flag', 'ALTER TABLE sup_server ADD root_direct_login TINYINT NOT NULL DEFAULT 0; UPDATE sup_server SET root_direct_login=2 WHERE server_id=1', /other than 0\/1/],
  ['duplicate permission', "INSERT INTO sys_menu(menu_name,parent_id,menu_type,perms) VALUES('A',2203,'F','support:credential:verify'),('B',2203,'F','support:credential:verify')", /Duplicate/],
  ['wrong permission parent', "INSERT INTO sys_menu(menu_name,parent_id,menu_type,perms) VALUES('A',8000,'F','support:credential:verify')", /different parent/],
  ['custom insert trigger', "CREATE TRIGGER fixture_menu_trigger BEFORE INSERT ON sys_menu FOR EACH ROW SET NEW.remark='custom'", /Custom server\/menu triggers/],
  ['custom storage engine', 'ALTER TABLE sys_role_menu ENGINE=MyISAM', /InnoDB/]
]) {
  test('blocks ' + name + ' before changing business rows or role grants', () => {
    const db = fixture()
    query(setup, db)
    const before = snapshot(db), flagBefore = hasFlag(db), permissionsBefore = permissionCount(db)
    const result = execute(upgrade, db)
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, message)
    assert.deepEqual(snapshot(db), before)
    assert.equal(hasFlag(db), flagBefore)
    assert.equal(permissionCount(db), permissionsBefore)
  })
}

test('incomplete baseline and an unselected database fail closed', () => {
  const db = fixture()
  query('DROP TABLE sys_role_menu', db)
  const failed = execute(upgrade, db)
  assert.notEqual(failed.status, 0)
  assert.match(failed.stderr, /Missing baseline tables/)
  assert.equal(hasFlag(db), '0')
  const noDatabase = execute(upgrade)
  assert.notEqual(noDatabase.status, 0)
  assert.match(noDatabase.stderr, /No database selected/)
})
