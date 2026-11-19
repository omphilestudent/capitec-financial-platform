// End-to-end verification of the Capitec platform.
//
// Every step goes through the real APIs (via the API gateway) and checks the
// outcome in PostgreSQL and Kafka. Nothing is mocked: records are created by the
// services, and the checks read what the services actually stored and published.
//
// Requirements: the platform running (see README: docker compose up -d --wait).
// Commands go through docker compose, so any compose project name works (for example an
// isolated test stack). Set COMPOSE_PROJECT_NAME to target a non-default project.
//
// Run:  node scripts/e2e/verify-platform.mjs

import { spawnSync } from 'node:child_process'

const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8000'
const DB_PASSWORD = process.env.DB_PASSWORD || 'local_dev_password'
const POSTGRES = 'postgres'
const KAFKA = 'kafka'
const KAFKA_BIN = '/opt/kafka/bin'

const RUN = Date.now()
const results = []

function record(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`)
}

async function api(method, path, body) {
  const res = await fetch(GATEWAY + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = text }
  return { status: res.status, data }
}

function sql(db, query) {
  const r = spawnSync('docker', [
    'compose', 'exec', '-T', '-e', `PGPASSWORD=${DB_PASSWORD}`, POSTGRES,
    'psql', '-U', 'app_user', '-d', db, '-Atc', query,
  ], { encoding: 'utf8' })
  if (r.status !== 0) throw new Error(`psql failed: ${r.stderr}`)
  return r.stdout.trim()
}

function topicMessages(topic, { headers = false } = {}) {
  const args = [
    'compose', 'exec', '-T', KAFKA, `${KAFKA_BIN}/kafka-console-consumer.sh`,
    '--bootstrap-server', 'localhost:9092', '--topic', topic,
    '--from-beginning', '--timeout-ms', '6000', '--property', 'print.key=true',
  ]
  if (headers) args.push('--property', 'print.headers=true')
  const r = spawnSync('docker', args, { encoding: 'utf8' })
  return r.stdout.split('\n').filter(Boolean)
}

async function waitFor(check, timeoutMs = 15000) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const value = await check()
    if (value) return value
    await new Promise((r) => setTimeout(r, 500))
  }
  return null
}

function assertEnvironment() {
  const running = spawnSync('docker', ['compose', 'ps', '--status', 'running', '--services'], { encoding: 'utf8' }).stdout
  if (!running.split('\n').includes(POSTGRES) || !running.split('\n').includes(KAFKA)) {
    throw new Error(`Services ${POSTGRES} and ${KAFKA} must be running (docker compose up -d --wait)`)
  }
}

async function main() {
  assertEnvironment()

  console.log('\nService health (via gateway)')
  const health = await api('GET', '/api/v1/services/health')
  const reachable = Array.isArray(health.data) && health.data.every((s) => s.reachable && s.status === 'UP')
  record('all three services report UP', health.status === 200 && reachable,
    Array.isArray(health.data) ? health.data.map((s) => `${s.name}=${s.status}`).join(', ') : `HTTP ${health.status}`)

  console.log('\nTest 1: customer creation -> CustomerCreated event')
  const email = `e2e.${RUN}@example.com`
  const customer = await api('POST', '/api/v1/customers', {
    firstName: 'E2E', lastName: `Customer ${RUN}`, email, phoneNumber: '+27 82 555 0100', clientType: 'INDIVIDUAL',
  })
  record('POST /customers returns 201', customer.status === 201, `HTTP ${customer.status}`)
  const customerId = customer.data?.id

  const persisted = sql('customer_service_db', `select count(*) from customers where id='${customerId}'`)
  record('customer persisted in customer_service_db', persisted === '1')

  const customerOutbox = await waitFor(() => {
    const s = sql('customer_service_db',
      `select status from outbox_events where message_key='${customerId}'`)
    return s === 'PUBLISHED' ? s : null
  })
  record('outbox row for customer is PUBLISHED', customerOutbox === 'PUBLISHED')

  const customerMsg = topicMessages('customer.events')
    .find((line) => line.startsWith(customerId))
  record('CustomerCreated message on customer.events, keyed by customer ID', Boolean(customerMsg))
  let customerEvent = null
  if (customerMsg) {
    customerEvent = JSON.parse(customerMsg.slice(customerMsg.indexOf('\t') + 1))
    record('event has eventId, customerId, names, email, clientType, occurredAt',
      Boolean(customerEvent.eventId && customerEvent.customerId === customerId &&
        customerEvent.firstName === 'E2E' && customerEvent.email === email &&
        customerEvent.clientType === 'INDIVIDUAL' && customerEvent.occurredAt))
  }

  console.log('\nTest 2: account-service consumes the event')
  const reference = await waitFor(() => {
    const n = sql('account_service_db', `select count(*) from customer_refs where customer_id='${customerId}'`)
    return n === '1' ? n : null
  })
  record('customer reference stored in account_service_db', reference === '1')

  console.log('\nTest 3: duplicate event delivery does not duplicate the reference')
  if (customerMsg) {
    // Re-produce the exact event that was published, as a duplicate delivery.
    const dup = spawnSync('docker', [
      'compose', 'exec', '-T', KAFKA, `${KAFKA_BIN}/kafka-console-producer.sh`,
      '--bootstrap-server', 'localhost:9092', '--topic', 'customer.events',
      '--property', 'parse.key=true', '--property', 'key.separator=\t',
    ], { input: customerMsg + '\n', encoding: 'utf8' })
    record('duplicate event re-produced', dup.status === 0)
    await new Promise((r) => setTimeout(r, 4000))
    const after = sql('account_service_db', `select count(*) from customer_refs where customer_id='${customerId}'`)
    record('still exactly one customer reference after duplicate', after === '1', `count=${after}`)
  } else {
    record('duplicate event re-produced', false, 'no original event found')
  }

  console.log('\nTest 4: account creation -> AccountOpened event')
  const account = await api('POST', '/api/v1/accounts', { customerId, accountType: 'CHEQUE' })
  record('POST /accounts returns 201', account.status === 201, `HTTP ${account.status}`)
  const accountId = account.data?.id
  record('account persisted in account_service_db',
    sql('account_service_db', `select count(*) from accounts where id='${accountId}'`) === '1')
  const accountOutbox = await waitFor(() =>
    sql('account_service_db', `select status from outbox_events where message_key='${accountId}'`) === 'PUBLISHED')
  record('outbox row for account is PUBLISHED', Boolean(accountOutbox))
  const accountMsg = topicMessages('account.events').find((l) => l.startsWith(accountId))
  record('AccountOpened message on account.events, keyed by account ID', Boolean(accountMsg))

  console.log('\nTest 5: transaction creation -> TransactionCreated event')
  const tx = await api('POST', '/api/v1/transactions', {
    accountId, type: 'CREDIT', amount: 1250.5, currency: 'zar', description: `E2E deposit ${RUN}`,
  })
  record('POST /transactions returns 201', tx.status === 201, `HTTP ${tx.status}`)
  const txId = tx.data?.id
  record('transaction persisted in transaction_service_db',
    sql('transaction_service_db', `select count(*) from transactions where id='${txId}'`) === '1')
  const txOutbox = await waitFor(() =>
    sql('transaction_service_db', `select count(*) from outbox_events where message_key='${txId}' and status='PUBLISHED'`) === '2')
  record('outbox rows for transaction (transaction.events and transaction-events) are PUBLISHED', Boolean(txOutbox))

  const txMsg = topicMessages('transaction-events').find((l) => l.startsWith(txId))
  record('TransactionCreated message on transaction-events, keyed by transaction ID', Boolean(txMsg))
  if (txMsg) {
    const event = JSON.parse(txMsg.slice(txMsg.indexOf('\t') + 1))
    const ok = event.eventId && event.transactionId === txId && event.accountId === accountId &&
      event.transactionReference === tx.data.transactionReference && event.type === 'CREDIT' &&
      Number(event.amount) === 1250.5 && event.currency === 'ZAR' && event.status === 'PENDING' &&
      typeof event.createdAt === 'string'
    record('event has eventId, transactionId, accountId, reference, type, amount, currency, status, createdAt', ok,
      `createdAt=${event.createdAt}`)
  }

  console.log('\nTest 6: transaction retrieval')
  const byId = await api('GET', `/api/v1/transactions/${txId}`)
  record('GET /transactions/{id} returns the created transaction', byId.status === 200 && byId.data?.id === txId)
  const byAccount = await api('GET', `/api/v1/transactions/account/${accountId}`)
  record('GET /transactions/account/{accountId} includes it',
    byAccount.status === 200 && byAccount.data.some((t) => t.id === txId))
  const recent = await api('GET', '/api/v1/transactions')
  record('GET /transactions (recent) includes it', recent.status === 200 && recent.data.some((t) => t.id === txId))

  console.log('\nTest 7: failure scenarios (no records must be created)')
  const ghostCustomer = '00000000-0000-4000-8000-000000000001'
  const ghostAccount = '00000000-0000-4000-8000-000000000002'

  const accBefore = sql('account_service_db', 'select count(*) from accounts')
  const noCust = await api('POST', '/api/v1/accounts', { customerId: ghostCustomer, accountType: 'SAVINGS' })
  record('account for nonexistent customer -> 404', noCust.status === 404, `HTTP ${noCust.status}: ${noCust.data?.message}`)
  record('no account created for nonexistent customer',
    sql('account_service_db', 'select count(*) from accounts') === accBefore)

  const txBefore = sql('transaction_service_db', 'select count(*) from transactions')
  const noAcct = await api('POST', '/api/v1/transactions', { accountId: ghostAccount, type: 'DEBIT', amount: 10, currency: 'ZAR' })
  record('transaction for nonexistent account -> 404', noAcct.status === 404, `HTTP ${noAcct.status}: ${noAcct.data?.message}`)
  record('no transaction created for nonexistent account',
    sql('transaction_service_db', 'select count(*) from transactions') === txBefore)

  const invalid = await api('POST', '/api/v1/transactions', { accountId, type: 'CREDIT', amount: 0, currency: 'X' })
  record('invalid transaction (zero amount, bad currency) -> 400 with field errors',
    invalid.status === 400 && invalid.data?.fieldErrors && Object.keys(invalid.data.fieldErrors).length >= 2,
    `HTTP ${invalid.status}`)

  const badType = await api('POST', '/api/v1/transactions', { accountId, type: 'REFUND', amount: 5, currency: 'ZAR' })
  record('unknown transaction type -> 400', badType.status === 400, `HTTP ${badType.status}`)

  const badId = await api('GET', '/api/v1/transactions/not-a-uuid')
  record('malformed transaction ID -> 400', badId.status === 400, `HTTP ${badId.status}`)

  const missing = await api('GET', `/api/v1/transactions/${ghostAccount}`)
  record('unknown transaction ID -> 404', missing.status === 404, `HTTP ${missing.status}`)

  const badCustomer = await api('POST', '/api/v1/customers', { firstName: '', lastName: '', email: 'nope', clientType: 'INDIVIDUAL' })
  record('invalid customer -> 400', badCustomer.status === 400, `HTTP ${badCustomer.status}`)

  const dupEmail = await api('POST', '/api/v1/customers', {
    firstName: 'E2E', lastName: 'Dup', email, phoneNumber: null, clientType: 'INDIVIDUAL',
  })
  record('duplicate customer email -> 409', dupEmail.status === 409, `HTTP ${dupEmail.status}`)

  console.log('\nTest 8: malformed event goes to the dead-letter topic')
  const poison = spawnSync('docker', [
    'compose', 'exec', '-T', KAFKA, `${KAFKA_BIN}/kafka-console-producer.sh`,
    '--bootstrap-server', 'localhost:9092', '--topic', 'customer.events',
    '--property', 'parse.key=true', '--property', 'key.separator=\t',
  ], { input: `poison-${RUN}\t{not valid json\n`, encoding: 'utf8' })
  record('malformed message produced', poison.status === 0)
  const dlt = await waitFor(async () => {
    const lines = topicMessages('customer.events-dlt', { headers: true })
    return lines.some((l) => l.includes('DeserializationException') || l.includes('poison')) ? lines : null
  }, 20000)
  record('malformed message appears in customer.events-dlt', Boolean(dlt))

  console.log('\nTest 9: UI-facing reads and writes persisted')
  const customers = await api('GET', '/api/v1/customers')
  record('GET /customers lists the created customer',
    customers.status === 200 && customers.data.some((c) => c.id === customerId))
  const accounts = await api('GET', `/api/v1/accounts/customer/${customerId}`)
  record('GET /accounts/customer/{id} lists the created account',
    accounts.status === 200 && accounts.data.some((a) => a.id === accountId))

  const passed = results.filter((r) => r.ok).length
  const failed = results.filter((r) => !r.ok)
  console.log(`\n${passed} passed, ${failed.length} failed`)
  if (failed.length) {
    console.log('Failed:\n' + failed.map((f) => `  - ${f.name} ${f.detail}`).join('\n'))
    process.exit(1)
  }
}

main().catch((err) => {
  console.error('Verification aborted:', err.message)
  process.exit(2)
})
