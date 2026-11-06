import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { withIsolatedMysql } from './isolated-mysql.mjs'

export const root = fileURLToPath(new URL('../../', import.meta.url))
export const output = resolve(root, 'WDF100.0/sql/rynew_schema_latest.sql')
export const version = 'v4.17.2'
export const sources = [
  'ry_20260320.sql',
  'quartz.sql',
  'support_deploy_all.sql',
  'support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql',
  'ipam_upgrade_20260707_v3_9_0.sql',
  'ipam_upgrade_20260708_v3_9_2.sql',
  'ipam_upgrade_20260709_v3_9_3.sql',
  'ipam_upgrade_20260711_v3_9_4.sql',
  'ipam_upgrade_20260712_v3_9_5.sql',
  'ipam_upgrade_20260712_v3_9_6.sql',
  'ipam_upgrade_20260712_v3_9_7.sql',
  'ipam_upgrade_20260717_v3_9_8.sql',
  'ipam_upgrade_20260717_v3_9_9.sql',
  'ipam_upgrade_20260811_v3_9_11.sql',
  'ipam_remove_credential_key_20260811.sql',
  'whitelist_v1.sql',
  'whitelist_upgrade_20260409_drop_filter_unique_index.sql',
  'document_management_v1_20260815.sql',
  'document_management_p0_20260815.sql',
  'document_management_p1_20260816.sql',
  'document_management_p1_lightweight_versions_20260816.sql',
  'document_management_v3_9_19_archive_quota_20260816.sql',
  'knowledge_center_upgrade_20260829_v3_14_3_to_v3_15_2_all.sql',
  'data_governance_hdi_core_upgrade_20261013.sql',
  'data_governance_hdi_connections_upgrade_20261025.sql'
]

export function readSource(name) {
  assert.ok(sources.includes(name), 'Unknown schema source: ' + name)
  return readFileSync(resolve(root, 'WDF100.0/sql', name), 'utf8')
}

export function sourceFingerprint(name) {
  return createHash('sha256').update(readSource(name)).digest('hex')
}

export async function buildSourceSchema(mysql, database = 'schema_source') {
  assert.match(database, /^schema_[a-z_]+$/)
  await mysql.query('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci')
  for (const name of sources) {
    const result = await mysql.execute(readSource(name), database)
    assert.equal(result.status, 0, name + ': ' + result.stderr)
  }
  return database
}

export async function tableDefinitions(mysql, database) {
  const rows = await mysql.query('SHOW FULL TABLES WHERE Table_type = \'BASE TABLE\'', database)
  const definitions = []
  for (const table of rows.split('\n').filter(Boolean).map(row => row.split('\t')[0]).sort()) {
    assert.match(table, /^[A-Za-z0-9_]+$/)
    const create = await mysql.query('SHOW CREATE TABLE `' + table + '`', database)
    definitions.push({ table, definition: create.slice(table.length + 1).replace(/ AUTO_INCREMENT=\d+\b/g, '') })
  }
  return definitions
}

export async function schemaMetadata(mysql, database) {
  assert.match(database, /^schema_[a-z_]+$/)
  const select = (table, columns, order, schemaColumn = 'TABLE_SCHEMA') => mysql.query('SELECT ' + columns +
    ' FROM information_schema.' + table + ' WHERE ' + schemaColumn + "='" + database + "' ORDER BY " + order)
  return {
    tables: await select('TABLES', 'TABLE_NAME,ENGINE,ROW_FORMAT,TABLE_COLLATION,TABLE_COMMENT', 'TABLE_NAME'),
    columns: await select('COLUMNS', 'TABLE_NAME,ORDINAL_POSITION,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,' +
      'COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME,COLUMN_COMMENT,GENERATION_EXPRESSION',
    'TABLE_NAME,ORDINAL_POSITION'),
    indexes: await select('STATISTICS', 'TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,' +
      'COLLATION,SUB_PART,INDEX_TYPE,INDEX_COMMENT,IS_VISIBLE,EXPRESSION',
    'TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX'),
    constraints: await select('KEY_COLUMN_USAGE', 'TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,' +
      'REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME', 'TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION'),
    references: await select('REFERENTIAL_CONSTRAINTS', 'TABLE_NAME,CONSTRAINT_NAME,UNIQUE_CONSTRAINT_NAME,' +
      'MATCH_OPTION,UPDATE_RULE,DELETE_RULE', 'TABLE_NAME,CONSTRAINT_NAME', 'CONSTRAINT_SCHEMA')
  }
}

export function renderSchema(definitions) {
  return [
    '-- RYNEW ' + version + ' latest platform schema (2026-11-06).',
    '-- Schema only: no accounts, passwords, menus, dictionaries, jobs or business rows.',
    '-- Source: repository SQL rebuilt in a new socket-only MySQL; never a live database export.',
    '-- Select an EMPTY target database before importing; database creation is deliberately excluded.',
    '-- NOT an upgrade script. IF NOT EXISTS does not repair old columns/indexes.',
    '-- Original legacy inspection tables are retained for historical compatibility only.',
    '-- Rebuild: node tools/database/schema-latest.mjs --write',
    '-- Validate: node --test tools/database/*.test.mjs',
    '-- Source fingerprints (SHA-256):',
    ...sources.map(name => '-- ' + sourceFingerprint(name) + '  ' + name),
    '',
    'SET NAMES utf8mb4;',
    'SET @rynew_previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;',
    'SET FOREIGN_KEY_CHECKS = 0;',
    '',
    ...definitions.flatMap(({ table, definition }) => [
      '-- Table: ' + table,
      definition.replace(/^CREATE TABLE /, 'CREATE TABLE IF NOT EXISTS ') + ';', ''
    ]),
    'SET FOREIGN_KEY_CHECKS = @rynew_previous_foreign_key_checks;',
    ''
  ].join('\n')
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  assert.ok(['--write', '--check'].includes(process.argv[2]), 'Use --write or --check')
  await withIsolatedMysql(async mysql => {
    const database = await buildSourceSchema(mysql)
    const definitions = await tableDefinitions(mysql, database)
    const sql = renderSchema(definitions)
    if (process.argv[2] === '--write') writeFileSync(output, sql)
    else assert.equal(readFileSync(output, 'utf8'), sql, 'Latest schema is stale; rebuild with --write')
    console.log(JSON.stringify({ version, mysql: mysql.version, sourceFiles: sources.length,
      tables: definitions.length, output, mode: process.argv[2] }))
  })
}
