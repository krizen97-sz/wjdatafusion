import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { lstatSync, readFileSync, realpathSync, writeFileSync } from 'node:fs'
import { basename, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { withIsolatedMysql } from './isolated-mysql.mjs'
import { buildSourceSchema, output as schemaOutput, root, sources, sourceFingerprint, version } from './schema-latest.mjs'

export const output = resolve(root, 'WDF100.0/sql/rynew_init_latest.sql')
export const seedSources = [
  'document_management_v3_9_20_ruoyi_file_permission_20260816_prepare.sql',
  'document_management_v3_9_20_ruoyi_file_permission_20260816_finalize.sql',
  'document_management_v3_9_21_document_role_20260816.sql',
  'data_governance_upgrade_20260909_v4_5_0.sql',
  'support_upgrade_20260826_menu_icons_v3_14_3.sql'
]
export const seedTables = [
  'sys_dept', 'sys_post', 'sys_role', 'sys_menu', 'sys_dict_type', 'sys_dict_data', 'sys_config',
  'sys_user', 'sys_user_role', 'sys_user_post', 'sys_role_menu', 'sys_job',
  'sup_auto_inspection_tool', 'ipam_setting', 'doc_user_quota', 'kb_space',
  'dg_hdi_workspace', 'dg_hdi_counter'
]
export const bcryptPattern = /^\$2[aby]\$1[0-4]\$[./A-Za-z0-9]{53}$/
const date = '2026-11-06 00:00:00'
const seedText = name => readFileSync(resolve(root, 'WDF100.0/sql', name), 'utf8')
const sha256 = value => createHash('sha256').update(value).digest('hex')

// Hex literals avoid dependence on the installation session's backslash escaping.
export function sqlLiteral(value) {
  if (value === null) return 'NULL'
  if (typeof value === 'number') { assert.ok(Number.isFinite(value)); return String(value) }
  return value === '' ? "''" : "CONVERT(0x" + Buffer.from(String(value), 'utf8').toString('hex') + ' USING utf8mb4)'
}

async function readRows(mysql, database, table) {
  assert.ok(seedTables.includes(table))
  const columns = (await mysql.query("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" +
    database + "' AND TABLE_NAME='" + table + "' ORDER BY ORDINAL_POSITION")).split('\n')
  const pairs = columns.map(column => "'" + column + "',`" + column + '`').join(',')
  const primary = await mysql.query("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='" +
    database + "' AND TABLE_NAME='" + table + "' AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX")
  const order = primary.split('\n').map(column => '`' + column + '`').join(',')
  const result = await mysql.query('SELECT JSON_OBJECT(' + pairs + ') FROM `' + table + '` ORDER BY ' + order, database)
  const rows = result ? result.split('\n').map(row => JSON.parse(row)) : []
  for (const row of rows) {
    for (const column of ['create_time', 'update_time', 'created_at', 'updated_at']) {
      if (Object.hasOwn(row, column)) row[column] = date
    }
  }
  return { table, columns, rows }
}

export async function buildSeeds(mysql) {
  const database = await buildSourceSchema(mysql)
  for (const name of seedSources) await mysql.query(seedText(name), database)
  // Only the owned disposable source database is normalized. No application configuration is read.
  await mysql.query(`
    DELETE FROM sys_user_role; DELETE FROM sys_user_post; DELETE FROM sys_role_dept;
    DELETE FROM sys_user; DELETE FROM sys_dept; DELETE FROM sys_post; DELETE FROM sys_role_menu;
    INSERT INTO sys_dept(dept_id,parent_id,ancestors,dept_name,order_num,status,del_flag,create_by)
      VALUES(100,0,'0','平台管理',0,'0','0','admin');
    INSERT INTO sys_post(post_id,post_code,post_name,post_sort,status,create_by)
      VALUES(1,'admin','平台管理员',1,'0','admin');
    INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,sex,password,status,del_flag,create_by)
      VALUES(1,100,'admin','平台管理员','00','2','INSTALL_TIME_HASH','0','0','admin');
    INSERT INTO sys_user_role VALUES(1,1); INSERT INTO sys_user_post VALUES(1,1);
    UPDATE sys_role SET data_scope='5' WHERE role_key='common';
    INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,status,del_flag,create_by)
      SELECT 101,'现场融合与巡检','datafusion',3,'1','0','0','admin'
      WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE role_key='datafusion');
    UPDATE sys_config SET config_value='',remark='由管理员逐人设置独立密码，不提供通用默认口令'
      WHERE config_key='sys.user.initPassword';
    DELETE FROM sys_menu WHERE path REGEXP '^https?://';
    UPDATE sys_menu SET visible='1',status='1' WHERE menu_id=2206 OR parent_id=2206;
    DELETE FROM sys_job WHERE invoke_target<>'documentPermissionExpiryTask.expire';
    UPDATE sys_job SET job_id=1,status='0',job_group='SYSTEM' WHERE invoke_target='documentPermissionExpiryTask.expire';
    DELETE FROM doc_user_quota;
    INSERT INTO doc_user_quota(user_id,quota_bytes,max_upload_bytes,create_by,update_by)
      VALUES(1,104857600,104857600,'admin','admin');
    UPDATE kb_space SET space_name='默认知识空间';
    INSERT INTO sys_role_menu(role_id,menu_id) SELECT 1,menu_id FROM sys_menu;
    INSERT INTO sys_role_menu(role_id,menu_id)
      WITH RECURSIVE grants(role_key,menu_id) AS (
        SELECT 'document',menu_id FROM sys_menu WHERE menu_id=2500 AND status='0'
        UNION ALL SELECT 'datafusion',menu_id FROM sys_menu WHERE menu_id IN (2200,2300) AND status='0'
        UNION ALL SELECT parent.role_key,child.menu_id FROM sys_menu child
          JOIN grants parent ON child.parent_id=parent.menu_id WHERE child.status='0'
      ) SELECT r.role_id,g.menu_id FROM grants g JOIN sys_role r ON r.role_key=g.role_key;
  `, database)
  const seeds = []
  for (const table of seedTables) seeds.push(await readRows(mysql, database, table))
  return seeds
}

function guard(label, invalid) {
  return ["INSERT INTO rynew_init_guard VALUES('" + label + "');",
    "INSERT INTO rynew_init_guard SELECT '" + label + "' WHERE " + invalid + ';'].join('\n')
}

export function renderInit(seeds) {
  assert.deepEqual(seeds.map(seed => seed.table), seedTables)
  const schema = readFileSync(schemaOutput, 'utf8')
  return [
    '-- RYNEW ' + version + ' complete fresh-database initialization (2026-11-06).',
    '-- EMPTY DATABASE ONLY. Not an upgrade. Use a new batch connection, never --force.',
    '-- Set @rynew_admin_password_hash to your installation-specific BCrypt hash first.',
    '-- No universal bootstrap password, source users, business rows or live credentials.',
    '-- Prepare a private runnable copy: node tools/database/init-latest.mjs --prepare --password-file FILE --output FILE',
    '-- Source SHA-256 (schema snapshot): ' + sha256(schema),
    ...sources.map(name => '-- ' + sourceFingerprint(name) + '  ' + name),
    ...seedSources.map(name => '-- ' + sha256(seedText(name)) + '  ' + name),
    '-- Seed policy SHA-256: ' + sha256(readFileSync(fileURLToPath(import.meta.url))),
    '', 'SET NAMES utf8mb4;',
    'CREATE TEMPORARY TABLE rynew_init_guard(check_name VARCHAR(80) PRIMARY KEY);',
    guard('DATABASE_MUST_BE_SELECTED', 'DATABASE() IS NULL'),
    guard('INSTALL_PASSWORD_HASH_REQUIRED', "NOT REGEXP_LIKE(CAST(COALESCE(@rynew_admin_password_hash,'') AS CHAR CHARACTER SET utf8mb4),'^[$]2[aby][$]1[0-4][$][./A-Za-z0-9]{53}$','c')"),
    "SET @rynew_init_lock = CONCAT('rynew_init_',LEFT(SHA2(DATABASE(),256),40));",
    'SET @rynew_init_lock_acquired = GET_LOCK(@rynew_init_lock,0);',
    guard('ANOTHER_INSTALLATION_IS_RUNNING', '@rynew_init_lock_acquired IS NULL OR @rynew_init_lock_acquired<>1'),
    guard('EMPTY_DATABASE_REQUIRED', '(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE())<>0 ' +
      'OR (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE())<>0 ' +
      'OR (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE())<>0'),
    '', '-- BEGIN SCHEMA SNAPSHOT', schema, '-- END SCHEMA SNAPSHOT', '', 'START TRANSACTION;',
    ...seeds.flatMap(({ table, columns, rows }) => [
      '-- Technical seed: ' + table + ' (' + rows.length + ' rows)',
      ...rows.map(row => 'INSERT INTO `' + table + '` (' + columns.map(column => '`' + column + '`').join(',') + ') VALUES (' +
        columns.map(column => table === 'sys_user' && column === 'password' ? '@rynew_admin_password_hash' : sqlLiteral(row[column])).join(',') + ');'), ''
    ]),
    'COMMIT;', 'SET @rynew_admin_password_hash = NULL;',
    'DROP TEMPORARY TABLE rynew_init_guard;',
    "SELECT 'INIT_OK' AS installation_status, (SELECT COUNT(*) FROM sys_user) AS initial_users, " +
      '(SELECT COUNT(*) FROM sys_menu) AS menu_count, (SELECT COUNT(*) FROM sup_auto_inspection_tool) AS built_in_tools;',
    'SELECT RELEASE_LOCK(@rynew_init_lock) AS installation_lock_released;', ''
  ].join('\n')
}

export function validatePassword(password) {
  assert.ok(/^[!-~]{12,20}$/.test(password), 'Password must be 12-20 printable ASCII characters without spaces')
  const classes = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter(pattern => pattern.test(password)).length
  assert.ok(classes >= 3, 'Password must use at least three character classes')
}

export function hashPassword(password) {
  validatePassword(password)
  let result
  try {
    result = execFileSync(process.env.HTPASSWD_BIN || 'htpasswd', ['-n', '-i', '-B', '-C', '12', 'bootstrap'],
      { input: password + '\n', encoding: 'utf8', timeout: 10000, stdio: ['pipe', 'pipe', 'pipe'] })
  } catch {
    throw new Error('BCrypt generation failed; install a trusted htpasswd with BCrypt support')
  }
  const hash = result.trim().slice('bootstrap:'.length)
  assert.ok(result.startsWith('bootstrap:') && bcryptPattern.test(hash), 'htpasswd did not return a supported BCrypt hash')
  return hash
}

export function prepareInstall(passwordFile, destination) {
  const parent = realpathSync(dirname(resolve(destination)))
  let inGit = false
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('GIT_')))
  try {
    execFileSync('git', ['-C', parent, 'rev-parse', '--absolute-git-dir'], { stdio: 'pipe', env })
    inGit = true
  } catch (error) {
    assert.ok(error.status === 128 && error.stderr?.toString().includes('not a git repository'),
      'Cannot verify that the output is outside Git; refusing to write credentials')
  }
  assert.equal(inGit, false, 'The credential-bearing installation copy must be outside every Git worktree')
  const stat = lstatSync(passwordFile)
  assert.ok(stat.isFile() && (stat.mode & 0o077) === 0 && stat.size <= 256, 'Password input must be a small owner-only regular file (0600)')
  if (process.getuid) assert.equal(stat.uid, process.getuid(), 'Password file must belong to the current user')
  const buffer = readFileSync(passwordFile)
  try {
    const password = buffer.toString('utf8').replace(/\r?\n$/, '')
    const hash = hashPassword(password)
    const sql = "SET @rynew_admin_password_hash = '" + hash + "';\n" + readFileSync(output, 'utf8')
    writeFileSync(resolve(parent, basename(destination)), sql, { mode: 0o600, flag: 'wx' })
    return { account: 'admin', output: resolve(destination) }
  } finally { buffer.fill(0) }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const mode = process.argv[2]
  assert.ok(['--write', '--check', '--prepare'].includes(mode), 'Use --write, --check or --prepare')
  if (mode === '--prepare') {
    const value = flag => process.argv[process.argv.indexOf(flag) + 1]
    assert.ok(process.argv.includes('--password-file') && process.argv.includes('--output'), 'Both --password-file and --output are required')
    console.log(JSON.stringify(prepareInstall(value('--password-file'), value('--output'))))
  } else await withIsolatedMysql(async mysql => {
    const seeds = await buildSeeds(mysql)
    const sql = renderInit(seeds)
    if (mode === '--write') writeFileSync(output, sql)
    else assert.equal(readFileSync(output, 'utf8'), sql, 'Complete initialization SQL is stale')
    console.log(JSON.stringify({ version, mysql: mysql.version, seedTables: seeds.length,
      seedRows: seeds.reduce((sum, seed) => sum + seed.rows.length, 0), output, mode }))
  })
}
