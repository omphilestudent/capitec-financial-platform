import { useEffect, useState } from 'react'
import { AlertCircle, CheckCircle2, ChevronRight, Loader2, RefreshCw, Search, ShieldAlert } from 'lucide-react'
import { getFraud, searchFraud, updateFraudStatus } from '../services/fraudService'
import { getTransactionForFraud } from '../services/transactionService'

const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const INVESTIGATION_STATUSES = ['OPEN', 'UNDER_REVIEW', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE']
const ALLOWED_NEXT = {
  OPEN: ['UNDER_REVIEW', 'FALSE_POSITIVE'],
  UNDER_REVIEW: ['CONFIRMED_FRAUD', 'FALSE_POSITIVE'],
  CONFIRMED_FRAUD: [],
  FALSE_POSITIVE: [],
}
const PRIORITY_CLASS = { LOW: 'low', MEDIUM: 'medium', HIGH: 'high', CRITICAL: 'critical' }

const label = (value) => (value || '').replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())

const errorMessage = (err) => {
  if (!err.response) return 'The API gateway cannot be reached.'
  const data = err.response.data
  if (data?.fieldErrors) return Object.values(data.fieldErrors).join(' ')
  return data?.message || 'The request failed.'
}

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

const formatMoney = (value, currency = 'ZAR') =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency }).format(Number(value ?? 0))

function Level({ value }) {
  return (
    <span className={`sf-priority ${PRIORITY_CLASS[value] || ''}`}>
      <i aria-hidden="true" />
      {label(value)}
    </span>
  )
}

function Panel({ state, onRetry, children }) {
  if (state.status === 'loading') {
    return <div className="sf-skeleton-stack" role="status" aria-label="Loading">
        <span className="sf-skeleton sf-skeleton-row" style={{ width: '100%' }} />
        <span className="sf-skeleton sf-skeleton-row" style={{ width: '92%' }} />
        <span className="sf-skeleton sf-skeleton-row" style={{ width: '96%' }} />
        <span className="sf-skeleton sf-skeleton-row" style={{ width: '70%' }} />
      </div>;
  }
  if (state.status === 'error') {
    return (
      <div className="cu-banner danger" role="alert">
        <AlertCircle size={18} /> <span>{state.error}</span>
        <button className="home-btn" onClick={onRetry}>Retry</button>
      </div>
    )
  }
  return children
}

export function FraudList({ onOpen, onOpenCase }) {
  const [riskLevel, setRiskLevel] = useState('')
  const [status, setStatus] = useState('')
  const [query, setQuery] = useState('')
  const [term, setTerm] = useState('')
  const [sortBy, setSortBy] = useState('detectedAt')
  const [page, setPage] = useState(0)
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ status: 'loading', data: null, error: '' })

  useEffect(() => {
    let active = true
    setState({ status: 'loading', data: null, error: '' })
    searchFraud({ riskLevel, status, q: term, sortBy, direction: 'desc', page, size: 20 })
      .then((data) => { if (active) setState({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setState({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [riskLevel, status, term, sortBy, page, version])

  const submitSearch = (event) => {
    event.preventDefault()
    setPage(0)
    setTerm(query.trim())
  }

  const data = state.data

  return (
    <section className="sf-list">
      <div className="sf-list-head">
        <div>
          <span className="sf-kicker">Fraud</span>
          <h1>Fraud Detections</h1>
          <span className="sf-muted">
            {data ? `${data.totalElements} record${data.totalElements === 1 ? '' : 's'}` : 'Loading…'}
          </span>
        </div>
        <div className="sf-list-tools">
          <form className="sf-search-form" onSubmit={submitSearch} role="search">
            <label className="sf-search">
              <Search size={15} aria-hidden="true" />
              <input
                type="search"
                placeholder="Search fraud reference, rule or ID"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search fraud records"
              />
            </label>
          </form>
          <select value={riskLevel} onChange={(e) => { setPage(0); setRiskLevel(e.target.value) }} aria-label="Filter by risk level">
            <option value="">All risk levels</option>
            {RISK_LEVELS.map((r) => <option key={r} value={r}>{label(r)}</option>)}
          </select>
          <select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value) }} aria-label="Filter by investigation status">
            <option value="">All statuses</option>
            {INVESTIGATION_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
          <select value={sortBy} onChange={(e) => { setPage(0); setSortBy(e.target.value) }} aria-label="Sort by">
            <option value="detectedAt">Detected</option>
            <option value="riskScore">Risk score</option>
            <option value="riskLevel">Risk level</option>
            <option value="fraudReference">Reference</option>
          </select>
          <button className="sf-pill-btn" onClick={() => setVersion((v) => v + 1)} aria-label="Refresh fraud records">
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      <Panel state={state} onRetry={() => setVersion((v) => v + 1)}>
        <div className="sf-card sf-table-wrap">
          <table className="sf-table">
            <thead>
              <tr>
                <th>Fraud Reference</th>
                <th>Detection Rule</th>
                <th>Risk Score</th>
                <th>Risk Level</th>
                <th>Investigation</th>
                <th>Case</th>
                <th>Detected</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && (
                <tr><td colSpan={7} className="sf-empty">No fraud records match these filters.</td></tr>
              )}
              {data?.content.map((f) => (
                <tr key={f.id}>
                  <td><button className="sf-link-button" onClick={() => onOpen(f.id)}>{f.fraudReference}</button></td>
                  <td>{f.detectionRule}</td>
                  <td>{f.riskScore}</td>
                  <td><Level value={f.riskLevel} /></td>
                  <td>{label(f.investigationStatus)}</td>
                  <td>
                    {f.caseId
                      ? <button className="sf-link-button" onClick={() => onOpenCase(f.caseId)}>Open case</button>
                      : <span className="sf-muted">Not linked</span>}
                  </td>
                  <td>{formatDate(f.detectedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.totalPages > 1 && (
          <div className="home-actions">
            <button className="sf-btn outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button>
            <span className="sf-muted">Page {page + 1} of {data.totalPages}</span>
            <button className="sf-btn outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Next</button>
          </div>
        )}
      </Panel>
    </section>
  )
}

export function FraudRecordView({ fraudId, onBack, onOpenCase }) {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ status: 'loading', data: null, error: '' })
  const [transaction, setTransaction] = useState({ status: 'idle', data: null, error: '' })
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)
  const [nextStatus, setNextStatus] = useState('')

  useEffect(() => {
    let active = true
    setState({ status: 'loading', data: null, error: '' })
    getFraud(fraudId)
      .then((data) => { if (active) setState({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setState({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [fraudId, version])

  useEffect(() => {
    const txId = state.data?.transactionId
    if (!txId) return undefined
    let active = true
    setTransaction({ status: 'loading', data: null, error: '' })
    getTransactionForFraud(txId, state.data?.customerId)
      .then((data) => { if (active) setTransaction({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setTransaction({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [state.data?.transactionId, state.data?.customerId])

  const submitStatus = async () => {
    if (!nextStatus) return
    setSaving(true)
    setNotice(null)
    try {
      await updateFraudStatus(fraudId, nextStatus)
      setNotice({ tone: 'success', text: 'Investigation status updated.' })
      setNextStatus('')
      setVersion((v) => v + 1)
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="sf-record">
      <Panel state={state} onRetry={() => setVersion((v) => v + 1)}>
        {state.data && (
          <>
            <header className="sf-record-head">
              <div className="sf-title-row">
                <div className="sf-title">
                  <span className="sf-icon fraud" aria-hidden="true"><ShieldAlert size={20} /></span>
                  <div>
                    <span className="sf-kicker">Fraud Detection</span>
                    <h1>{state.data.fraudReference}</h1>
                  </div>
                </div>
                <div className="sf-actions">
                  <button className="sf-pill-btn" onClick={onBack}>Back to fraud</button>
                  {state.data.caseId && (
                    <button className="sf-pill-btn" onClick={() => onOpenCase(state.data.caseId)}>Open case</button>
                  )}
                </div>
              </div>
              <dl className="sf-highlights">
                <div><dt>Risk Level</dt><dd><Level value={state.data.riskLevel} /></dd></div>
                <div><dt>Risk Score</dt><dd>{state.data.riskScore}</dd></div>
                <div><dt>Investigation</dt><dd>{label(state.data.investigationStatus)}</dd></div>
                <div><dt>Detected</dt><dd>{formatDate(state.data.detectedAt)}</dd></div>
              </dl>
            </header>

            <div className="sf-body single">
              <div className="sf-card sf-main">
                <section className="sf-section">
                  <div className="sf-section-head"><h3>Detection</h3></div>
                  <div className="sf-section-body">
                    <div className="sf-fields">
                      {[
                        ['Detection Rule', state.data.detectionRule],
                        ['Fraud Type', label(state.data.fraudType)],
                        ['Fraud Category', label(state.data.fraudCategory)],
                        ['Detection Source', label(state.data.detectionSource)],
                        ['Customer ID', state.data.customerId],
                        ['Account ID', state.data.accountId],
                        ['Transaction ID', state.data.transactionId],
                        ['Last Updated', formatDate(state.data.lastUpdated)],
                      ].map(([field, value]) => (
                        <div key={field} className="sf-field">
                          <div className="sf-field-label">{field}</div>
                          <div className="sf-field-value">{value || '—'}</div>
                        </div>
                      ))}
                    </div>
                    <div className="sf-long-block">
                      <span className="sf-field-label">Explanation</span>
                      <p>{state.data.detectionDescription}</p>
                    </div>
                  </div>
                </section>

                <section className="sf-section">
                  <div className="sf-section-head"><h3>Underlying Transaction</h3></div>
                  <div className="sf-section-body">
                    <Panel state={transaction} onRetry={() => setVersion((v) => v + 1)}>
                      {transaction.data ? (
                        <div className="sf-fields">
                          <div className="sf-field"><div className="sf-field-label">Reference</div><div className="sf-field-value">{transaction.data.transactionReference}</div></div>
                          <div className="sf-field"><div className="sf-field-label">Type</div><div className="sf-field-value">{transaction.data.type}</div></div>
                          <div className="sf-field"><div className="sf-field-label">Amount</div><div className="sf-field-value">{formatMoney(transaction.data.amount, transaction.data.currency)}</div></div>
                          <div className="sf-field"><div className="sf-field-label">Status</div><div className="sf-field-value">{transaction.data.status}</div></div>
                          <div className="sf-field"><div className="sf-field-label">Description</div><div className="sf-field-value">{transaction.data.description || '—'}</div></div>
                          <div className="sf-field"><div className="sf-field-label">Created</div><div className="sf-field-value">{formatDate(transaction.data.createdAt)}</div></div>
                        </div>
                      ) : <p className="sf-muted">No transaction is available for this record.</p>}
                    </Panel>
                  </div>
                </section>

                <section className="sf-section">
                  <div className="sf-section-head"><h3>Update Investigation</h3></div>
                  <div className="sf-section-body">
                    {notice && (
                      <div className={`cu-banner ${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'}>
                        {notice.tone === 'danger' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
                        <span>{notice.text}</span>
                      </div>
                    )}
                    {(ALLOWED_NEXT[state.data.investigationStatus] || []).length === 0 ? (
                      <p className="sf-muted">This investigation is closed.</p>
                    ) : (
                      <div className="home-actions">
                        <select value={nextStatus} onChange={(e) => setNextStatus(e.target.value)} disabled={saving} aria-label="New investigation status">
                          <option value="">Choose a status</option>
                          {ALLOWED_NEXT[state.data.investigationStatus].map((s) => <option key={s} value={s}>{label(s)}</option>)}
                        </select>
                        <button className="sf-btn primary" onClick={submitStatus} disabled={!nextStatus || saving}>
                          {saving ? <Loader2 size={15} className="cu-spin" /> : null}
                          {saving ? 'Saving…' : 'Update status'}
                        </button>
                      </div>
                    )}
                  </div>
                </section>
              </div>
            </div>
          </>
        )}
      </Panel>
      {state.status === 'error' && <button className="sf-pill-btn" onClick={onBack}>Back to fraud</button>}
    </section>
  )
}
