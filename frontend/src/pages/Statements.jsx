import { useEffect, useMemo, useState } from 'react'
import ProtectedPdfViewer from '../components/ProtectedPdfViewer'
import { AlertCircle, Clock, FileText, Loader2, RefreshCw } from 'lucide-react'
import { getAccountsByCustomer } from '../services/accountService'
import {
  createDownloadLink, fetchStatementPdf, generateStatement, getStatementEligibility,
  listAccountStatements,
} from '../services/statementService'

const ACCESS_WINDOW_MS = 5 * 60 * 1000

const ACCOUNT_TYPE_LABEL = { SAVINGS: 'Savings', CHEQUE: 'Cheque', CREDIT: 'Credit' }

const money = (value) =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(value ?? 0))

const dateText = (value) =>
  value ? new Date(`${value}T00:00:00`).toLocaleDateString('en-ZA', { dateStyle: 'medium' }) : '—'

const dateTimeText = (value) =>
  value ? new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

const isoDay = (date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const errorMessage = (err) => {
  if (!err.response) return 'The platform cannot be reached. Check that the services are running.'
  const status = err.response.status
  const data = err.response.data || {}
  if (status === 409 && data.eligibleCount !== undefined) {
    return `Not enough eligible transactions: this period has ${data.eligibleCount}. A statement needs at least 2.`
  }
  if (status === 403) return 'You are not authorised to access this account or statement.'
  if (status === 404) return 'The account or statement could not be found.'
  if (status === 410) return 'This document access has expired. Open the statement again to refresh it.'
  if (status === 503) return 'The statement service is temporarily unavailable. Try again shortly.'
  return data.message || 'The request failed. Please try again.'
}

const VERSION_LABEL = { CURRENT: 'Current version', SUPERSEDED: 'Superseded', FAILED: 'Failed attempt' }

export default function StatementsPanel({ customerId, caseAccount }) {
  const today = useMemo(() => isoDay(new Date()), [])
  const [accounts, setAccounts] = useState({ status: 'loading', data: [], error: '' })
  const [accountId, setAccountId] = useState(caseAccount?.id ?? '')
  const [from, setFrom] = useState(() => {
    const d = new Date()
    d.setDate(1)
    return isoDay(d)
  })
  const [to, setTo] = useState(today)
  const [eligibility, setEligibility] = useState({ status: 'idle', data: null, error: '' })
  const [generating, setGenerating] = useState(false)
  const [notice, setNotice] = useState(null)
  const [history, setHistory] = useState({ status: 'idle', items: [], error: '' })
  const [viewer, setViewer] = useState({ status: 'idle', url: null, statement: null, expiresAt: null, error: '' })
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    let active = true
    if (!customerId) {
      setAccounts({ status: 'ready', data: [], error: '' })
      return undefined
    }
    setAccounts({ status: 'loading', data: [], error: '' })
    getAccountsByCustomer(customerId)
      .then((data) => { if (active) setAccounts({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setAccounts({ status: 'error', data: [], error: errorMessage(err) }) })
    return () => { active = false }
  }, [customerId])

  const accountList = useMemo(() => {
    const list = [...(accounts.data || [])]
    if (caseAccount && !list.some((a) => a.id === caseAccount.id)) list.unshift(caseAccount)
    return list
  }, [accounts.data, caseAccount])

  const selectedAccount = accountList.find((a) => a.id === accountId) ?? accountList[0] ?? null

  const closeViewer = () => {
    setViewer({ status: 'idle', blob: null, statement: null, expiresAt: null, error: '' })
  }

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const periodError = !from || !to ? 'Select both a start and an end date.'
    : from > to ? 'The start date must be on or before the end date.'
    : to > today ? 'Statement periods cannot end in the future.'
    : null

  useEffect(() => {
    if (!selectedAccount || periodError) {
      setEligibility({ status: 'idle', data: null, error: '' })
      return undefined
    }
    let active = true
    setEligibility({ status: 'loading', data: null, error: '' })
    getStatementEligibility(customerId, selectedAccount.id, from, to)
      .then((data) => { if (active) setEligibility({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setEligibility({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [customerId, selectedAccount?.id, from, to, periodError])

  const loadHistory = (targetAccountId) => {
    if (!targetAccountId) return
    setHistory({ status: 'loading', items: [], error: '' })
    listAccountStatements(customerId, targetAccountId)
      .then((data) => setHistory({ status: 'ready', items: data.items, error: '' }))
      .catch((err) => setHistory({ status: 'error', items: [], error: errorMessage(err) }))
  }

  useEffect(() => {
    closeViewer()
    if (selectedAccount) loadHistory(selectedAccount.id)
  }, [selectedAccount?.id, customerId])

  const canGenerate = Boolean(selectedAccount && !periodError && !generating && eligibility.data?.canGenerate)

  const openStatement = async (statement) => {
    setViewer({ status: 'loading', url: null, statement, expiresAt: null, error: '' })
    try {
      const link = await createDownloadLink(customerId, statement.id)
      const blob = await fetchStatementPdf(customerId, link.token)
      setViewer({ status: 'ready', blob: new Blob([blob], { type: 'application/pdf' }), statement, expiresAt: Date.now() + ACCESS_WINDOW_MS, error: '' })
    } catch (err) {
      setViewer({ status: 'error', url: null, statement, expiresAt: null, error: errorMessage(err) })
    }
  }

  const onGenerate = async () => {
    setGenerating(true)
    setNotice(null)
    try {
      const statement = await generateStatement(customerId, {
        accountId: selectedAccount.id,
        periodType: 'CUSTOM',
        from,
        to,
      })
      setNotice({ tone: 'success', text: `Statement ${statement.statementReference} is ready.` })
      loadHistory(selectedAccount.id)
      await openStatement(statement)
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setGenerating(false)
    }
  }

  const isWithinAccessWindow = (statement) => {
    const generated = statement.generatedAt ? new Date(statement.generatedAt).getTime() : 0
    return generated > 0 && now - generated < ACCESS_WINDOW_MS
  }
  const available = history.items.filter(isWithinAccessWindow)
  const expiredStatements = history.items.filter((s) => !isWithinAccessWindow(s))

  const secondsLeft = viewer.expiresAt ? Math.max(0, Math.floor((viewer.expiresAt - now) / 1000)) : null
  const expired = viewer.status === 'ready' && secondsLeft === 0

  return (
    <div className="txn">
      <div className="txn-subtab">Statements</div>

      {accounts.status === 'error' && (
        <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{accounts.error}</span></div>
      )}
      {accounts.status === 'ready' && accountList.length === 0 && (
        <p className="sf-muted centered">This customer has no accounts, so no statements can be generated.</p>
      )}

      {accountList.length > 0 && (
        <>
          <div className="txn-filters">
            <label className="txn-field">
              <span>Account</span>
              <select value={selectedAccount?.id ?? ''} onChange={(e) => setAccountId(e.target.value)}>
                {accountList.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.accountNumber} · {ACCOUNT_TYPE_LABEL[a.accountType] || a.accountType}
                  </option>
                ))}
              </select>
            </label>
            <label className="txn-field">
              <span>Start date</span>
              <input type="date" value={from} max={to || today} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label className="txn-field">
              <span>End date</span>
              <input type="date" value={to} min={from || undefined} max={today} onChange={(e) => setTo(e.target.value)} />
            </label>
            <div className="txn-field">
              <span>&nbsp;</span>
              <button className="sf-btn primary" disabled={!canGenerate} onClick={onGenerate}>
                {generating ? <Loader2 size={15} className="cu-spin" /> : <FileText size={15} />} Generate Statement
              </button>
            </div>
          </div>

          <p className="sf-muted">
            Selected period: <strong>{dateText(from)}</strong> to <strong>{dateText(to)}</strong>
          </p>

          {periodError && (
            <p className="cu-banner danger txn-range-error" role="alert"><AlertCircle size={18} /> <span>{periodError}</span></p>
          )}
          {!periodError && eligibility.status === 'loading' && (
            <span className="sf-skeleton sf-skeleton-row" style={{ width: '60%' }} aria-label="Checking eligible transactions" role="status" />
          )}
          {!periodError && eligibility.status === 'error' && (
            <p className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{eligibility.error}</span></p>
          )}
          {!periodError && eligibility.status === 'ready' && !eligibility.data.canGenerate && (
            <p className="cu-banner danger">
              {`No statement can be generated for this period: it needs at least 2 eligible transactions and has ${eligibility.data.eligibleCount}.`}
            </p>
          )}
          {notice && <div className={`cu-banner ${notice.tone}`} role="status"><span>{notice.text}</span></div>}

          <div className="txn-account-card">
            <div className="txn-account-title">
              <span className="sf-icon account" aria-hidden="true"><FileText size={18} /></span>
              <div>
                <strong>Statement viewer</strong>
                <span>
                  {viewer.statement ? `${viewer.statement.statementReference} · version ${viewer.statement.versionNumber}` : 'Select a statement from the history below to view it here.'}
                </span>
              </div>
            </div>
            <div className="txn-account-actions">
              {viewer.statement && viewer.status === 'ready' && (
                <span className="sf-muted">
                  <Clock size={14} /> Last accessed {dateTimeText(new Date().toISOString())} · access ends in {Math.floor(secondsLeft / 60)}m {String(secondsLeft % 60).padStart(2, '0')}s
                </span>
              )}
              {viewer.statement && <button className="sf-btn outline" onClick={closeViewer}>Close</button>}
              {expired && viewer.statement && (
                <button className="sf-btn primary" onClick={() => openStatement(viewer.statement)}>Reopen statement</button>
              )}
            </div>
          </div>

          {viewer.status === 'loading' && <div className="sf-skeleton sf-skeleton-page" role="status" aria-label="Preparing statement" />}
          {viewer.status === 'error' && (
            <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{viewer.error}</span></div>
          )}
          {viewer.status === 'ready' && viewer.blob && !expired && (
            <ProtectedPdfViewer blob={viewer.blob} title={`Statement ${viewer.statement.statementReference}`} />
          )}
          {expired && (
            <p className="cu-banner danger" role="status">
              This document access has expired. Reopen the statement to view it again.
            </p>
          )}

          <div className="txn-period">
            <strong>Statement history</strong>
            <button className="sf-link-button" onClick={() => loadHistory(selectedAccount.id)}>
              <RefreshCw size={12} /> Refresh
            </button>
          </div>
          {history.status === 'loading' && (
            <div aria-label="Loading statement history" role="status">
              <span className="sf-skeleton sf-skeleton-row" style={{ width: '100%' }} />
              <span className="sf-skeleton sf-skeleton-row" style={{ width: '90%' }} />
              <span className="sf-skeleton sf-skeleton-row" style={{ width: '95%' }} />
            </div>
          )}
          {history.status === 'error' && <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{history.error}</span></div>}
          {history.status === 'ready' && available.length === 0 && (
            <p className="sf-muted centered">No statements have been generated for this account yet.</p>
          )}
          {history.status === 'ready' && available.length > 0 && (
            <div className="sf-table-wrap inset">
              <table className="sf-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Period</th>
                    <th>Version</th>
                    <th className="num">Opening</th>
                    <th className="num">Closing</th>
                    <th>Generated</th>
                    <th>Document</th>
                  </tr>
                </thead>
                <tbody>
                  {available.map((s) => (
                    <tr key={s.id}>
                      <td>{s.statementReference}</td>
                      <td>{dateText(s.periodStart)} – {dateText(s.periodEnd)}</td>
                      <td>
                        {s.versionNumber ? `v${s.versionNumber}` : '—'}{' '}
                        <small className="sf-muted">{VERSION_LABEL[s.versionStatus] || ''}</small>
                      </td>
                      <td className="num">{money(s.openingBalance)}</td>
                      <td className="num">{money(s.closingBalance)}</td>
                      <td>{dateTimeText(s.generatedAt)}</td>
                      <td>
                        {s.status === 'GENERATED' ? (
                          <button className="sf-btn outline" onClick={() => openStatement(s)}>View PDF</button>
                        ) : s.status === 'WITHDRAWN' ? (
                          <small className="sf-muted">Withdrawn while under review</small>
                        ) : (
                          <small className="sf-muted">Unavailable</small>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {history.status === 'ready' && expiredStatements.length > 0 && (
            <div className="txn-activity" aria-label="Statement activity">
              <strong>Activity</strong>
              <ul>
                {expiredStatements.map((s) => (
                  <li key={s.id}>
                    Statement {s.statementReference} was generated on {dateTimeText(s.generatedAt)}. Its access period has ended.
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </div>
  )
}
