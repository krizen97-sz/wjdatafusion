import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, realpathSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { once } from 'node:events'

// This helper cannot accept a host, a connection string or an existing datadir.
export async function withIsolatedMysql(callback) {
  const dir = mkdtempSync(join(tmpdir(), 'rynew-schema-'))
  const data = join(dir, 'data')
  mkdirSync(data)
  const mysqld = process.env.MYSQL_TEST_SERVER || '/usr/local/mysql/bin/mysqld'
  const mysql = process.env.MYSQL_TEST_CLIENT || '/usr/local/mysql/bin/mysql'
  const log = join(dir, 'mysql.log')
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('MYSQL_')))
  let server
  let interrupted
  let cancel
  const cancelled = new Promise((resolve, reject) => { cancel = reject })
  cancelled.catch(() => {})
  const handlers = new Map(['SIGINT', 'SIGTERM'].map(signal => [signal, () => {
    if (!interrupted) {
      interrupted = new Error('Isolated MySQL cancelled by ' + signal)
      controller.abort(interrupted)
      cancel(interrupted)
    }
  }]))
  const controller = new AbortController()
  const commands = new Set()
  const run = (program, args, input = '') => {
    const command = new Promise(resolve => {
      let stdout = '', stderr = '', error, size = 0
      const child = spawn(program, args, { env, signal: controller.signal, stdio: ['pipe', 'pipe', 'pipe'] })
      const timer = setTimeout(() => {
        error = new Error('Isolated MySQL command timed out')
        child.kill('SIGKILL')
      }, 60000)
      const append = stream => chunk => {
        size += Buffer.byteLength(chunk)
        if (size > 32 * 1024 * 1024) {
          error = new Error('Isolated MySQL output exceeded 32 MiB')
          child.kill('SIGKILL')
        } else if (stream === 'stdout') stdout += chunk
        else stderr += chunk
      }
      child.stdout.setEncoding('utf8')
      child.stderr.setEncoding('utf8')
      child.stdout.on('data', append('stdout'))
      child.stderr.on('data', append('stderr'))
      child.on('error', cause => { error = cause })
      child.stdin.on('error', cause => { if (cause.code !== 'EPIPE') error = cause })
      child.on('close', (status, signal) => {
        clearTimeout(timer)
        resolve({ status, signal, stdout, stderr, error })
      })
      child.stdin.end(input)
    })
    commands.add(command)
    command.then(() => commands.delete(command), () => commands.delete(command))
    return command
  }
  for (const [signal, handler] of handlers) process.on(signal, handler)
  try {
    const init = await run(mysqld, ['--no-defaults', '--initialize-insecure', '--datadir=' + data])
    if (interrupted) throw interrupted
    assert.equal(init.status, 0, init.stderr || init.error?.message)
    server = spawn(mysqld, ['--no-defaults', '--datadir=' + data,
      '--socket=' + join(dir, 'mysql.sock'), '--pid-file=' + join(dir, 'mysql.pid'),
      '--log-error=' + log, '--skip-networking', '--mysqlx=OFF', '--skip-log-bin',
      '--innodb-buffer-pool-size=64M', '--performance-schema=OFF', '--secure-file-priv=' + dir],
    { stdio: 'ignore', env })
    let startError
    server.once('error', error => { startError = error })
    const args = ['--no-defaults', '--no-login-paths', '--skip-password',
      '--protocol=SOCKET', '--socket=' + join(dir, 'mysql.sock'),
      '--user=root', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
    const execute = async (sql, database) => {
      if (interrupted) throw interrupted
      return run(mysql, [...args, ...(database ? [database] : [])], sql)
    }
    const query = async (sql, database) => {
      const result = await execute(sql, database)
      assert.equal(result.status, 0, result.stderr || result.error?.message)
      return result.stdout.trimEnd()
    }
    for (let attempt = 0; attempt < 150; attempt++) {
      if (startError) throw startError
      if (server.exitCode != null) throw Error(readFileSync(log, 'utf8'))
      if ((await execute('SELECT 1')).status === 0) break
      if (attempt === 149) throw Error('Isolated MySQL did not become ready: ' + log)
      await Promise.race([new Promise(resolve => setTimeout(resolve, 200)), cancelled])
    }
    assert.equal(realpathSync(await query('SELECT @@datadir')), realpathSync(data))
    assert.equal(await query('SELECT @@skip_networking'), '1')
    return await Promise.race([callback({ query, execute, version: await query('SELECT VERSION()') }), cancelled])
  } finally {
    try {
      controller.abort()
      await Promise.allSettled([...commands])
      if (server?.pid && server.exitCode == null && server.signalCode == null) {
        const exited = once(server, 'exit')
        server.kill('SIGTERM')
        const timer = setTimeout(() => server.kill('SIGKILL'), 10000)
        try { await exited } finally { clearTimeout(timer) }
      }
      if (!server?.pid || server.exitCode != null || server.signalCode != null) {
        rmSync(dir, { recursive: true, force: true })
      }
    } finally {
      for (const [signal, handler] of handlers) process.off(signal, handler)
    }
  }
}
