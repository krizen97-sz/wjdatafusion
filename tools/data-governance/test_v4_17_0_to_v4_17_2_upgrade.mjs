import assert from 'node:assert/strict'
import { execFileSync, spawn, spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, existsSync } from 'node:fs'
import { once } from 'node:events'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { after, test } from 'node:test'

const root = fileURLToPath(new URL('../../', import.meta.url))
const dir = mkdtempSync('/tmp/rynew-v4172-upgrade-')
assert.match(dir, /^\/tmp\/rynew-v4172-upgrade-[A-Za-z0-9]+$/)
const data = resolve(dir, 'data')
mkdirSync(data)
const socket = resolve(dir, 'mysql.sock')
const mysqld = process.env.MYSQL_TEST_SERVER || '/usr/local/mysql/bin/mysqld'
const mysql = process.env.MYSQL_TEST_CLIENT || '/usr/local/mysql/bin/mysql'
const admin = resolve(mysql, '../mysqladmin')
const init = spawnSync(mysqld, ['--no-defaults', '--initialize-insecure', '--datadir=' + data], { encoding: 'utf8' })
assert.equal(init.status, 0, init.stderr)
const server = spawn(mysqld, ['--no-defaults', '--datadir=' + data, '--socket=' + socket,
  '--pid-file=' + resolve(dir, 'mysql.pid'), '--log-error=' + resolve(dir, 'mysql.log'),
  '--skip-networking', '--mysqlx=OFF', '--skip-log-bin', '--innodb-buffer-pool-size=64M',
  '--performance-schema=OFF', '--secure-file-priv=' + dir], { stdio: 'ignore' })
let startError
server.once('error', error => { startError = error })
const args = ['--no-defaults', '--protocol=SOCKET', '--socket=' + socket, '--user=root',
  '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
const execute = (sql, db) => spawnSync(mysql, [...args, ...(db ? [db] : [])],
  { input: sql, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 })
function query(sql, db) {
  const result = execute(sql, db)
  assert.equal(result.status, 0, result.stderr)
  return result.stdout.trimEnd()
}
for (let attempt = 0; attempt < 150; attempt++) {
  if (startError) throw startError
  if (server.exitCode != null) throw Error(readFileSync(resolve(dir, 'mysql.log'), 'utf8'))
  if (existsSync(socket) && execute('SELECT 1').status === 0) break
  if (attempt === 149) throw Error('Isolated test MySQL did not become ready')
  await new Promise(resolve => setTimeout(resolve, 200))
}
assert.equal(query('SELECT @@datadir').replace(/^\/private/, '').replace(/\/$/, ''), data)
assert.equal(query('SELECT @@skip_networking'), '1')
console.log('Isolated socket-only MySQL: ' + query('SELECT VERSION()'))
const owned = []
after(async () => {
  try {
    for (const db of owned) query('DROP DATABASE ' + db)
    const exit = once(server, 'exit')
    spawnSync(admin, [...args.filter(arg => !['--batch', '--raw', '--skip-column-names'].includes(arg)), 'shutdown'],
      { encoding: 'utf8' })
    const timer = setTimeout(() => server.kill('SIGTERM'), 10000)
    try { await exit } finally { clearTimeout(timer) }
  } finally {
    if (server.exitCode != null) rmSync(dir, { recursive: true })
  }
})
const source = file => execFileSync('git', ['show', '1b085c9:' + file], { cwd: root, encoding: 'utf8' })
const support = source('WDF100.0/sql/support_v1.sql')
const system = source('WDF100.0/sql/ry_20260320.sql')
const business = ['sup_site', 'sup_server', 'sup_server_scan_credential', 'sup_server_scan_profile', 'sys_menu', 'sys_role_menu']
const schema = business.map(table => {
  const sql = table.startsWith('sys_') ? system : support
  const found = sql.match(new RegExp('^CREATE TABLE (?:IF NOT EXISTS )?' + table + '\\s*\\([\\s\\S]*?^\\)[^;]*;', 'im'))
  assert.ok(found, table)
  return found[0]
}).join('\n')
const read = name => readFileSync(resolve(root, 'WDF100.0/sql', name), 'utf8')
const upgrade = read('data_governance_upgrade_20261026_v4_17_0_to_v4_17_2_all.sql')
const check = read('data_governance_check_20261026_v4_17_0_to_v4_17_2.sql')
const core = read('data_governance_hdi_core_upgrade_20261013.sql')
const connections = read('data_governance_hdi_connections_upgrade_20261025.sql')
const tables = ['dg_hdi_workspace', 'dg_hdi_counter', 'dg_hdi_task', 'dg_hdi_revision', 'dg_hdi_run',
  'dg_hdi_preference', 'dg_hdi_schedule', 'dg_hdi_connection', 'dg_hdi_connection_revision', 'dg_hdi_connection_table']
function create() {
  const db = 'rynew_v4172_delta_test_' + process.pid + '_' + owned.length
  query('CREATE DATABASE ' + db + ' CHARACTER SET utf8mb4')
  owned.push(db)
  return db
}
function fixture() {
  const db = create()
  query(schema + `
    CREATE TABLE sup_auto_inspection_record(record_id BIGINT PRIMARY KEY, result_status CHAR(1), summary TEXT);
    INSERT INTO sup_site(site_id,site_name,site_code) VALUES(1,'现场测试','FIXTURE');
    INSERT INTO sup_server(server_id,site_id,server_name,server_address,ssh_port,root_direct_login,os_password_cipher)
      VALUES(1,1,'fixture server','192.0.2.1',2343,1,'fixture-cipher');
    INSERT INTO sup_server_scan_credential(credential_id,site_id,credential_name,login_mode,password_cipher)
      VALUES(1,1,'authorized pool','PASSWORD_POOL','fixture-pool-cipher');
    INSERT INTO sys_menu(menu_id,menu_name,parent_id,menu_type,perms) VALUES(2203,'server',2200,'C','support:server:list');
    INSERT INTO sys_role_menu VALUES(2,2203);
    INSERT INTO sup_auto_inspection_record VALUES(1,'1','历史执行保持原样');
  `, db)
  return db
}
const snapshot = (db, names) => names.map(table => query('SELECT * FROM ' + table + ' ORDER BY 1,2', db))
const baselineNames = [...business, 'sup_auto_inspection_record']

test('cumulative DDL matches both released sources and only adds ten governed tables', () => {
  assert.ok(upgrade.includes(core.trim()))
  assert.ok(upgrade.includes(connections.trim()))
  assert.doesNotMatch(upgrade, /\b(?:TRUNCATE|DELETE\s+FROM|DROP\s+TABLE|ALTER\s+TABLE)\b/i)
  const db = fixture(), expected = create()
  const before = snapshot(db, baselineNames)
  assert.equal((query(check, db).match(/NOT_APPLIED/g) || []).length, 10)
  query(upgrade, db)
  query(core + connections, expected)
  assert.deepEqual(snapshot(db, baselineNames), before)
  for (const table of tables) {
    const structure = schema => query('SHOW CREATE TABLE ' + table, schema).replace(/AUTO_INCREMENT=\d+ /g, '')
    assert.equal(structure(db), structure(expected), table)
  }
  const result = query(check, db)
  assert.doesNotMatch(result, /STOP:|NOT_APPLIED/)
  assert.equal((result.match(/PRESENT: review definitions below/g) || []).length, 10)
  assert.doesNotMatch(result, /fixture-cipher|fixture-pool-cipher/)
})

test('repeated execution preserves business data, credentials, saved tasks, counters and exact object identities', () => {
  const db = fixture()
  query(upgrade, db)
  query(`
    UPDATE dg_hdi_workspace SET workspace_name='已定制域名称' WHERE workspace_key='platform';
    UPDATE dg_hdi_counter SET next_value=345678 WHERE counter_name='native_runtime';
    INSERT INTO dg_hdi_task(task_id,workspace_id,owner_id,project_code,task_name,created_at,updated_at)
      VALUES(1,1,42,'fixture','已有任务',NOW(3),NOW(3));
    INSERT INTO dg_hdi_revision VALUES(1,1,42,'{}',REPEAT('a',64),'{}',REPEAT('b',64),'{}',NOW(3));
    INSERT INTO dg_hdi_run(run_id,task_id,revision,owner_id,workspace_id,native_flow_id,native_instance_id,
      metadata_id_base,definition_sha256,mode,state,created_at,updated_at)
      VALUES('fixture-run',1,1,42,1,123,456,789,REPEAT('a',64),'RUN','SUCCEEDED',NOW(3),NOW(3));
    INSERT INTO dg_hdi_preference VALUES(42,'fixture','{}',NOW(3));
    INSERT INTO dg_hdi_schedule VALUES(1,42,1,1,'{}',0,NULL,'PAUSED',NOW(3),NOW(3));
    INSERT INTO dg_hdi_connection(connection_id,owner_id,revision,name,db_type,created_at,updated_at)
      VALUES(1,42,1,'已有连接',1,NOW(3),NOW(3));
    INSERT INTO dg_hdi_connection_revision VALUES(1,1,42,'{}','fixture-encrypted-secret',NOW(3));
    INSERT INTO dg_hdi_connection_table(connection_id,owner_id,object_key,schema_name,table_name,table_kind,created_at)
      VALUES(1,42,SHA2(CONCAT('public',CHAR(0),'Foo',CHAR(0),'TABLE'),256),'public','Foo','TABLE',NOW(3)),
            (1,42,SHA2(CONCAT('public',CHAR(0),'foo',CHAR(0),'TABLE'),256),'public','foo','TABLE',NOW(3));
  `, db)
  const before = snapshot(db, [...baselineNames, ...tables])
  query(upgrade, db)
  assert.deepEqual(snapshot(db, [...baselineNames, ...tables]), before)
  assert.equal(query('SELECT COUNT(*) FROM dg_hdi_connection_table', db), '2')
  assert.equal(query("SELECT next_value FROM dg_hdi_counter WHERE counter_name='native_runtime'", db), '345678')
})

test('a previously deployed task-core-only schema is extended without replacing its data', () => {
  const db = fixture()
  query(core, db)
  query("UPDATE dg_hdi_workspace SET workspace_name='before-connections'", db)
  const before = snapshot(db, tables.slice(0, 7))
  query(upgrade, db)
  assert.deepEqual(snapshot(db, tables.slice(0, 7)), before)
  assert.equal(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'dg_hdi_%'", db), '10')
})

test('missing v4.17.0 baseline stops before adding HDI tables', () => {
  const db = fixture()
  query('ALTER TABLE sup_server_scan_credential DROP COLUMN password_cipher', db)
  const result = execute(upgrade, db)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /complete v4.17.0 schema is required/)
  assert.equal(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'dg_hdi_%'", db), '0')
})

test('incompatible partial target table stops without changing original rows', () => {
  const db = fixture()
  query('CREATE TABLE dg_hdi_connection(connection_id BIGINT PRIMARY KEY)', db)
  const before = snapshot(db, baselineNames)
  const result = execute(upgrade, db)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /incompatible existing dg_hdi table/)
  assert.deepEqual(snapshot(db, baselineNames), before)
  assert.equal(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='dg_hdi_workspace'", db), '0')
})

test('custom target triggers require review before initialization writes', () => {
  const db = fixture()
  query(core, db)
  query("CREATE TRIGGER fixture_trigger BEFORE INSERT ON dg_hdi_workspace FOR EACH ROW SET NEW.workspace_name='changed'", db)
  const before = snapshot(db, [...baselineNames, ...tables.slice(0, 7)])
  const result = execute(upgrade, db)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /review custom dg_hdi triggers/)
  assert.deepEqual(snapshot(db, [...baselineNames, ...tables.slice(0, 7)]), before)
})
