#!/usr/bin/env node
// Fraud demonstration for the customer Omphile Mohlala.
//
// Creates one suspicious transaction through the platform's normal API and follows it through
// the real event flow: transaction-service -> outbox -> Kafka -> fraud-detection-service ->
// fraud alert -> case-service -> case, and notification-service. Nothing is inserted into a
// database directly. Every ID, score and status printed is read back from the running services.
//
// Repeatable: the customer and account are reused when they exist (the customer record is never
// modified). Each run adds one new transaction, tagged with its run ID in the description, so
// the demo records can be found and told apart. Running twice does not create a second customer.
//
// Usage (from the repository root):
//   scripts/dev/seed-fraud-demo.sh [--amount 60000] [--currency ZAR] [--timeout 60]
//
// Environment:
//   GATEWAY_URL                  default http://localhost:8000
//   FRAUD_DEMO_TIMEOUT_SECONDS   default 60 (per wait step)
//   DB_PASSWORD                  default: read from .env (used only to query the outbox and the
//                                fraud evaluation log via docker compose; never printed)
//
// Exit codes: 0 demo complete, 1 workflow failed or timed out, 2 preflight or configuration error.

import { spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import fs from 'node:fs'
import { pathToFileURL, fileURLToPath } from 'node:url'
import path from 'node:path'

export const CUSTOMER = Object.freeze({ firstName: 'Omphile', lastName: 'Mohlala' })
// Only used when the customer does not exist yet. A reserved example domain, not a real mailbox.
export const DEMO_EMAIL = 'omphile.mohlala.fraud-demo@example.com'
export const DEFAULT_AMOUNT = 60000
export const DEFAULT_CURRENCY = 'ZAR'
export const DEFAULT_TIMEOUT_SECONDS = 60
export const DEFAULT_GATEWAY = 'http://localhost:8000'
const ACCOUNT_TYPE = 'SAVINGS'
const FRAUD_RULE = 'HIGH_AMOUNT'
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

export class DemoError extends Error {
  constructor(message, exitCode = 1) {
    super(message)
    this.name = 'DemoError'
    this.exitCode = exitCode
  }
}

// ---------------------------------------------------------------- pure logic (unit tested)

const norm = (value) => String(value ?? '').trim().replace(/\s+/g, ' ').toLowerCase()

export function parseArgs(argv, env = process.env) {
  const opts = {
    amount: DEFAULT_AMOUNT,
    currency: DEFAULT_CURRENCY,
    timeoutSeconds: Number(env.FRAUD_DEMO_TIMEOUT_SECONDS ?? DEFAULT_TIMEOUT_SECONDS),
    gateway: env.GATEWAY_URL ?? DEFAULT_GATEWAY,
    help: false,
  }
  for (let i = 0; i < argv.length; i++) {
    const flag = argv[i]
    const take = () => {
      const value = argv[++i]
      if (value === undefined) throw new DemoError(`${flag} needs a value`, 2)
      return value
    }
    switch (flag) {
      case '--amount': opts.amount = Number(take()); break
      case '--currency': opts.currency = take(); break
      case '--timeout': opts.timeoutSeconds = Number(take()); break
      case '--help':
      case '-h': opts.help = true; break
      default: throw new DemoError(`unknown option ${flag}`, 2)
    }
  }
  if (!(opts.timeoutSeconds > 0)) throw new DemoError('the timeout must be a positive number of seconds', 2)
  return opts
}

/** Customers whose first and last name match, ignoring case and extra spaces. */
export function findCustomers(customers) {
  return customers.filter((c) =>
    norm(c.firstName) === norm(CUSTOMER.firstName) && norm(c.lastName) === norm(CUSTOMER.lastName))
}

/** Reuse rule: the record with the demo email if there is one, otherwise the oldest match. */
export function chooseCustomer(matches) {
  if (matches.length === 0) return null
  const demo = matches.find((c) => norm(c.email) === norm(DEMO_EMAIL))
  if (demo) return demo
  return [...matches].sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)))[0]
}

/** An active savings account if there is one, otherwise any active account. */
export function chooseAccount(accounts) {
  const active = accounts.filter((a) => a.status === 'ACTIVE')
  return active.find((a) => a.accountType === ACCOUNT_TYPE) ?? active[0] ?? null
}

/** The HIGH_AMOUNT threshold as the fraud service describes it (GET /fraud/rules). */
export function highAmountThreshold(rules) {
  const rule = rules.find((r) => r.ruleId === FRAUD_RULE)
  if (!rule) throw new DemoError(`the fraud service does not list the ${FRAUD_RULE} rule`, 2)
  if (!rule.enabled) throw new DemoError(`the ${FRAUD_RULE} rule is disabled`, 2)
  const match = /(\d+(?:\.\d+)?)/.exec(rule.condition ?? '')
  if (!match) throw new DemoError(`cannot read the ${FRAUD_RULE} threshold from "${rule.condition}"`, 2)
  return Number(match[1])
}

/** A run ID such as DEMO-20261120101500-9F2A1C. Digits and hex only, so the description is not EFT-like. */
export function newRunId(now = new Date(), bytes = randomBytes(3)) {
  const stamp = now.toISOString().replace(/\D/g, '').slice(0, 14)
  return `DEMO-${stamp}-${bytes.toString('hex').toUpperCase()}`
}

/**
 * The transaction body. A DEBIT at or above the HIGH_AMOUNT threshold, in ZAR, with no EFT
 * wording, so only the HIGH_AMOUNT rule is expected to fire. Refuses values the rule would not flag.
 */
export function buildDemoTransaction({ accountId, amount, currency, threshold, runId }) {
  if (!Number.isFinite(amount) || amount <= 0) throw new DemoError('--amount must be a positive number', 2)
  if (amount < threshold) {
    throw new DemoError(`--amount ${amount} is below the ${FRAUD_RULE} threshold of ${threshold}, so the rule would not fire`, 2)
  }
  if (!/^[A-Za-z]{3}$/.test(currency ?? '')) throw new DemoError('--currency must be a 3-letter code', 2)
  return {
    accountId,
    type: 'DEBIT',
    amount,
    currency: currency.toUpperCase(),
    description: `Fraud demo ${runId}: high-value purchase`,
  }
}

/**
 * Polls `check` until it returns a truthy value or the timeout passes. Returns the value, or null on timeout.
 * `check` may throw to stop early.
 */
export async function pollUntil(check, {
  timeoutMs,
  intervalMs = 1000,
  now = Date.now,
  sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
}) {
  const start = now()
  for (;;) {
    const value = await check()
    if (value) return value
    if (now() - start >= timeoutMs) return null
    await sleep(intervalMs)
  }
}

/** Reasons the fraud alert and case do not point at each other and at the demo records. Empty when consistent. */
export function linkageProblems({ transaction, fraud, caseRecord, customer, account }) {
  const problems = []
  if (fraud.transactionId !== transaction.id) problems.push('the fraud alert points at a different transaction')
  if (fraud.customerId !== customer.id) problems.push('the fraud alert points at a different customer')
  if (fraud.accountId !== account.id) problems.push('the fraud alert points at a different account')
  if (fraud.caseId !== caseRecord.id) problems.push('the fraud alert does not hold the case ID')
  if (caseRecord.sourceFraudId !== fraud.id) problems.push('the case is not linked to the fraud alert')
  if (caseRecord.transactionId !== transaction.id) problems.push('the case points at a different transaction')
  if (caseRecord.customerId !== customer.id) problems.push('the case points at a different customer')
  return problems
}

export function summaryLines({ customer, transaction, fraud, caseRecord }) {
  return [
    `Customer:       ${customer.firstName} ${customer.lastName}`,
    `Transaction ID: ${transaction.id}`,
    `Fraud Score:    ${fraud.riskScore} (${fraud.riskLevel}, rule ${fraud.detectionRule})`,
    `Alert ID:       ${fraud.id} (${fraud.fraudReference})`,
    `Case ID:        ${caseRecord.id} (${caseRecord.caseNumber})`,
    `Case Status:    ${caseRecord.status}`,
  ]
}

/** DB_PASSWORD from the environment, else from the DB_PASSWORD line of .env. The value is never printed. */
export function dbPassword(env = process.env, readFile = (file) => fs.readFileSync(file, 'utf8')) {
  if (env.DB_PASSWORD) return env.DB_PASSWORD
  try {
    const match = /^DB_PASSWORD=(.*)$/m.exec(readFile(path.join(REPO_ROOT, '.env')))
    if (match && match[1].trim()) return match[1].trim()
  } catch {
    // fall through to the error below
  }
  throw new DemoError('DB_PASSWORD is not set in the environment or in .env', 2)
}

// ---------------------------------------------------------------- I/O

export function createApi(gateway, fetchImpl = fetch) {
  return async function api(method, route, body) {
    let res
    try {
      res = await fetchImpl(gateway + route, {
        method,
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
    } catch (err) {
      const reason = err.cause?.code ?? err.message
      throw new DemoError(`gateway not reachable at ${gateway} (${reason}). Is the platform running? See scripts/dev/bootstrap.sh`, 2)
    }
    const text = await res.text()
    let data = null
    try { data = text ? JSON.parse(text) : null } catch { data = text }
    return { status: res.status, data }
  }
}

/** Runs a read-only query against a service database through the postgres container. */
export function queryDb(password, database, sql) {
  const r = spawnSync('docker', [
    'compose', 'exec', '-T', '-e', `PGPASSWORD=${password}`, 'postgres',
    'psql', '-U', 'app_user', '-d', database, '-Atc', sql,
  ], { encoding: 'utf8', cwd: REPO_ROOT })
  if (r.status !== 0) {
    throw new DemoError(`cannot query ${database} through docker compose (is the stack running?): ${(r.stderr || '').trim()}`, 2)
  }
  return r.stdout.trim()
}

const log = (line = '') => console.log(line)
const ok = (line) => log(`[OK] ${line}`)
const warn = (line) => log(`[WARN] ${line}`)

function fail(message, exitCode = 1) {
  throw new DemoError(message, exitCode)
}

async function expectOk(api, method, route, service) {
  const res = await api(method, route)
  if (res.status !== 200) fail(`${service} did not answer ${method} ${route} (HTTP ${res.status})`, 2)
  return res.data
}

// ---------------------------------------------------------------- the workflow

export async function run(opts, { api = createApi(opts.gateway), password = null } = {}) {
  const timeoutMs = opts.timeoutSeconds * 1000
  const runId = newRunId()
  const db = password ?? dbPassword()

  log('Starting fraud demonstration for Omphile Mohlala...')
  log(`Run reference: ${runId}`)
  log()

  // 1. Preflight: every service the flow touches must answer.
  const allCustomers = await expectOk(api, 'GET', '/api/v1/customers', 'customer-service')
  const rules = await expectOk(api, 'GET', '/api/v1/fraud/rules', 'fraud-detection-service')
  await expectOk(api, 'GET', '/api/v1/cases?size=1', 'case-service')
  await expectOk(api, 'GET', '/api/v1/notifications?size=1', 'notification-service')
  queryDb(db, 'fraud_detection_db', 'select 1')
  ok('Services reachable')

  // 2. Customer: reuse, never overwrite. Create only when none exists.
  const matches = findCustomers(allCustomers)
  let customer = chooseCustomer(matches)
  if (!customer) {
    const created = await api('POST', '/api/v1/customers', {
      firstName: CUSTOMER.firstName, lastName: CUSTOMER.lastName,
      email: DEMO_EMAIL, phoneNumber: null, clientType: 'INDIVIDUAL',
    })
    if (created.status !== 201) {
      fail(`could not create the customer (HTTP ${created.status}): ${JSON.stringify(created.data)}`, 2)
    }
    customer = created.data
    ok(`Customer created (${customer.id})`)
  } else {
    ok(`Customer identified (${customer.id})${matches.length > 1 ? `, one of ${matches.length} matching records` : ''}`)
  }

  // 3. Account: reuse an active account, or open one.
  const accounts = await expectOk(api, 'GET', `/api/v1/accounts/customer/${customer.id}`, 'account-service')
  let account = chooseAccount(accounts)
  if (!account) {
    const created = await api('POST', '/api/v1/accounts', { customerId: customer.id, accountType: ACCOUNT_TYPE })
    if (created.status !== 201) fail(`could not open an account (HTTP ${created.status})`, 2)
    account = created.data
    ok(`Account created (${account.accountType})`)
  } else {
    ok(`Account identified (${account.accountType}, ${account.accountNumber})`)
  }
  await expectOk(api, 'GET', `/api/v1/transactions/account/${account.id}/balance`, 'transaction-service')

  // 4. Suspicious transaction, built to meet the configured HIGH_AMOUNT condition.
  const threshold = highAmountThreshold(rules)
  const body = buildDemoTransaction({
    accountId: account.id, amount: opts.amount, currency: opts.currency, threshold, runId,
  })
  const created = await api('POST', '/api/v1/transactions', body)
  if (created.status !== 201) fail(`transaction-service rejected the demo transaction (HTTP ${created.status}): ${JSON.stringify(created.data)}`)
  const transaction = created.data
  ok(`Suspicious transaction created (${transaction.id}, ${body.amount} ${body.currency} ${body.type})`)

  // 5. The transaction event reached Kafka: the outbox row is PUBLISHED.
  const published = await pollUntil(() => {
    const status = queryDb(db, 'transaction_service_db',
      `select status from outbox_events where message_key='${transaction.id}' and topic='transaction.events'`)
    if (status === 'FAILED') fail(`the transaction event could not be published (outbox status FAILED for ${transaction.id})`)
    return status === 'PUBLISHED'
  }, { timeoutMs })
  if (!published) fail(`the transaction event was not published within ${opts.timeoutSeconds}s (check outbox-relay in transaction-service)`)
  ok('Transaction event published')

  // 6. The fraud model evaluated the transaction. The outcome comes from the fraud service's own log.
  const outcome = await pollUntil(() => {
    const value = queryDb(db, 'fraud_detection_db',
      `select outcome from processed_transactions where transaction_id='${transaction.id}'`)
    return value || null
  }, { timeoutMs })
  if (!outcome) fail(`the fraud model did not evaluate the transaction within ${opts.timeoutSeconds}s (check fraud-detection-service logs)`)
  if (outcome !== 'DETECTED') {
    fail(`the fraud model evaluated the transaction but did not flag it (outcome ${outcome}). Rules: ${JSON.stringify(rules)}`)
  }
  ok(`Fraud model evaluated transaction (outcome ${outcome})`)

  // 7. Fraud alert, created by the normal alert workflow.
  const fraud = await pollUntil(async () => {
    const res = await api('GET', `/api/v1/fraud/transaction/${transaction.id}`)
    if (res.status === 200) return res.data
    if (res.status === 404) return null
    fail(`fraud-detection-service returned HTTP ${res.status} for the transaction`)
  }, { timeoutMs })
  if (!fraud) fail(`no fraud alert appeared within ${opts.timeoutSeconds}s`)
  ok(`Fraud alert generated (${fraud.fraudReference}, ${fraud.riskLevel} ${fraud.riskScore}, rule ${fraud.detectionRule})`)

  // 8. Case, opened by case-service and linked back to the alert.
  const withCase = await pollUntil(async () => {
    const res = await api('GET', `/api/v1/fraud/${fraud.id}`)
    return res.status === 200 && res.data.caseId ? res.data : null
  }, { timeoutMs })
  if (!withCase) fail(`no case was linked to the fraud alert within ${opts.timeoutSeconds}s (check case-service logs)`)
  const caseRes = await api('GET', `/api/v1/cases/${withCase.caseId}`)
  if (caseRes.status !== 200) fail(`case ${withCase.caseId} could not be read (HTTP ${caseRes.status})`)
  const caseRecord = caseRes.data
  ok(`Fraud case created (${caseRecord.caseNumber})`)

  const problems = linkageProblems({
    transaction, fraud: withCase, caseRecord, customer, account,
  })
  if (problems.length) fail(`the records are not linked correctly: ${problems.join('; ')}`)

  // 9. Notification (optional step: reported, not required for success).
  const notified = await pollUntil(async () => {
    const res = await api('GET', '/api/v1/notifications?size=50')
    return Array.isArray(res.data) && res.data.some((n) => n.caseId === caseRecord.id && n.type === 'CASE_CREATED')
  }, { timeoutMs: Math.min(timeoutMs, 15000) })
  if (notified) ok('Case notification sent')
  else warn('no CASE_CREATED notification found for the case (notification-service may be behind)')

  log()
  log('FRAUD DEMONSTRATION COMPLETE')
  log()
  for (const line of summaryLines({ customer, transaction, fraud: withCase, caseRecord })) log(line)
  return { customer, account, transaction, fraud: withCase, caseRecord }
}

const USAGE = `Usage: scripts/dev/seed-fraud-demo.sh [--amount <ZAR>] [--currency <code>] [--timeout <seconds>]

Creates one suspicious transaction for Omphile Mohlala and follows it through the real fraud
and case workflow. Reuses the customer and account when they exist.
Defaults: --amount ${DEFAULT_AMOUNT} --currency ${DEFAULT_CURRENCY} --timeout ${DEFAULT_TIMEOUT_SECONDS}
Exit codes: 0 complete, 1 workflow failed or timed out, 2 preflight or configuration error.`

export async function main(argv, { env = process.env } = {}) {
  try {
    const opts = parseArgs(argv, env)
    if (opts.help) {
      log(USAGE)
      return 0
    }
    await run(opts)
    return 0
  } catch (err) {
    if (err instanceof DemoError) {
      console.error(`\n[FAIL] ${err.message}`)
      return err.exitCode
    }
    console.error(`\n[FAIL] unexpected error: ${err.message}`)
    return 1
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  main(process.argv.slice(2)).then((code) => process.exit(code))
}
