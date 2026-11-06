import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { chmodSync, existsSync, mkdtempSync, readFileSync, rmSync, statSync, symlinkSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { test } from 'node:test'
import { withIsolatedMysql } from './isolated-mysql.mjs'
import { output as schemaOutput, root, schemaMetadata } from './schema-latest.mjs'
import { output, bcryptPattern, buildSeeds, hashPassword, prepareInstall, renderInit, seedTables, validatePassword } from './init-latest.mjs'

const fixturePassword = 'Fixture!Pass2026'

test('bootstrap password constraints follow RuoYi and never accept a public weak default', () => {
  const constants = readFileSync(resolve(root, 'WDF100.0/wjdatafusion-common/src/main/java/com/hm/common/constant/UserConstants.java'), 'utf8')
  assert.match(constants, /PASSWORD_MAX_LENGTH = 20;/)
  for (const password of ['admin123', '123456', 'abcdefghijkl', 'X'.repeat(21), 'Has Space!12345']) {
    assert.throws(() => validatePassword(password))
  }
  validatePassword(fixturePassword)
})

test('private installation copy contains only a bcrypt hash, cannot overwrite or enter Git', () => {
  const dir = mkdtempSync('/tmp/rynew-init-private-')
  try {
    const secret = resolve(dir, 'password.txt'), install = resolve(dir, 'install.sql')
    writeFileSync(secret, fixturePassword, { mode: 0o600 })
    assert.deepEqual(prepareInstall(secret, install), { account: 'admin', output: install })
    const sql = readFileSync(install, 'utf8')
    assert.ok(!sql.includes(fixturePassword))
    assert.match(sql.split('\n')[0], /^SET @rynew_admin_password_hash = '\$2[aby]\$12\$/)
    assert.equal(statSync(install).mode & 0o077, 0)
    assert.throws(() => prepareInstall(secret, install), /EEXIST/)
    assert.throws(() => prepareInstall(secret, resolve(root, 'must-not-write-init.sql')), /outside every Git worktree/)
    chmodSync(secret, 0o644)
    assert.throws(() => prepareInstall(secret, resolve(dir, 'unsafe.sql')), /owner-only/)
    chmodSync(secret, 0o600)
    const link = resolve(dir, 'password-link')
    symlinkSync(secret, link)
    assert.throws(() => prepareInstall(link, resolve(dir, 'unsafe.sql')), /regular file/)
    const nested = resolve(dir, 'repository')
    execFileSync('git', ['init', '-q', nested], { stdio: 'pipe' })
    assert.throws(() => prepareInstall(secret, resolve(nested, '.git', 'must-not-write.sql')), /outside every Git worktree/)
  } finally { rmSync(dir, { recursive: true, force: true }) }
})

test('prepared BCrypt hashes are accepted by the actual RuoYi Spring Security encoder', { timeout: 120000 }, () => {
  const dir = mkdtempSync('/tmp/rynew-init-bcrypt-')
  try {
    const classpathFile = resolve(dir, 'classpath.txt')
    execFileSync('mvn', ['-q', '-f', resolve(root, 'WDF100.0/wjdatafusion-common/pom.xml'),
      'dependency:build-classpath', '-Dmdep.outputFile=' + classpathFile],
    { stdio: 'pipe', timeout: 90000 })
    const hash = hashPassword(fixturePassword)
    const result = execFileSync('java', ['--class-path', readFileSync(classpathFile, 'utf8').trim(),
      resolve(root, 'tools/database/VerifyBootstrapPassword.java')],
    { input: fixturePassword + '\n' + hash + '\n', encoding: 'utf8', timeout: 15000, stdio: 'pipe' })
    assert.equal(result.trim(), 'PASSWORD_MATCH')
  } finally { rmSync(dir, { recursive: true, force: true }) }
})

test('complete fresh install is usable, minimal and rejects unsafe reinstallation', async () => {
  await withIsolatedMysql(async mysql => {
    const seeds = await buildSeeds(mysql)
    const sql = readFileSync(output, 'utf8')
    assert.equal(sql, renderInit(seeds))
    const hash = hashPassword(fixturePassword)
    assert.ok(bcryptPattern.test(hash))
    const install = "SET @rynew_admin_password_hash='" + hash + "';\n" + sql
    for (const name of ['schema_init', 'schema_reference', 'schema_missing_password', 'schema_existing',
      'schema_bad_hash', 'schema_partial', 'schema_routines', 'schema_failed_seed', 'schema_locked']) {
      await mysql.query('CREATE DATABASE `' + name + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci')
    }
    const missing = await mysql.execute(sql, 'schema_missing_password')
    assert.notEqual(missing.status, 0)
    assert.match(missing.stderr, /INSTALL_PASSWORD_HASH_REQUIRED/)
    assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='schema_missing_password'"), '0')
    for (const bad of ['123456', 'not-bcrypt', '$2y$05$' + 'A'.repeat(53)]) {
      const result = await mysql.execute("SET @rynew_admin_password_hash='" + bad + "';\n" + sql, 'schema_bad_hash')
      assert.notEqual(result.status, 0)
      assert.match(result.stderr, /INSTALL_PASSWORD_HASH_REQUIRED/)
    }
    assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='schema_bad_hash'"), '0')
    await mysql.query(install, 'schema_init')
    await mysql.query(readFileSync(schemaOutput, 'utf8'), 'schema_reference')
    assert.deepEqual(await schemaMetadata(mysql, 'schema_init'), await schemaMetadata(mysql, 'schema_reference'))
    const q = sql => mysql.query(sql, 'schema_init')
    assert.equal(await q("SELECT CONCAT(user_id,':',user_name,':',status,':',dept_id) FROM sys_user"), '1:admin:0:100')
    assert.equal(await q('SELECT COUNT(*) FROM sys_user'), '1')
    assert.equal(await q('SELECT COUNT(*) FROM sys_user_role WHERE user_id=1 AND role_id=1'), '1')
    assert.equal(await q('SELECT COUNT(*) FROM sys_user_role'), '1')
    assert.equal(await q("SELECT COUNT(*) FROM sys_user WHERE email<>'' OR phonenumber<>'' OR login_ip<>'' OR login_date IS NOT NULL"), '0')
    assert.equal(await q('SELECT COUNT(*) FROM sys_user WHERE pwd_update_date IS NULL'), '1')
    assert.equal(await q("SELECT config_value FROM sys_config WHERE config_key='sys.user.initPassword'"), '')
    assert.equal(await q("SELECT config_value FROM sys_config WHERE config_key='sys.account.registerUser'"), 'false')
    assert.equal(await q("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE r.role_key='common'"), '0')
    assert.equal(await q('SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id=m.menu_id AND rm.role_id=1 WHERE rm.menu_id IS NULL'), '0')
    assert.equal(await q('SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_menu p ON p.menu_id=m.parent_id WHERE m.parent_id<>0 AND p.menu_id IS NULL'), '0')
    assert.equal(await q('SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_role r ON r.role_id=rm.role_id LEFT JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE r.role_id IS NULL OR m.menu_id IS NULL'), '0')
    assert.equal(await q('SELECT COUNT(*) FROM sys_dict_data d LEFT JOIN sys_dict_type t ON t.dict_type=d.dict_type WHERE t.dict_id IS NULL'), '0')
    assert.ok(Number(await q('SELECT COUNT(*) FROM sys_dict_type')) > 0)
    for (const component of (await q("SELECT component FROM sys_menu WHERE menu_type='C' AND COALESCE(component,'')<>''")).split('\n')) {
      assert.ok(existsSync(resolve(root, 'RuoYi-Vue3-master/src/views', component + '.vue')), component)
    }
    const menus = (await q("SELECT JSON_OBJECT('id',menu_id,'parent',parent_id,'status',status) FROM sys_menu ORDER BY menu_id"))
      .split('\n').map(row => JSON.parse(row))
    const expectedGrants = new Set([2200, 2300])
    for (const id of expectedGrants) {
      for (const child of menus.filter(menu => menu.parent === id && menu.status === '0')) expectedGrants.add(child.id)
    }
    const actualGrants = (await q("SELECT rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE r.role_key='datafusion' ORDER BY rm.menu_id"))
      .split('\n').map(Number)
    assert.deepEqual(actualGrants, [...expectedGrants].sort((a, b) => a - b))
    assert.equal(await q("SELECT CONCAT(menu_type,':',perms,':',component) FROM sys_menu WHERE menu_id=2507"), 'C:document:file:manage:document/workspace/index')
    assert.equal(await q("SELECT COUNT(*) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE r.role_key='document'"), '8')
    assert.equal(await q("SELECT COUNT(*) FROM sys_menu WHERE component='governance/workspace/index' AND perms='governance:flow:list'"), '1')
    assert.equal(await q("SELECT CONCAT(visible,':',status) FROM sys_menu WHERE menu_id=2206"), '1:1')
    const codes = await q('SELECT tool_code FROM sup_auto_inspection_tool ORDER BY tool_code')
    const service = readFileSync(resolve(root, 'WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/service/impl/SupportAutoInspectionServiceImpl.java'), 'utf8')
    const expectedTools = [...service.matchAll(/private static final String TOOL_[A-Z_]+ = "([A-Z_]+)";/g)].map(match => match[1]).sort()
    assert.deepEqual(codes.split('\n'), expectedTools)
    assert.equal(await q("SELECT COUNT(*) FROM sys_job WHERE invoke_target NOT IN ('documentPermissionExpiryTask.expire') OR status<>'0'"), '0')
    assert.equal(await q('SELECT COUNT(*) FROM sys_job'), '1')
    assert.equal(await q('SELECT max_upload_bytes FROM doc_user_quota WHERE user_id=1'), '104857600')
    assert.equal(await q("SELECT next_value FROM dg_hdi_counter WHERE counter_name='native_runtime'"), '100000')
    assert.equal(await q("SELECT workspace_key FROM dg_hdi_workspace"), 'platform')
    const tables = (await q('SHOW TABLES')).split('\n')
    assert.equal(tables.length, 105)
    for (const table of tables.filter(table => !seedTables.includes(table))) {
      assert.equal(await q('SELECT COUNT(*) FROM `' + table + '`'), '0', table)
    }
    const before = await q('SELECT COUNT(*) FROM sys_menu')
    const repeated = await mysql.execute(install, 'schema_init')
    assert.notEqual(repeated.status, 0)
    assert.match(repeated.stderr, /EMPTY_DATABASE_REQUIRED/)
    assert.equal(await q('SELECT COUNT(*) FROM sys_menu'), before)
    assert.equal(await q('SELECT password FROM sys_user'), hash)
    await mysql.query('CREATE TABLE fixture(id INT PRIMARY KEY, value VARCHAR(20)); INSERT INTO fixture VALUES(1,\'keep\')', 'schema_existing')
    const existing = await mysql.execute(install, 'schema_existing')
    assert.notEqual(existing.status, 0)
    assert.match(existing.stderr, /EMPTY_DATABASE_REQUIRED/)
    assert.equal(await mysql.query('SELECT value FROM fixture', 'schema_existing'), 'keep')
    assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='schema_existing'"), '1')
    await mysql.query(readFileSync(schemaOutput, 'utf8'), 'schema_partial')
    const partial = await mysql.execute(install, 'schema_partial')
    assert.notEqual(partial.status, 0)
    assert.match(partial.stderr, /EMPTY_DATABASE_REQUIRED/)
    assert.equal(await mysql.query('SELECT COUNT(*) FROM sys_user', 'schema_partial'), '0')
    await mysql.query('CREATE PROCEDURE fixture_noop() SELECT 1', 'schema_routines')
    const routines = await mysql.execute(install, 'schema_routines')
    assert.notEqual(routines.status, 0)
    assert.match(routines.stderr, /EMPTY_DATABASE_REQUIRED/)
    assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='schema_routines'"), '0')
    assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='schema_routines'"), '1')
    const failed = await mysql.execute(install.replace('INSERT INTO `sys_user` (`user_id`', 'INSERT INTO `sys_user` (`unknown_user_id`'), 'schema_failed_seed')
    assert.notEqual(failed.status, 0)
    assert.match(failed.stderr, /unknown_user_id/)
    for (const table of seedTables) assert.equal(await mysql.query('SELECT COUNT(*) FROM `' + table + '`', 'schema_failed_seed'), '0', table)
    const lock = await mysql.query("SELECT CONCAT('rynew_init_',LEFT(SHA2('schema_locked',256),40))")
    const holder = mysql.execute("SELECT GET_LOCK('" + lock + "',0);SELECT SLEEP(60)")
    let connectionId = 0
    try {
      for (let attempt = 0; attempt < 100; attempt++) {
        connectionId = Number(await mysql.query("SELECT COALESCE(IS_USED_LOCK('" + lock + "'),0)"))
        if (connectionId) break
        await new Promise(resolve => setTimeout(resolve, 20))
      }
      assert.ok(Number.isSafeInteger(connectionId) && connectionId > 0)
      const locked = await mysql.execute(install, 'schema_locked')
      assert.notEqual(locked.status, 0)
      assert.match(locked.stderr, /ANOTHER_INSTALLATION_IS_RUNNING/)
      assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='schema_locked'"), '0')
    } finally {
      if (connectionId) await mysql.query('KILL ' + connectionId)
      await holder
    }
    assert.equal(await mysql.query("SELECT IS_FREE_LOCK('" + lock + "')"), '1')
    console.log(JSON.stringify({ mysql: mysql.version, tables: tables.length, users: 1,
      tools: expectedTools.length, businessTablesEmpty: true, oldDatabaseUntouched: true }))
  })
})
