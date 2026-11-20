// Unit tests for the fraud demonstration's decision logic. Run: node --test scripts/dev/
// The end-to-end run against the live platform is scripts/dev/seed-fraud-demo.sh itself.

import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  DEMO_EMAIL, DemoError, buildDemoTransaction, chooseAccount, chooseCustomer, dbPassword,
  findCustomers, highAmountThreshold, linkageProblems, newRunId, parseArgs, pollUntil, summaryLines,
} from './seed-fraud-demo.mjs'

const omphile = (over = {}) => ({
  id: 'c-1', firstName: 'Omphile', lastName: 'Mohlala', email: 'omphile.mohlala@umuzi.org',
  createdAt: '2026-11-18T18:00:00', ...over,
})

test('findCustomers matches the name ignoring case and extra spaces, and nothing else', () => {
  const list = [
    omphile(),
    omphile({ id: 'c-2', firstName: '  omphile ', lastName: 'MOHLALA' }),
    omphile({ id: 'c-3', firstName: 'Omphile', lastName: 'Mohlal' }),
    omphile({ id: 'c-4', firstName: 'Thabo', lastName: 'Mohlala' }),
  ]
  assert.deepEqual(findCustomers(list).map((c) => c.id), ['c-1', 'c-2'])
})

test('chooseCustomer returns null when no customer matches (the caller then creates one)', () => {
  assert.equal(chooseCustomer([]), null)
})

test('chooseCustomer reuses the record with the demo email in preference to others', () => {
  const older = omphile({ id: 'old', createdAt: '2026-01-01T00:00:00' })
  const demo = omphile({ id: 'demo', email: DEMO_EMAIL, createdAt: '2026-11-01T00:00:00' })
  assert.equal(chooseCustomer([older, demo]).id, 'demo')
})

test('chooseCustomer without a demo record picks the oldest, so repeat runs always reuse the same one', () => {
  const newer = omphile({ id: 'new', createdAt: '2026-11-18T00:00:00' })
  const oldest = omphile({ id: 'oldest', createdAt: '2026-01-01T00:00:00' })
  assert.equal(chooseCustomer([newer, oldest]).id, 'oldest')
})

test('chooseCustomer does not modify the record it returns', () => {
  const c = omphile()
  const before = JSON.stringify(c)
  chooseCustomer([c])
  assert.equal(JSON.stringify(c), before)
})

test('chooseAccount prefers an active savings account and ignores closed accounts', () => {
  const accounts = [
    { id: 'closed', accountType: 'SAVINGS', status: 'CLOSED' },
    { id: 'cheque', accountType: 'CHEQUE', status: 'ACTIVE' },
    { id: 'savings', accountType: 'SAVINGS', status: 'ACTIVE' },
  ]
  assert.equal(chooseAccount(accounts).id, 'savings')
  assert.equal(chooseAccount([accounts[1]]).id, 'cheque')
  assert.equal(chooseAccount([accounts[0]]), null)
})

test('highAmountThreshold reads the threshold from the rule the fraud service reports', () => {
  const rules = [{ ruleId: 'HIGH_AMOUNT', enabled: true, condition: 'Amount at or above 50000', weight: 70 }]
  assert.equal(highAmountThreshold(rules), 50000)
})

test('highAmountThreshold fails clearly when the rule is missing or disabled', () => {
  assert.throws(() => highAmountThreshold([]), /does not list the HIGH_AMOUNT rule/)
  assert.throws(
    () => highAmountThreshold([{ ruleId: 'HIGH_AMOUNT', enabled: false, condition: 'Amount at or above 50000' }]),
    /is disabled/)
})

const rule = { threshold: 50000 }
const build = (amount, extra = {}) => buildDemoTransaction({
  accountId: 'acc-1', amount, currency: 'zar', threshold: rule.threshold, runId: 'DEMO-20261120101500-AB12CD', ...extra,
})

test('buildDemoTransaction builds a DEBIT at the amount, in upper-case ZAR, tagged with the run ID', () => {
  const tx = build(60000)
  assert.deepEqual(tx, {
    accountId: 'acc-1', type: 'DEBIT', amount: 60000, currency: 'ZAR',
    description: 'Fraud demo DEMO-20261120101500-AB12CD: high-value purchase',
  })
})

test('buildDemoTransaction never produces a description the LARGE_EFT rule would match', () => {
  assert.equal(build(60000).description.toUpperCase().includes('EFT'), false)
})

test('buildDemoTransaction refuses an amount the HIGH_AMOUNT rule would not flag', () => {
  assert.throws(() => build(49999.99), (e) => e instanceof DemoError && e.exitCode === 2 && /would not fire/.test(e.message))
})

test('buildDemoTransaction accepts an amount exactly at the threshold, as the rule does', () => {
  assert.equal(build(50000).amount, 50000)
})

test('buildDemoTransaction rejects a non-positive amount and a malformed currency', () => {
  assert.throws(() => build(0), /positive number/)
  assert.throws(() => build(Number.NaN), /positive number/)
  assert.throws(() => build(60000, { currency: 'RANDS' }), /3-letter code/)
})

test('newRunId has a fixed shape, is unique per call, and contains no EFT letters', () => {
  const at = new Date('2026-11-20T10:15:00Z')
  const a = newRunId(at, Buffer.from([0x9f, 0x2a, 0x1c]))
  const b = newRunId(at, Buffer.from([0x01, 0x02, 0x03]))
  assert.match(a, /^DEMO-\d{14}-[0-9A-F]{6}$/)
  assert.notEqual(a, b)
  assert.equal(a.includes('EFT'), false)
})

test('pollUntil returns the first truthy value without waiting further', async () => {
  let calls = 0
  const value = await pollUntil(() => (++calls === 3 ? 'found' : null), { timeoutMs: 10000, sleep: async () => {} })
  assert.equal(value, 'found')
  assert.equal(calls, 3)
})

test('pollUntil returns null when the timeout passes, without an unbounded loop', async () => {
  let t = 0
  let calls = 0
  const value = await pollUntil(() => { calls++; return null }, {
    timeoutMs: 5000, intervalMs: 1000, now: () => t, sleep: async (ms) => { t += ms },
  })
  assert.equal(value, null)
  assert.ok(calls >= 5 && calls <= 7, `expected about 5-6 checks, got ${calls}`)
})

test('pollUntil propagates an error thrown by the check (a failure stops the wait early)', async () => {
  await assert.rejects(
    pollUntil(() => { throw new DemoError('boom') }, { timeoutMs: 10000, sleep: async () => {} }),
    /boom/)
})

const consistent = () => ({
  transaction: { id: 'tx' },
  customer: { id: 'cu' },
  account: { id: 'ac' },
  fraud: { id: 'fr', transactionId: 'tx', customerId: 'cu', accountId: 'ac', caseId: 'ca' },
  caseRecord: { id: 'ca', sourceFraudId: 'fr', transactionId: 'tx', customerId: 'cu' },
})

test('linkageProblems is empty when the alert and case point at each other and at the demo records', () => {
  assert.deepEqual(linkageProblems(consistent()), [])
})

test('linkageProblems reports each broken link', () => {
  const broken = consistent()
  broken.fraud.caseId = 'other-case'
  broken.caseRecord.sourceFraudId = 'other-fraud'
  broken.fraud.transactionId = 'other-tx'
  const problems = linkageProblems(broken)
  assert.equal(problems.length, 3)
  assert.ok(problems.some((p) => /does not hold the case ID/.test(p)))
  assert.ok(problems.some((p) => /not linked to the fraud alert/.test(p)))
  assert.ok(problems.some((p) => /different transaction/.test(p)))
})

test('summaryLines prints the values it was given, not placeholders', () => {
  const s = consistent()
  s.fraud = { ...s.fraud, riskScore: 70, riskLevel: 'HIGH', detectionRule: 'HIGH_AMOUNT', fraudReference: 'FRD-1' }
  s.caseRecord = { ...s.caseRecord, caseNumber: 'CASE-1', status: 'NEW' }
  s.customer = { ...s.customer, firstName: 'Omphile', lastName: 'Mohlala' }
  s.transaction = { id: 'tx' }
  const text = summaryLines(s).join('\n')
  assert.match(text, /Fraud Score:\s+70 \(HIGH, rule HIGH_AMOUNT\)/)
  assert.match(text, /Case Status:\s+NEW/)
  assert.match(text, /Customer:\s+Omphile Mohlala/)
})

test('parseArgs uses the defaults and reads overrides', () => {
  const defaults = parseArgs([], {})
  assert.equal(defaults.amount, 60000)
  assert.equal(defaults.currency, 'ZAR')
  assert.equal(defaults.timeoutSeconds, 60)
  assert.equal(defaults.gateway, 'http://localhost:8000')
  const custom = parseArgs(['--amount', '75000', '--currency', 'usd', '--timeout', '120'], { GATEWAY_URL: 'http://gw' })
  assert.equal(custom.amount, 75000)
  assert.equal(custom.currency, 'usd')
  assert.equal(custom.timeoutSeconds, 120)
  assert.equal(custom.gateway, 'http://gw')
})

test('parseArgs rejects unknown options, missing values and a non-positive timeout', () => {
  assert.throws(() => parseArgs(['--nope'], {}), /unknown option/)
  assert.throws(() => parseArgs(['--amount'], {}), /needs a value/)
  assert.throws(() => parseArgs(['--timeout', '0'], {}), /positive number/)
})

test('dbPassword prefers the environment and reads DB_PASSWORD from .env otherwise, never echoing it', () => {
  assert.equal(dbPassword({ DB_PASSWORD: 'from-env' }, () => { throw new Error('unused') }), 'from-env')
  assert.equal(dbPassword({}, () => 'A=1\nDB_PASSWORD=from-file\n'), 'from-file')
  assert.throws(() => dbPassword({}, () => 'A=1\n'), /not set/)
})

test('main reports a bad option as a configuration error (exit 2) instead of crashing', async () => {
  const { main } = await import('./seed-fraud-demo.mjs')
  const log = console.error
  console.error = () => {}
  try {
    assert.equal(await main(['--bogus'], { env: {} }), 2)
  } finally {
    console.error = log
  }
})
