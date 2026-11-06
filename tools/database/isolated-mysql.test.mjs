import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { existsSync, mkdtempSync, realpathSync, readdirSync, rmSync } from 'node:fs'
import { test } from 'node:test'
import { withIsolatedMysql } from './isolated-mysql.mjs'

test('temporary MySQL ignores inherited passwords and login-file overrides', async () => {
  const keys = ['MYSQL_PWD', 'MYSQL_TEST_LOGIN_FILE', 'MYSQL_HOST', 'MYSQL_TCP_PORT']
  const before = new Map(keys.map(key => [key, process.env[key]]))
  try {
    process.env.MYSQL_PWD = 'fixture-invalid-password'
    process.env.MYSQL_TEST_LOGIN_FILE = '/fixture/never-read-mylogin.cnf'
    process.env.MYSQL_HOST = '192.0.2.1'
    process.env.MYSQL_TCP_PORT = '1'
    await withIsolatedMysql(async mysql => {
      assert.equal(await mysql.query('SELECT 1'), '1')
      assert.equal(await mysql.query('SELECT @@skip_networking'), '1')
    })
  } finally {
    for (const [key, value] of before) {
      if (value === undefined) delete process.env[key]
      else process.env[key] = value
    }
  }
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  test(signal + ' reaps the owned mysqld and removes its datadir', { timeout: 40000 }, async () => {
    // Keep the nested Unix socket below macOS's 103-byte path limit.
    const dir = mkdtempSync('/tmp/rynew-schema-cancel-')
    const program = `
      import { readFileSync } from 'node:fs';
      import { withIsolatedMysql } from ${JSON.stringify(new URL('./isolated-mysql.mjs', import.meta.url).href)};
      await withIsolatedMysql(async mysql => {
        const datadir = await mysql.query('SELECT @@datadir');
        const pid = Number(readFileSync(await mysql.query('SELECT @@pid_file'), 'utf8').trim());
        const running = mysql.query('SELECT SLEEP(60)');
        let active = false;
        for (let attempt = 0; attempt < 100; attempt++) {
          if (await mysql.query("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE INFO='SELECT SLEEP(60)'") !== '0') {
            active = true; break;
          }
          await new Promise(resolve => setTimeout(resolve, 20));
        }
        console.log(JSON.stringify({datadir, pid, active}));
        await running;
      });
    `
    const child = spawn(process.execPath, ['--input-type=module', '-e', program],
      { env: { ...process.env, TMPDIR: dir }, stdio: ['ignore', 'pipe', 'pipe'] })
    const exited = once(child, 'exit')
    exited.catch(() => {})
    let output = '', stderr = '', owned
    child.stdout.setEncoding('utf8')
    child.stderr.setEncoding('utf8')
    child.stderr.on('data', chunk => { stderr += chunk })
    try {
      owned = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(Error('Isolated child did not become ready: ' + stderr)), 20000)
        child.stdout.on('data', chunk => {
          output += chunk
          if (output.includes('\n')) {
            clearTimeout(timer)
            try { resolve(JSON.parse(output.split('\n')[0])) } catch (error) { reject(error) }
          }
        })
        exited.then(() => { clearTimeout(timer); reject(Error('Child exited before readiness: ' + stderr)) }, reject)
      })
      assert.ok(realpathSync(owned.datadir).startsWith(realpathSync(dir) + '/rynew-schema-'))
      assert.ok(Number.isSafeInteger(owned.pid) && owned.pid > 1)
      assert.equal(owned.active, true, 'The signal must interrupt an executing SQL command')
      const cancelledAt = Date.now()
      child.kill(signal)
      const [code, exitSignal] = await exited
      assert.ok(Date.now() - cancelledAt < 15000, 'Cancellation must not wait for the 60-second SQL command')
      assert.notEqual(code, 0)
      assert.equal(exitSignal, null, 'Node must finish cleanup instead of default signal termination')
      assert.match(stderr, new RegExp('cancelled by ' + signal))
      assert.equal(existsSync(owned.datadir), false)
      assert.throws(() => process.kill(owned.pid, 0), { code: 'ESRCH' })
      assert.deepEqual(readdirSync(dir), [])
    } finally {
      if (child.exitCode == null && child.signalCode == null) {
        child.kill('SIGTERM')
        await exited
      }
      // A failed cleanup assertion must not leave this regression's server behind.
      if (owned && existsSync(owned.datadir)) {
        try { process.kill(owned.pid, 'SIGTERM') } catch (error) { if (error.code !== 'ESRCH') throw error }
        let stopped = false
        for (let attempt = 0; attempt < 100; attempt++) {
          try { process.kill(owned.pid, 0) } catch (error) { if (error.code === 'ESRCH') { stopped = true; break }; throw error }
          await new Promise(resolve => setTimeout(resolve, 100))
        }
        assert.ok(stopped, 'Preserve the datadir if its owned mysqld cannot be stopped')
      }
      rmSync(dir, { recursive: true, force: true })
    }
  })
}
