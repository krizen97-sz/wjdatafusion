import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { test } from 'node:test'
import { withIsolatedMysql } from './isolated-mysql.mjs'
import { buildSourceSchema, output, renderSchema, root, sources, sourceFingerprint,
  schemaMetadata, tableDefinitions } from './schema-latest.mjs'

test('latest schema is present, source-traceable and contains no data or destructive DDL', () => {
  const sql = readFileSync(output, 'utf8')
  for (const name of sources) assert.ok(sql.includes('-- ' + sourceFingerprint(name) + '  ' + name), name)
  assert.doesNotMatch(sql, /^\s*(?:INSERT|REPLACE|UPDATE|DELETE|TRUNCATE|DROP|ALTER|USE)\b/im)
  assert.doesNotMatch(sql, /^\s*CREATE\s+(?:DATABASE|PROCEDURE|TRIGGER|EVENT)\b/im)
  assert.doesNotMatch(sql, /^\s*SOURCE\b/im)
  assert.match(sql, /CREATE TABLE IF NOT EXISTS `dg_hdi_connection_table`/)
  assert.match(sql, /CREATE TABLE IF NOT EXISTS `doc_user_quota`/)
  assert.match(sql, /CREATE TABLE IF NOT EXISTS `kb_page`/)
  assert.match(sql, /CREATE TABLE IF NOT EXISTS `ipam_scan_lock`/)
})

test('snapshot covers the platform business tables named in MyBatis mappers', () => {
  const sql = readFileSync(output, 'utf8')
  const tables = new Set([...sql.matchAll(/^CREATE TABLE IF NOT EXISTS `([^`]+)`/gm)]
    .map(match => match[1].toLowerCase()))
  const missing = new Set()
  function inspect(directory) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = resolve(directory, entry.name)
      if (entry.isDirectory()) inspect(path)
      else if (entry.name.endsWith('.xml')) {
        const mapper = readFileSync(path, 'utf8')
        for (const match of mapper.matchAll(/\b(?:from|join|update|into)\s+`?((?:sup_|ipam_|doc_|kb_|sys_|wl_|dg_hdi_)[a-z0-9_]+)/gi)) {
          if (!tables.has(match[1].toLowerCase())) missing.add(match[1])
        }
      }
    }
  }
  inspect(resolve(root, 'WDF100.0/wjdatafusion-manage/src/main/resources/mapper'))
  assert.deepEqual([...missing], [])
})

test('fresh import matches released sources, is row-free and repeat-safe', async () => {
  await withIsolatedMysql(async mysql => {
    const source = await buildSourceSchema(mysql)
    const expected = await tableDefinitions(mysql, source)
    const sql = readFileSync(output, 'utf8')
    assert.equal(sql, renderSchema(expected), 'Rebuild schema from the current source SQL')
    await mysql.query('CREATE DATABASE schema_import CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci')
    assert.equal(await mysql.query(sql + '\nSELECT @@FOREIGN_KEY_CHECKS;', 'schema_import'), '1')
    assert.deepEqual(await schemaMetadata(mysql, 'schema_import'), await schemaMetadata(mysql, source))
    const imported = await tableDefinitions(mysql, 'schema_import')
    for (const { table } of expected) {
      assert.equal(await mysql.query('SELECT COUNT(*) FROM `' + table + '`', 'schema_import'), '0', table)
    }
    const columns = table => mysql.query(`SELECT COLUMN_NAME FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='schema_import' AND TABLE_NAME='${table}' ORDER BY ORDINAL_POSITION`)
    assert.ok((await columns('sup_server_scan_credential')).split('\n').includes('password_cipher'))
    assert.ok((await columns('sup_auto_inspection_target_result')).split('\n').includes('window_key'))
    assert.ok((await columns('sup_equipment_cabinet')).split('\n').includes('position_x'))
    assert.ok((await columns('doc_folder')).split('\n').includes('folder_color'))
    assert.ok(!(await columns('ipam_address')).split('\n').includes('login_password_cipher'))
    assert.equal(expected.filter(({ table }) => table.startsWith('dg_hdi_')).length, 10)
    assert.equal(expected.filter(({ table }) => table.startsWith('ipam_')).length, 13)
    assert.equal(expected.length, 105)
    assert.ok(!expected.some(({ table }) => /(?:_bak_|_clean_bak_|migration_20)/.test(table)))
    const orphan = await mysql.execute('INSERT INTO sup_server_scan_profile(server_id,site_id) VALUES(123,456)', 'schema_import')
    assert.notEqual(orphan.status, 0)
    assert.match(orphan.stderr, /foreign key constraint fails/i)
    await mysql.query("INSERT INTO dg_hdi_counter VALUES('keep_fixture',42)", 'schema_import')
    assert.equal(await mysql.query('SET FOREIGN_KEY_CHECKS=0;\n' + sql + '\nSELECT @@FOREIGN_KEY_CHECKS;', 'schema_import'), '0')
    assert.equal(await mysql.query("SELECT next_value FROM dg_hdi_counter WHERE counter_name='keep_fixture'", 'schema_import'), '42')
    assert.deepEqual(await tableDefinitions(mysql, 'schema_import'), imported)
    await mysql.query('CREATE DATABASE schema_legacy CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci')
    await mysql.query("CREATE TABLE sup_auto_inspection_plan(plan_id BIGINT PRIMARY KEY, fixture_marker VARCHAR(20));" +
      "INSERT INTO sup_auto_inspection_plan VALUES(1,'untouched')", 'schema_legacy')
    await mysql.query(sql, 'schema_legacy')
    assert.equal(await mysql.query('SELECT fixture_marker FROM sup_auto_inspection_plan WHERE plan_id=1', 'schema_legacy'), 'untouched')
    assert.equal(await mysql.query("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='schema_legacy' " +
      "AND TABLE_NAME='sup_auto_inspection_plan' AND COLUMN_NAME='template_id'"), '0', 'Snapshot must not act as an upgrade')
    console.log(JSON.stringify({ mysql: mysql.version, tables: expected.length,
      freshImport: true, emptyBusinessData: true, repeatedImportPreservedRows: true }))
  })
})
