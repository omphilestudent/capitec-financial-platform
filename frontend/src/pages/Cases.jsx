import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertCircle, Briefcase, Building2, CheckCircle2, ChevronDown, ChevronRight, FileText,
  Loader2, RefreshCw, Search, ShieldAlert, UserRound, Wallet,
} from 'lucide-react'
import { getCase, getCaseActivity, getCases, updateCase } from '../services/caseService'
import { getFraudForCase } from '../services/fraudService'
import { getAccountById, getAccountsByCustomer } from '../services/accountService'
import { getCustomerById } from '../services/customerService'
import { getTransactionSummary, getTransactionsByAccount } from '../services/transactionService'
import StatementsPanel from './Statements'

export const CASE_STATUS_PATH = ['NEW', 'OPEN', 'IN_PROGRESS', 'PENDING', 'ESCALATED', 'RESOLVED', 'CLOSED']

const STATUS_LABEL = {
  NEW: 'New', OPEN: 'Open', IN_PROGRESS: 'In Progress', PENDING: 'Pending',
  ESCALATED: 'Escalated', RESOLVED: 'Resolved', CLOSED: 'Closed',
}

const PRIORITY_LABEL = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' }

const PRIORITY_CLASS = { LOW: 'low', MEDIUM: 'medium', HIGH: 'high', CRITICAL: 'critical' }

const ALLOWED_NEXT = {
  NEW: ['OPEN', 'IN_PROGRESS', 'PENDING', 'ESCALATED', 'RESOLVED'],
  OPEN: ['IN_PROGRESS', 'PENDING', 'ESCALATED', 'RESOLVED'],
  IN_PROGRESS: ['PENDING', 'ESCALATED', 'RESOLVED'],
  PENDING: ['IN_PROGRESS', 'ESCALATED', 'RESOLVED'],
  ESCALATED: ['IN_PROGRESS', 'RESOLVED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
}

const ACTIVITY_LABEL = {
  CREATED: 'Case created',
  STATUS_CHANGED: 'Status changed',
  PRIORITY_CHANGED: 'Priority changed',
  ASSIGNED: 'Assignment changed',
  OWNER_CHANGED: 'Owner changed',
  DESCRIPTION_UPDATED: 'Description updated',
}

const errorMessage = (err) => {
  if (!err.response) return 'The API gateway cannot be reached.'
  const data = err.response.data
  if (data?.fieldErrors) return Object.values(data.fieldErrors).join(' ')
  return data?.message || 'The request failed.'
}

const formatMoney = (value, currency = 'ZAR') =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency }).format(Number(value ?? 0))

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

const formatDay = (value) =>
  value ? new Date(value).toLocaleDateString('en-ZA', { dateStyle: 'medium' }) : '—'

const initials = (name) => (name || '?').split(' ').slice(0, 2).map((p) => p[0]).join('').toUpperCase()

function useRequest(run, deps) {
  const [state, setState] = useState({ status: 'loading', data: null, error: '' })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    setState({ status: 'loading', data: null, error: '' })
    run()
      .then((data) => { if (active) setState({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setState({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [...deps, version])

  return { ...state, reload: () => setVersion((v) => v + 1) }
}

function Priority({ value }) {
  return (
    <span className={`sf-priority ${PRIORITY_CLASS[value] || ''}`}>
      <i aria-hidden="true" />
      {PRIORITY_LABEL[value] || value}
    </span>
  )
}

function Section({ title, children }) {
  const [open, setOpen] = useState(true)
  return (
    <section className="sf-section">
      <button className="sf-section-head" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        <h3>{title}</h3>
      </button>
      {open && <div className="sf-section-body">{children}</div>}
    </section>
  )
}

function FieldGrid({ fields }) {
  return (
    <div className="sf-fields">
      {fields.map(([label, value]) => (
        <div key={label} className="sf-field">
          <div className="sf-field-label">{label}</div>
          <div className="sf-field-value">{value || <span className="sf-blank">—</span>}</div>
        </div>
      ))}
    </div>
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

export function CaseList({ onOpen }) {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [priority, setPriority] = useState('')
  const [page, setPage] = useState(0)
  const [term, setTerm] = useState('')

  const list = useRequest(
    () => getCases({ q: term, status, priority, page, size: 20 }),
    [term, status, priority, page],
  )

  const submitSearch = (event) => {
    event.preventDefault()
    setPage(0)
    setTerm(query.trim())
  }

  const data = list.data
  const count = data?.totalElements ?? 0

  return (
    <section className="sf-list">
      <div className="sf-list-head">
        <div>
          <span className="sf-kicker">Cases</span>
          <h1>All Cases</h1>
          <span className="sf-muted">
            {data ? `${count} item${count === 1 ? '' : 's'} • Newest first` : 'Loading…'}
          </span>
        </div>
        <div className="sf-list-tools">
          <form className="sf-search-form" onSubmit={submitSearch} role="search">
            <label className="sf-search">
              <Search size={15} aria-hidden="true" />
              <input
                type="search"
                placeholder="Search case number, subject or customer ID"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search cases"
              />
            </label>
          </form>
          <select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value) }} aria-label="Filter by status">
            <option value="">All statuses</option>
            {CASE_STATUS_PATH.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
          <select value={priority} onChange={(e) => { setPage(0); setPriority(e.target.value) }} aria-label="Filter by priority">
            <option value="">All priorities</option>
            {Object.keys(PRIORITY_LABEL).map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
          </select>
          <button className="sf-pill-btn" onClick={list.reload} aria-label="Refresh cases">
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      <Panel state={list} onRetry={list.reload}>
        <div className="sf-card sf-table-wrap">
          <table className="sf-table">
            <thead>
              <tr>
                <th>Case Number</th>
                <th>Subject</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Owner</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && (
                <tr><td colSpan={6} className="sf-empty">No cases match these filters.</td></tr>
              )}
              {data?.content.map((c) => (
                <tr key={c.id}>
                  <td><button className="sf-link-button" onClick={() => onOpen(c.id)}>{c.caseNumber}</button></td>
                  <td>{c.subject}</td>
                  <td><Priority value={c.priority} /></td>
                  <td>{STATUS_LABEL[c.status] || c.status}</td>
                  <td>{c.owner || '—'}</td>
                  <td>{formatDate(c.createdAt)}</td>
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

export function CaseRecord({ caseId, onBack, onOpenFraud, onOpenCustomer }) {
  const [tab, setTab] = useState('details')
  const caseRequest = useRequest(() => getCase(caseId), [caseId])
  const activity = useRequest(() => getCaseActivity(caseId), [caseId])
  const fraud = useRequest(() => getFraudForCase(caseId), [caseId])
  const caseData = caseRequest.data

  const accountRequest = useRequest(
    () => (caseData?.accountId ? getAccountById(caseData.accountId) : Promise.resolve(null)),
    [caseData?.accountId],
  )
  const customerRequest = useRequest(
    () => (caseData?.customerId ? getCustomerById(caseData.customerId) : Promise.resolve(null)),
    [caseData?.customerId],
  )

  const reloadAll = () => {
    caseRequest.reload()
    activity.reload()
    fraud.reload()
  }

  const [pathSaving, setPathSaving] = useState(false)
  const [pathError, setPathError] = useState('')

  const changeStatus = async (next) => {
    setPathSaving(true)
    setPathError('')
    try {
      await updateCase(caseData.id, { status: next })
      reloadAll()
    } catch (err) {
      setPathError(errorMessage(err))
    } finally {
      setPathSaving(false)
    }
  }

  if (caseRequest.status !== 'ready') {
    return (
      <section className="sf-record">
        <button className="sf-pill-btn" onClick={onBack}>Back to cases</button>
        <Panel state={caseRequest} onRetry={caseRequest.reload}>{null}</Panel>
      </section>
    )
  }

  const closed = caseData.status === 'CLOSED'

  return (
    <div className="sf-record">
      <header className="sf-record-head">
        <div className="sf-title-row">
          <div className="sf-title">
            <span className="sf-icon case" aria-hidden="true"><Briefcase size={20} /></span>
            <div>
              <span className="sf-kicker">Case · {caseData.caseNumber}</span>
              <h1>{caseData.subject}</h1>
            </div>
          </div>
          <div className="sf-actions">
            <button className="sf-pill-btn" onClick={onBack}>Back to cases</button>
            <button className="sf-pill-btn" onClick={reloadAll}><RefreshCw size={14} /> Refresh</button>
          </div>
        </div>

        <dl className="sf-highlights">
          <div><dt>Priority</dt><dd><Priority value={caseData.priority} /></dd></div>
          <div><dt>Status</dt><dd>{STATUS_LABEL[caseData.status] || caseData.status}</dd></div>
          <div><dt>Case Number</dt><dd>{caseData.caseNumber}</dd></div>
          <div><dt>Owner</dt><dd>{caseData.owner || '—'}</dd></div>
          <div><dt>Investigator</dt><dd>{caseData.assignedInvestigator || 'Unassigned'}</dd></div>
        </dl>

        <div className="sf-path-row">
          <ol className="sf-path" aria-label="Case status">
            {CASE_STATUS_PATH.map((step) => {
              const current = step === caseData.status
              const reachable = (ALLOWED_NEXT[caseData.status] || []).includes(step)
              return (
                <li
                  key={step}
                  className={current ? (closed ? 'current closed' : 'current') : ''}
                  aria-current={current ? 'step' : undefined}
                >
                  {reachable ? (
                    <button
                      type="button"
                      className="sf-path-step-button"
                      disabled={pathSaving}
                      onClick={() => changeStatus(step)}
                      title={`Move this case to ${STATUS_LABEL[step]}`}
                    >
                      {STATUS_LABEL[step]}
                    </button>
                  ) : (
                    STATUS_LABEL[step]
                  )}
                </li>
              )
            })}
          </ol>
          {pathError && <p className="cu-banner danger" role="alert">{pathError}</p>}
        </div>
      </header>

      <div className="sf-body">
        <div className="sf-card sf-main">
          <div className="sf-tabs" role="tablist">
            {[
              ['details', 'Details'],
              ['transactions', 'Transaction History'],
              ['fraud', `Fraud Monitoring (${fraud.data?.length ?? 0})`],
              ['statements', 'Statements'],
            ].map(([key, label]) => (
              <button
                key={key}
                role="tab"
                aria-selected={tab === key}
                className={tab === key ? 'active' : ''}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="sf-tabpanel" role="tabpanel">
            {tab === 'details' && (
              <CaseDetails caseData={caseData} onSaved={reloadAll} closed={closed} />
            )}
            {tab === 'transactions' && (
              <Panel state={accountRequest} onRetry={accountRequest.reload}>
                {accountRequest.data
                  ? <TransactionHistory customerId={caseData.customerId} caseAccount={accountRequest.data} fraudRecords={fraud.data || []} />
                  : <p className="sf-muted centered">This case is not linked to an account.</p>}
              </Panel>
            )}
            {tab === 'fraud' && (
              <Panel state={fraud} onRetry={fraud.reload}>
                <FraudMonitoring records={fraud.data || []} onOpenFraud={onOpenFraud} />
              </Panel>
            )}
            {tab === 'statements' && (
              <StatementsPanel customerId={caseData.customerId} caseAccount={accountRequest.data} />
            )}
          </div>
        </div>

        <aside className="sf-side">
          <div className="sf-card sf-side-card">
            <h2><UserRound size={16} /> Customer</h2>
            <Panel state={customerRequest} onRetry={customerRequest.reload}>
              {customerRequest.data ? (
                <div className="sf-related-list">
                  <button className="sf-related-row" onClick={() => onOpenCustomer(customerRequest.data.id)}>
                    <span className="sf-avatar">{initials(`${customerRequest.data.firstName} ${customerRequest.data.lastName}`)}</span>
                    <span className="acc-row-body">
                      <span className="acc-row-title">{customerRequest.data.firstName} {customerRequest.data.lastName}</span>
                      <span className="acc-row-meta">{customerRequest.data.email}</span>
                    </span>
                    <ChevronRight size={15} aria-hidden="true" />
                  </button>
                </div>
              ) : <p className="sf-muted">No customer is linked to this case.</p>}
            </Panel>
          </div>

          <div className="sf-card sf-side-card">
            <h2><Wallet size={16} /> Account</h2>
            <Panel state={accountRequest} onRetry={accountRequest.reload}>
              {accountRequest.data ? (
                <dl className="sf-highlights stacked">
                  <div><dt>Account Number</dt><dd>{accountRequest.data.accountNumber}</dd></div>
                  <div><dt>Type</dt><dd>{accountRequest.data.accountType}</dd></div>
                  <div><dt>Status</dt><dd>{accountRequest.data.status}</dd></div>
                  <div><dt>Balance</dt><dd>{formatMoney(accountRequest.data.balance, accountRequest.data.currency)}</dd></div>
                </dl>
              ) : <p className="sf-muted">No account is linked to this case.</p>}
            </Panel>
          </div>

          <div className="sf-card sf-side-card">
            <h2><ShieldAlert size={16} /> Activity</h2>
            <Panel state={activity} onRetry={activity.reload}>
              {activity.data?.length === 0 && <p className="sf-muted">No activity recorded.</p>}
              <ul className="home-list">
                {[...(activity.data || [])].reverse().map((a) => (
                  <li key={a.id}>
                    <div className="home-list-main">
                      <strong>{ACTIVITY_LABEL[a.activityType] || a.activityType}</strong>
                      <span className="home-muted">{a.description}</span>
                      <span className="home-muted">{a.actor} · {formatDate(a.createdAt)}</span>
                    </div>
                  </li>
                ))}
              </ul>
            </Panel>
          </div>
        </aside>
      </div>
    </div>
  )
}

function CaseDetails({ caseData, onSaved, closed }) {
  const allowed = ALLOWED_NEXT[caseData.status] || []
  const [form, setForm] = useState({
    status: caseData.status,
    priority: caseData.priority,
    assignedInvestigator: caseData.assignedInvestigator || '',
    owner: caseData.owner || '',
    resolutionDetails: caseData.resolutionDetails || '',
  })
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    setForm({
      status: caseData.status,
      priority: caseData.priority,
      assignedInvestigator: caseData.assignedInvestigator || '',
      owner: caseData.owner || '',
      resolutionDetails: caseData.resolutionDetails || '',
    })
  }, [caseData])

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    setNotice(null)
    const changes = {}
    if (form.priority !== caseData.priority) changes.priority = form.priority
    if (form.assignedInvestigator.trim() !== (caseData.assignedInvestigator || '')) {
      changes.assignedInvestigator = form.assignedInvestigator.trim()
    }
    if (form.owner.trim() !== (caseData.owner || '')) changes.owner = form.owner.trim()
    if (form.resolutionDetails.trim() && form.resolutionDetails.trim() !== (caseData.resolutionDetails || '')) {
      changes.resolutionDetails = form.resolutionDetails.trim()
    }
    if (Object.keys(changes).length === 0) {
      setNotice({ tone: 'danger', text: 'Nothing has changed.' })
      setSaving(false)
      return
    }
    try {
      await updateCase(caseData.id, changes)
      setNotice({ tone: 'success', text: 'Case updated.' })
      onSaved()
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <Section title="Case Information">
        <FieldGrid fields={[
          ['Case Number', caseData.caseNumber],
          ['Subject', caseData.subject],
          ['Status', STATUS_LABEL[caseData.status] || caseData.status],
          ['Priority', PRIORITY_LABEL[caseData.priority] || caseData.priority],
          ['Case Type', caseData.caseType.replace(/_/g, ' ')],
          ['Origin', caseData.origin.replace(/_/g, ' ')],
          ['Created', formatDate(caseData.createdAt)],
          ['Last Modified', formatDate(caseData.updatedAt)],
          ['Closed', formatDate(caseData.closedAt)],
          ['Source Fraud ID', caseData.sourceFraudId],
        ]} />
        <div className="sf-long-block">
          <span className="sf-field-label">Description</span>
          <p>{caseData.description || 'No description.'}</p>
        </div>
      </Section>

      <Section title="Ownership and Investigation">
        <FieldGrid fields={[
          ['Owner', caseData.owner],
          ['Assigned Investigator', caseData.assignedInvestigator],
          ['Resolution Details', caseData.resolutionDetails],
        ]} />
      </Section>

      <Section title="Update Case">
        {closed ? (
          <p className="sf-muted">This case is closed and can no longer be changed.</p>
        ) : (
          <form className="acc-form" onSubmit={submit} aria-busy={saving}>
            {notice && (
              <div className={`cu-banner ${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'}>
                {notice.tone === 'danger' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
                <span>{notice.text}</span>
              </div>
            )}
            <div className="acc-form-grid">
              <div className="acc-field">
                <label htmlFor="case-priority">Priority</label>
                <select
                  id="case-priority"
                  value={form.priority}
                  disabled={saving}
                  onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
                >
                  {Object.keys(PRIORITY_LABEL).map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
                </select>
              </div>
              <div className="acc-field">
                <label htmlFor="case-investigator">Assigned investigator</label>
                <input
                  id="case-investigator"
                  maxLength={100}
                  value={form.assignedInvestigator}
                  disabled={saving}
                  onChange={(e) => setForm((f) => ({ ...f, assignedInvestigator: e.target.value }))}
                />
              </div>
              <div className="acc-field">
                <label htmlFor="case-owner">Owner</label>
                <input
                  id="case-owner"
                  maxLength={100}
                  value={form.owner}
                  disabled={saving}
                  onChange={(e) => setForm((f) => ({ ...f, owner: e.target.value }))}
                />
              </div>
            </div>
            <div className="acc-field">
              <label htmlFor="case-resolution">Resolution details (required to resolve or close)</label>
              <textarea
                id="case-resolution"
                rows={3}
                maxLength={5000}
                value={form.resolutionDetails}
                disabled={saving}
                onChange={(e) => setForm((f) => ({ ...f, resolutionDetails: e.target.value }))}
              />
            </div>
            <div className="acc-form-actions">
              <button type="submit" className="sf-btn primary" disabled={saving}>
                {saving ? <Loader2 size={15} className="cu-spin" /> : null}
                {saving ? 'Saving…' : 'Save changes'}
              </button>
            </div>
          </form>
        )}
      </Section>
    </>
  )
}

const ACCOUNT_TYPE_LABEL = { SAVINGS: 'Savings', CHEQUE: 'Cheque', CREDIT: 'Credit' }

const DATE_PRESETS = [
  { key: '7', label: 'Last 7 Days', days: 7 },
  { key: '30', label: 'Last 30 Days', days: 30 },
  { key: '90', label: 'Last 90 Days', days: 90 },
  { key: 'all', label: 'All Dates' },
]

const isoDay = (date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
const addDays = (iso, n) => {
  const [y, m, d] = iso.split('-').map(Number)
  return isoDay(new Date(y, m - 1, d + n))
}
const inclusiveSpan = (from, to) => Math.round((Date.parse(to) - Date.parse(from)) / 86400000) + 1

function TransactionHistory({ customerId, caseAccount, fraudRecords }) {
  const today = useMemo(() => isoDay(new Date()), [])

  const accounts = useRequest(
    () => (customerId ? getAccountsByCustomer(customerId) : Promise.resolve([])),
    [customerId],
  )
  const accountList = useMemo(() => {
    const list = [...(accounts.data || [])]
    if (caseAccount && !list.some((a) => a.id === caseAccount.id)) list.unshift(caseAccount)
    return list
  }, [accounts.data, caseAccount])
  const accountTypes = useMemo(
    () => [...new Set(accountList.map((a) => a.accountType))],
    [accountList],
  )

  const [accountType, setAccountType] = useState('ALL')
  const visibleAccounts = accountList.filter((a) => accountType === 'ALL' || a.accountType === accountType)
  const [selectedAccountId, setSelectedAccountId] = useState(caseAccount?.id ?? null)
  const selectedAccount = visibleAccounts.find((a) => a.id === selectedAccountId) ?? visibleAccounts[0] ?? null

  const [preset, setPreset] = useState('30')
  const [from, setFrom] = useState(addDays(today, -29))
  const [to, setTo] = useState(today)
  const [moneyType, setMoneyType] = useState('ALL')
  const [openRow, setOpenRow] = useState(null)

  const [applied, setApplied] = useState(null)
  const [result, setResult] = useState({ status: 'idle', rows: [], summary: null, error: '' })
  const requestSeq = useRef(0)

  const fraudByTransaction = useMemo(
    () => new Map(fraudRecords.map((f) => [f.transactionId, f])),
    [fraudRecords],
  )

  const rangeInvalid = Boolean(from && to && from > to)

  const runQuery = (criteria) => {
    if (!criteria.accountId) return
    const seq = ++requestSeq.current
    setApplied(criteria)
    setOpenRow(null)
    setResult({ status: 'loading', rows: [], summary: null, error: '' })
    const filter = { from: criteria.from, to: criteria.to, type: criteria.type }
    Promise.all([
      getTransactionsByAccount(criteria.accountId, filter),
      getTransactionSummary(criteria.accountId, filter),
    ])
      .then(([rows, summary]) => {
        if (seq === requestSeq.current) setResult({ status: 'ready', rows, summary, error: '' })
      })
      .catch((err) => {
        if (seq === requestSeq.current) setResult({ status: 'error', rows: [], summary: null, error: errorMessage(err) })
      })
  }

  const currentCriteria = (overrides = {}) => ({
    accountId: selectedAccount?.id,
    accountNumber: selectedAccount?.accountNumber,
    from,
    to,
    type: moneyType,
    ...overrides,
  })

  const applyPreset = (option) => {
    setPreset(option.key)
    if (option.key === 'all') {
      setFrom('')
      setTo('')
    } else {
      setFrom(addDays(today, -(option.days - 1)))
      setTo(today)
    }
  }

  const editDate = (setter) => (e) => {
    setPreset('custom')
    setter(e.target.value)
  }

  const shiftPeriod = (direction) => {
    if (!from || !to) return
    const span = inclusiveSpan(from, to)
    const nextFrom = addDays(from, direction * span)
    const nextTo = addDays(to, direction * span)
    setPreset('custom')
    setFrom(nextFrom)
    setTo(nextTo)
    runQuery(currentCriteria({ from: nextFrom, to: nextTo }))
  }

  const canShiftForward = Boolean(to) && to < today

  return (
    <div className="txn">
      <div className="txn-subtab">Transaction History · Account {selectedAccount?.accountNumber || '—'}</div>

      <Panel state={accounts} onRetry={accounts.reload}>
        <div className="txn-filters">
          <div className="txn-field">
            <span>Select</span>
            <div className="sf-segmented">
              {DATE_PRESETS.map((option) => (
                <button
                  key={option.key}
                  className={preset === option.key ? 'active' : ''}
                  onClick={() => applyPreset(option)}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
          <label className="txn-field">
            <span>From Date</span>
            <input type="date" value={from} max={to || undefined} onChange={editDate(setFrom)} />
          </label>
          <label className="txn-field">
            <span>To Date</span>
            <input type="date" value={to} min={from || undefined} max={today} onChange={editDate(setTo)} />
          </label>
          <label className="txn-field">
            <span>Account Type</span>
            <select
              value={accountType}
              onChange={(e) => setAccountType(e.target.value)}
              disabled={accountList.length === 0}
            >
              <option value="ALL">All account types</option>
              {accountTypes.map((t) => (
                <option key={t} value={t}>{ACCOUNT_TYPE_LABEL[t] || t}</option>
              ))}
            </select>
          </label>
          <label className="txn-field">
            <span>Account Number</span>
            <select
              value={selectedAccount?.id ?? ''}
              onChange={(e) => setSelectedAccountId(e.target.value)}
              disabled={visibleAccounts.length === 0}
            >
              {visibleAccounts.length === 0 && <option value="">No accounts</option>}
              {visibleAccounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountNumber} · {ACCOUNT_TYPE_LABEL[a.accountType] || a.accountType}
                </option>
              ))}
            </select>
          </label>
          <label className="txn-field">
            <span>Money Type</span>
            <select value={moneyType} onChange={(e) => setMoneyType(e.target.value)}>
              <option value="ALL">ALL</option>
              <option value="CREDIT">CREDIT</option>
              <option value="DEBIT">DEBIT</option>
            </select>
          </label>
        </div>

        {rangeInvalid && (
          <p className="cu-banner danger txn-range-error" role="alert">
            <AlertCircle size={18} /> <span>The From Date must be on or before the To Date.</span>
          </p>
        )}

        <div className="txn-account-card">
          <div className="txn-account-title">
            <span className="sf-icon account" aria-hidden="true"><Building2 size={18} /></span>
            <div>
              <strong>Account</strong>
              <span>
                {selectedAccount
                  ? `${selectedAccount.accountNumber} · ${ACCOUNT_TYPE_LABEL[selectedAccount.accountType] || selectedAccount.accountType}`
                  : 'No account selected'}
              </span>
            </div>
          </div>
          <div className="txn-account-actions">
            <button
              className="sf-btn primary"
              disabled={!selectedAccount || rangeInvalid}
              onClick={() => runQuery(currentCriteria())}
            >
              Get Transactions
            </button>
            <button className="sf-btn outline" disabled title="Dispute workflow not connected yet">Dispute Transaction</button>
          </div>
        </div>

        {applied && (
          <div className="txn-period">
            <button
              className="sf-btn outline"
              disabled={!from || !to || result.status === 'loading'}
              onClick={() => shiftPeriod(-1)}
            >
              ‹ Earlier period
            </button>
            <span className="sf-muted">
              {from && to ? `${from} to ${to}` : 'All dates'}
            </span>
            <button
              className="sf-btn outline"
              disabled={!canShiftForward || result.status === 'loading'}
              onClick={() => shiftPeriod(1)}
            >
              Later period ›
            </button>
          </div>
        )}

        {!applied ? (
          <p className="sf-muted centered">No transactions to display. Click <strong>Get Transactions</strong> to load results.</p>
        ) : result.status === 'loading' ? (
          <div className="sf-skeleton-stack" role="status" aria-label="Loading">
                <span className="sf-skeleton sf-skeleton-row" style={{ width: '100%' }} />
                <span className="sf-skeleton sf-skeleton-row" style={{ width: '92%' }} />
                <span className="sf-skeleton sf-skeleton-row" style={{ width: '96%' }} />
                <span className="sf-skeleton sf-skeleton-row" style={{ width: '70%' }} />
              </div>
        ) : result.status === 'error' ? (
          <div className="cu-banner danger" role="alert">
            <AlertCircle size={18} /> <span>{result.error}</span>
            <button className="home-btn" onClick={() => runQuery(applied)}>Retry</button>
          </div>
        ) : (
          <TransactionResults
            criteria={applied}
            rows={result.rows}
            summary={result.summary}
            fraudByTransaction={fraudByTransaction}
            openRow={openRow}
            onToggle={(id) => setOpenRow((current) => (current === id ? null : id))}
          />
        )}
      </Panel>
    </div>
  )
}

function TransactionResults({ criteria, rows, summary, fraudByTransaction, openRow, onToggle }) {
  return (
    <>
      <div className="txn-totals" aria-label="Totals for the selected filters">
        <div><span>Transactions</span><strong>{summary?.transactionCount ?? rows.length}</strong></div>
        <div><span>Credits</span><strong className="credit">{formatMoney(summary?.creditTotal)}</strong></div>
        <div><span>Debits</span><strong className="debit">{formatMoney(summary?.debitTotal)}</strong></div>
        <div><span>Net</span><strong>{formatMoney(summary?.netTotal)}</strong></div>
      </div>

      {rows.length === 0 ? (
        <p className="sf-muted centered">No transactions match these filters.</p>
      ) : (
        <div className="sf-table-wrap inset">
          <table className="sf-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Type</th>
                <th className="num">Amount</th>
                <th>Status</th>
                <th>Fraud</th>
                <th aria-label="Details" />
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => {
                const fraud = fraudByTransaction.get(t.id)
                const open = openRow === t.id
                return (
                  <Fragment key={t.id}>
                    <tr>
                      <td>{formatDate(t.createdAt)}</td>
                      <td>
                        <strong>{t.description || '—'}</strong>
                        <small>{t.transactionReference}</small>
                      </td>
                      <td className={t.type === 'CREDIT' ? 'credit' : 'debit'}>{t.type}</td>
                      <td className={`num ${t.type === 'CREDIT' ? 'credit' : 'debit'}`}>
                        {t.type === 'CREDIT' ? '+' : '−'}{formatMoney(t.amount, t.currency)}
                      </td>
                      <td>{t.status}</td>
                      <td>{fraud ? <span className="sf-priority high"><i aria-hidden="true" />{fraud.fraudReference}</span> : '—'}</td>
                      <td>
                        <button
                          className="sf-link-button"
                          aria-expanded={open}
                          onClick={() => onToggle(t.id)}
                        >
                          {open ? 'Hide' : 'Details'}
                        </button>
                      </td>
                    </tr>
                    {open && (
                      <tr className="txn-detail">
                        <td colSpan={7}>
                          <dl>
                            <div><dt>Reference</dt><dd>{t.transactionReference}</dd></div>
                            <div><dt>Transaction ID</dt><dd>{t.id}</dd></div>
                            <div><dt>Account</dt><dd>{criteria.accountNumber}</dd></div>
                            <div><dt>Recorded</dt><dd>{formatDate(t.createdAt)}</dd></div>
                            <div><dt>Status</dt><dd>{t.status}</dd></div>
                            <div><dt>Currency</dt><dd>{t.currency}</dd></div>
                            <div><dt>Description</dt><dd>{t.description || '—'}</dd></div>
                            <div><dt>Fraud record</dt><dd>{fraud ? fraud.fraudReference : 'None linked'}</dd></div>
                          </dl>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function FraudMonitoring({ records, onOpenFraud }) {
  if (records.length === 0) {
    return <p className="sf-muted centered">No fraud records are linked to this case.</p>
  }
  return (
    <>
      <div className="sf-tab-heading"><ShieldAlert size={16} /> Fraud records ({records.length})</div>
      <div className="sf-table-wrap inset">
        <table className="sf-table">
          <thead>
            <tr>
              <th>Fraud Reference</th>
              <th>Detection Rule</th>
              <th>Category</th>
              <th>Risk Score</th>
              <th>Risk Level</th>
              <th>Investigation</th>
              <th>Detected</th>
            </tr>
          </thead>
          <tbody>
            {records.map((f) => (
              <tr key={f.id}>
                <td><button className="sf-link-button" onClick={() => onOpenFraud(f.id)}>{f.fraudReference}</button></td>
                <td>{f.detectionRule}</td>
                <td>{f.fraudCategory.replace(/_/g, ' ')}</td>
                <td>{f.riskScore}</td>
                <td><Priority value={f.riskLevel} /></td>
                <td>{f.investigationStatus.replace(/_/g, ' ')}</td>
                <td>{formatDay(f.detectedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
