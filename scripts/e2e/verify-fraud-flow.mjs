// End-to-end verification of the fraud and case flow.
//
// Creates real records through the API (customer, account, transactions), lets the
// services react through Kafka, and checks what they persisted and published.
// Duplicate deliveries are produced by re-sending the exact message the service
// published, so the test checks the consumers' idempotency rather than assuming it.
//
// Requirements: the platform running (see README), capitec-postgres and capitec-kafka up.
// Run: node scripts/e2e/verify-fraud-flow.mjs

import { spawnSync } from 'node:child_process'

const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8000'
const DB_PASSWORD = process.env.DB_PASSWORD || 'local_dev_password'
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
    'exec', '-e', `PGPASSWORD=${DB_PASSWORD}`, 'capitec-postgres',
    'psql', '-U', 'app_user', '-d', db, '-Atc', query,
  ], { encoding: 'utf8' })
  if (r.status !== 0) throw new Error(`psql failed: ${r.stderr}`)
  return r.stdout.trim()
}

function topicLines(topic) {
  const r = spawnSync('docker', [
    'exec', 'capitec-kafka', `${KAFKA_BIN}/kafka-console-consumer.sh`,
    '--bootstrap-server', 'localhost:9092', '--topic', topic,
    '--from-beginning', '--timeout-ms', '6000', '--property', 'print.key=true',
  ], { encoding: 'utf8' })
  return r.stdout.split('\n').filter(Boolean)
}

function produce(topic, key, value) {
  const r = spawnSync('docker', [
    'exec', '-i', 'capitec-kafka', `${KAFKA_BIN}/kafka-console-producer.sh`,
    '--bootstrap-server', 'localhost:9092', '--topic', topic,
    '--property', 'parse.key=true', '--property', 'key.separator=\t',
  ], { input: `${key}\t${value}\n`, encoding: 'utf8' })
  return r.status === 0
}

// The JSON payload the service published for a given key, or null.
function publishedPayload(topic, key) {
  const line = topicLines(topic).find((l) => l.startsWith(key + '\t'))
  return line ? JSON.parse(line.slice(key.length + 1)) : null
}

async function waitFor(check, timeoutMs = 20000) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const v = await check()
    if (v) return v
    await new Promise((r) => setTimeout(r, 500))
  }
  return null
}

async function main() {
  console.log('\nSetup: real customer and account')
  const customer = await api('POST', '/api/v1/customers', {
    firstName: 'Fraud', lastName: `Flow ${RUN}`, email: `fraud.${RUN}@example.com`,
    phoneNumber: null, clientType: 'INDIVIDUAL',
  })
  record('customer created', customer.status === 201, `HTTP ${customer.status}`)
  const customerId = customer.data.id
  const account = await api('POST', '/api/v1/accounts', { customerId, accountType: 'SAVINGS' })
  record('account created', account.status === 201, `HTTP ${account.status}`)
  const accountId = account.data.id

  console.log('\nTest 1: ordinary transaction is evaluated and not flagged')
  const normal = await api('POST', '/api/v1/transactions', {
    accountId, type: 'CREDIT', amount: 250, currency: 'ZAR', description: `normal ${RUN}`,
  })
  record('ordinary transaction recorded (201)', normal.status === 201, `HTTP ${normal.status}`)
  const normalEvaluated = await waitFor(() =>
    sql('fraud_detection_db', `select outcome from processed_transactions where transaction_id='${normal.data.id}'`))
  record('fraud service evaluated it', normalEvaluated === 'NOT_DETECTED', `outcome=${normalEvaluated}`)
  record('no fraud record for ordinary transaction',
    sql('fraud_detection_db', `select count(*) from fraud_detections where transaction_id='${normal.data.id}'`) === '0')

  console.log('\nTest 2: high-value transaction produces a fraud record and a case')
  const large = await api('POST', '/api/v1/transactions', {
    accountId, type: 'DEBIT', amount: 60000, currency: 'ZAR', description: `large ${RUN}`,
  })
  record('high-value transaction recorded (201)', large.status === 201, `HTTP ${large.status}`)
  const txId = large.data.id
  const fraudRow = await waitFor(() => {
    const r = sql('fraud_detection_db',
      `select id||'|'||fraud_reference||'|'||risk_level||'|'||detection_rule||'|'||coalesce(case_id::text,'') from fraud_detections where transaction_id='${txId}'`)
    return r || null
  })
  record('fraud record persisted for the transaction', Boolean(fraudRow), fraudRow || '')
  const [fraudId, fraudRef, riskLevel, rule, linkedCase] = (fraudRow || '').split('|')
  record('fraud record is HIGH risk from the HIGH_AMOUNT rule', riskLevel === 'HIGH' && rule === 'HIGH_AMOUNT')

  const fraudEvent = fraudId ? publishedPayload('fraud.events', fraudId) : null
  record('FraudDetected published to fraud.events, keyed by fraud ID', Boolean(fraudEvent))
  if (fraudEvent) {
    record('FraudDetected carries the real transaction and customer',
      fraudEvent.transactionId === txId && fraudEvent.accountId === accountId &&
      fraudEvent.customerId === customerId && fraudEvent.requiresInvestigation === true)
  }

  const caseRow = fraudId ? await waitFor(() => {
    const r = sql('case_service_db', `select id||'|'||case_number||'|'||status||'|'||priority from cases where source_fraud_id='${fraudId}'`)
    return r || null
  }) : null
  record('case opened for the fraud alert', Boolean(caseRow), caseRow || '')
  const [caseId, caseNumber, caseStatus, casePriority] = (caseRow || '').split('|')
  record('case is linked to the fraud and has HIGH priority', casePriority === 'HIGH' && caseStatus === 'NEW')

  const linked = caseId ? await waitFor(() =>
    sql('fraud_detection_db', `select case_id from fraud_detections where id='${fraudId}'`) === caseId ? 'yes' : null) : null
  record('fraud record holds the case ID (Fraud <-> Case link persisted)', Boolean(linked))

  const caseEvent = caseId ? publishedPayload('case.events', caseId) : null
  record('CaseCreated published to case.events (creation only)', Boolean(caseEvent) && caseEvent.caseNumber === caseNumber)

  console.log('\nTest 3: duplicate deliveries do not create duplicates')
  const txMsg = topicLines('transaction-events').find((l) => l.startsWith(txId + '\t'))
  if (txMsg) {
    produce('transaction-events', txId, txMsg.slice(txId.length + 1))
    await new Promise((r) => setTimeout(r, 4000))
  }
  record('duplicate TransactionCreated did not create a second fraud record',
    sql('fraud_detection_db', `select count(*) from fraud_detections where transaction_id='${txId}'`) === '1')
  record('duplicate TransactionCreated did not re-evaluate the transaction',
    sql('fraud_detection_db', `select count(*) from processed_transactions where transaction_id='${txId}'`) === '1')

  const fraudMsg = topicLines('fraud.events').find((l) => l.startsWith(fraudId + '\t'))
  if (fraudMsg) {
    produce('fraud.events', fraudId, fraudMsg.slice(fraudId.length + 1))
    await new Promise((r) => setTimeout(r, 4000))
  }
  record('duplicate FraudDetected did not open a second case',
    sql('case_service_db', `select count(*) from cases where source_fraud_id='${fraudId}'`) === '1')

  console.log('\nTest 4: query endpoints return persisted records')
  const byId = await api('GET', `/api/v1/fraud/${fraudId}`)
  record('GET /fraud/{id} returns the record', byId.status === 200 && byId.data.fraudReference === fraudRef)
  const byTx = await api('GET', `/api/v1/fraud/transaction/${txId}`)
  record('GET /fraud/transaction/{id} returns the record', byTx.status === 200 && byTx.data.id === fraudId)
  const byCase = await api('GET', `/api/v1/fraud/case/${caseId}`)
  record('GET /fraud/case/{caseId} returns the same record (Case Fraud tab)',
    byCase.status === 200 && byCase.data.some((f) => f.id === fraudId))
  const search = await api('GET', `/api/v1/fraud?riskLevel=HIGH&customerId=${customerId}&sortBy=riskScore&direction=desc&size=5`)
  record('GET /fraud with filters returns the record (standalone Fraud page)',
    search.status === 200 && search.data.content.some((f) => f.id === fraudId) && search.data.page === 0)
  const bySearch = await api('GET', `/api/v1/fraud?q=${fraudRef}`)
  record('GET /fraud?q=<reference> finds it', bySearch.status === 200 && bySearch.data.content.length === 1)
  const rules = await api('GET', '/api/v1/fraud/rules')
  record('GET /fraud/rules lists configured rules', rules.status === 200 && rules.data.length >= 2)

  console.log('\nTest 5: case lifecycle')
  const openCase = await api('GET', `/api/v1/cases/${caseId}`)
  record('GET /cases/{id} returns the case', openCase.status === 200 && openCase.data.caseNumber === caseNumber)
  const invalidClose = await api('PATCH', `/api/v1/cases/${caseId}`, { status: 'CLOSED' })
  record('closing a NEW case is rejected (409)', invalidClose.status === 409, `HTTP ${invalidClose.status}`)
  const inProgress = await api('PATCH', `/api/v1/cases/${caseId}`, { status: 'IN_PROGRESS', assignedInvestigator: 'Investigator A' })
  record('moving to IN_PROGRESS and assigning succeeds', inProgress.status === 200 && inProgress.data.status === 'IN_PROGRESS')
  const noResolution = await api('PATCH', `/api/v1/cases/${caseId}`, { status: 'RESOLVED' })
  record('resolving without resolution details is rejected (409)', noResolution.status === 409, `HTTP ${noResolution.status}`)
  const resolved = await api('PATCH', `/api/v1/cases/${caseId}`, { status: 'RESOLVED', resolutionDetails: 'Confirmed with customer.' })
  record('resolving with details succeeds', resolved.status === 200 && resolved.data.status === 'RESOLVED')
  const activity = await api('GET', `/api/v1/cases/${caseId}/activity`)
  record('case history records the changes', activity.status === 200 && activity.data.length >= 4,
    `${activity.data?.length ?? 0} entries`)
  const updates = await waitFor(() =>
    topicLines('case.updates').some((l) => l.startsWith(caseId + '\t')) ? true : null)
  record('case status changes published to case.updates (not case.events)', Boolean(updates))

  console.log('\nTest 6: investigation status on the fraud record')
  const reviewed = await api('PATCH', `/api/v1/fraud/${fraudId}/status`, { investigationStatus: 'UNDER_REVIEW' })
  record('UNDER_REVIEW accepted', reviewed.status === 200 && reviewed.data.investigationStatus === 'UNDER_REVIEW')
  const badMove = await api('PATCH', `/api/v1/fraud/${fraudId}/status`, { investigationStatus: 'OPEN' })
  record('moving back to OPEN is rejected (409)', badMove.status === 409, `HTTP ${badMove.status}`)

  console.log('\nTest 7: invalid requests and missing records')
  const noCustomer = await api('POST', '/api/v1/cases', { subject: 'x', priority: 'HIGH', caseType: 'GENERAL_INQUIRY' })
  record('manual case without a customer is rejected (400)', noCustomer.status === 400, `HTTP ${noCustomer.status}`)
  const missingCase = await api('GET', '/api/v1/cases/00000000-0000-4000-8000-000000000009')
  record('unknown case returns 404', missingCase.status === 404)
  const missingFraud = await api('GET', '/api/v1/fraud/00000000-0000-4000-8000-000000000009')
  record('unknown fraud returns 404', missingFraud.status === 404)
  const badFilter = await api('GET', '/api/v1/fraud?riskLevel=EXTREME')
  record('invalid risk level filter returns 400', badFilter.status === 400, `HTTP ${badFilter.status}`)

  console.log('\nTest 8: notifications come from real events, once each')
  const notifCount = (type, id) => sql('notification_db',
    `select count(*) from notifications where notification_type='${type}' and ${id}`)
  record('FRAUD_HIGH_RISK notification persisted for the fraud record',
    notifCount('FRAUD_HIGH_RISK', `fraud_id='${fraudId}'`) === '1')
  record('CASE_CREATED notification persisted for the case',
    notifCount('CASE_CREATED', `case_id='${caseId}'`) === '1')
  record('case status change produced a notification',
    Number(notifCount('CASE_STATUS_CHANGED', `case_id='${caseId}'`)) >= 1)
  record('case assignment produced a notification',
    Number(notifCount('CASE_ASSIGNED', `case_id='${caseId}'`)) >= 1)
  record('notifications are served by the API',
    (await api('GET', '/api/v1/notifications?size=50')).data.some((n) => n.caseId === caseId))
  record('no duplicate notification after the duplicate fraud delivery',
    notifCount('FRAUD_HIGH_RISK', `fraud_id='${fraudId}'`) === '1')

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
