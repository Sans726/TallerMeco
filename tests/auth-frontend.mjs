import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'
import test from 'node:test'

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const ts = require('typescript')
let source = await readFile(new URL('../frontend/src/api.ts', import.meta.url), 'utf8')
source = source.replaceAll("import.meta.env.VITE_DEMO", "'false'")
source = source.replace("from 'vue'", `from '${pathToFileURL(require.resolve('vue/dist/vue.runtime.esm-bundler.js')).href}'`)
const output = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
for (const name of ['localStorage', 'sessionStorage']) {
  Object.defineProperty(globalThis, name, { get() { throw new Error('Normal authentication must not access browser storage') } })
}
globalThis.location = { hash: '' }
const auth = await import('data:text/javascript;base64,' + Buffer.from(output).toString('base64'))
const response = (status, body) => new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const user = { id: 27, name: 'Recepción', email: 'reception@example.invalid', role: 'RECEPTIONIST', roles: ['RECEPTIONIST'] }

await test('normal login trusts /auth/me and refreshes CSRF after authentication', async () => {
  const calls = []
  const replies = [response(200, { header: 'X-CSRF-TOKEN', token: 'before' }), response(200, { ok: true }), response(200, { header: 'X-CSRF-TOKEN', token: 'after' }), response(200, user)]
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return replies.shift() }
  await auth.login(' reception@example.invalid ', 'test-only-password')
  assert.equal(auth.isDemo, false)
  assert.deepEqual(calls.map(c => c.url), ['/api/auth/csrf', '/api/auth/login', '/api/auth/csrf', '/api/auth/me'])
  assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], 'before')
  assert.deepEqual([...calls[1].options.body.keys()], ['email', 'password'])
  assert.equal(calls[1].options.body.get('email'), user.email)
  assert.ok(calls.every(c => c.options.credentials === 'same-origin'))
  assert.deepEqual({ ...auth.session.user }, user)
  assert.equal(auth.roles.RECEPTIONIST, 'Recepción')
})

await test('logout sends CSRF, uses the backend and clears the in-memory identity', async () => {
  const calls = []
  const replies = [response(200, { header: 'X-CSRF-TOKEN', token: 'logout' }), response(204)]
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return replies.shift() }
  await auth.logout()
  assert.deepEqual(calls.map(c => c.url), ['/api/auth/csrf', '/api/auth/logout'])
  assert.equal(calls[1].options.method, 'POST')
  assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], 'logout')
  assert.equal(auth.session.user, null)
  assert.equal(location.hash, '/login')
})

await test('wrong password leaves no frontend identity', async () => {
  const replies = [response(200, { header: 'X-CSRF-TOKEN', token: 'login' }), response(401, { message: 'Correo o contraseña incorrectos' })]
  globalThis.fetch = async () => replies.shift()
  await assert.rejects(auth.login(user.email, 'incorrect'), /Correo o contraseña incorrectos/)
  assert.equal(auth.session.user, null)
})

await test('expired /auth/me removes the previously cached identity', async () => {
  auth.session.user = user
  globalThis.fetch = async () => response(401, { message: 'Inicia sesión' })
  await assert.rejects(auth.api('/auth/me'), /Inicia sesión/)
  assert.equal(auth.session.user, null)
})

await test('CSRF fetch failure prevents credential submission', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return response(503, { message: 'No disponible' }) }
  await assert.rejects(auth.login(user.email, 'password'), /No disponible/)
  assert.equal(calls, 1)
})

await test('normal production JS does not contain demo identities or browser session storage', async () => {
  const assets = resolve('backend/src/main/resources/static/assets')
  const files = (await readdir(assets)).filter(f => f.endsWith('.js'))
  assert.ok(files.length)
  const bundle = (await Promise.all(files.map(f => readFile(resolve(assets, f), 'utf8')))).join('\n')
  for (const forbidden of ['tallermeco-prototype-v1', 'taller-demo-user', 'demoLogin', 'Ana Martínez', 'localStorage', 'sessionStorage']) {
    assert.ok(!bundle.includes(forbidden), `Demo code leaked into normal bundle: ${forbidden}`)
  }
  const loginView = await readFile(new URL('../frontend/src/views/Auth.vue', import.meta.url), 'utf8')
  assert.ok(loginView.includes('v-if="isDemo" class="demo-access"'))
  assert.ok(loginView.includes('v-if="!message&&!isDemo"'))
})
