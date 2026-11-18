import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, Download, FileText, Loader2, RefreshCw, ShieldOff } from 'lucide-react'
import { getAccountsByCustomer } from '../services/accountService'
import {
  createDownloadLink, downloadStatementPdf, generateStatement, getStatementEligibility, getStatementToken,
  listAccountStatements, revokeDownloadLinks, setStatementToken,
} from '../services/statementService'

const PAGE_SIZE = 10

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
  if (!err.response) return 'The API gateway cannot be reached. Check that the platform is running.'
  const status = err.response.status
  if (status === 401) return 'Your statement access token is missing, invalid or expired.'
  if (status === 403) return 'You are not authorised to perform this action for this account.'
  if (status === 410) return 'This download link has expired. Create a new link and try again.'
  if (status === 503) return 'A service needed for this statement is unavailable. Try again shortly.'
  return err.response.data?.message || 'The request failed. Please try again.'
}

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export default function StatementsPanel({ customerId, caseAccount }) {
  const [token, setToken] = useState(getStatementToken())
  const [tokenDraft, setTokenDraft] = useState('')

  const [accounts, setAccounts] = useState({ status: 'loading', data: [], error: '' })
  const [accountId, setAccountId] = useState(caseAccount?.id ?? '')
  const [accountTypeFilter, setAccountTypeFilter] = useState('ALL')
  const [eligibility, setEligibility] = useState({ status: 'idle', data: null, error: '' })

  const [periodType, setPeriodType] = useState('MONTHLY')
  const [month, setMonth] = useState(isoDay(new Date()).slice(0, 7))
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [generating, setGenerating] = useState(false)
  const [notice, setNotice] = useState(null)

  const [history, setHistory] = useState({ status: 'idle', items: [], page: 0, totalPages: 0, totalElements: 0, error: '' })
  const [busy, setBusy] = useState('')
  const [links, setLinks] = useState({})

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

  const accountTypes = useMemo(() => [...new Set(accountList.map((a) => a.accountType))], [accountList])
  const visibleAccounts = accountList.filter((a) => accountTypeFilter === 'ALL' || a.accountType === accountTypeFilter)
  const selectedAccount = visibleAccounts.find((a) => a.id === accountId) ?? visibleAccounts[0] ?? null

  const monthBounds = (value) => {
    const [y, m] = value.split('-').map(Number)
    return { from: `${value}-01`, to: isoDay(new Date(y, m, 0)) }
  }

  const period = periodType === 'MONTHLY' ? monthBounds(month) : { from, to }
  const periodReady = periodType === 'MONTHLY' ? Boolean(month) : Boolean(from && to && from <= to)

  useEffect(() => {
    if (!selectedAccount || !token || !periodReady) {
      setEligibility({ status: 'idle', data: null, error: '' })
      return undefined
    }
    let active = true
    setEligibility({ status: 'loading', data: null, error: '' })
    getStatementEligibility(selectedAccount.id, period.from, period.to)
      .then((data) => { if (active) setEligibility({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setEligibility({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [selectedAccount?.id, period.from, period.to, token])

  const loadHistory = (targetAccountId, page = 0) => {
    if (!targetAccountId || !getStatementToken()) {
      setHistory({ status: 'idle', items: [], page: 0, totalPages: 0, totalElements: 0, error: '' })
      return
    }
    setHistory((h) => ({ ...h, status: 'loading', error: '' }))
    listAccountStatements(targetAccountId, page, PAGE_SIZE)
      .then((data) => setHistory({
        status: 'ready',
        items: data.items,
        page: data.page,
        totalPages: data.totalPages,
        totalElements: data.totalElements,
        error: '',
      }))
      .catch((err) => setHistory((h) => ({ ...h, status: 'error', error: errorMessage(err) })))
  }

  useEffect(() => {
    if (selectedAccount) {
      setAccountId(selectedAccount.id)
      loadHistory(selectedAccount.id, 0)
    } else {
      loadHistory('', 0)
    }
  }, [selectedAccount?.id, token])

  const saveToken = (event) => {
    event.preventDefault()
    const value = tokenDraft.trim()
    setStatementToken(value)
    setToken(value)
    setTokenDraft('')
  }

  const clearToken = () => {
    setStatementToken('')
    setToken('')
    setHistory({ status: 'idle', items: [], page: 0, totalPages: 0, totalElements: 0, error: '' })
  }

  const buildRequest = () => {
    if (periodType === 'MONTHLY') {
      const [year, monthNumber] = month.split('-').map(Number)
      return { accountId: selectedAccount.id, periodType: 'MONTHLY', year, month: monthNumber }
    }
    return { accountId: selectedAccount.id, periodType: 'CUSTOM', from, to }
  }

  const canGenerate = Boolean(
    selectedAccount && token && !generating && periodReady &&
    eligibility.status === 'ready' && eligibility.data?.canGenerate,
  )

  const onGenerate = async () => {
    setGenerating(true)
    setNotice(null)
    try {
      const statement = await generateStatement(buildRequest())
      setNotice({ tone: 'success', text: `Statement ${statement.statementReference} is ready.` })
      loadHistory(selectedAccount.id, 0)
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setGenerating(false)
    }
  }

  const onDownload = async (statement) => {
    setBusy(statement.id)
    setNotice(null)
    try {
      const link = await createDownloadLink(statement.id)
      setLinks((current) => ({ ...current, [statement.id]: { expiresAt: link.expiresAt, token: link.token } }))
      const blob = await downloadStatementPdf(link.token)
      downloadBlob(blob, `${statement.statementReference}.pdf`)
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setBusy('')
    }
  }

  const onRevoke = async (statement) => {
    setBusy(statement.id)
    try {
      const result = await revokeDownloadLinks(statement.id)
      setLinks((current) => {
        const next = { ...current }
        delete next[statement.id]
        return next
      })
      setNotice({ tone: 'success', text: `Revoked ${result.revokedCount} active download link(s).` })
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setBusy('')
    }
  }

  if (!token) {
    return (
      <div className="txn">
        <form className="txn-account-card" onSubmit={saveToken}>
          <div className="txn-account-title">
            <span className="sf-icon account" aria-hidden="true"><ShieldOff size={18} /></span>
            <div>
              <strong>Statement access token required</strong>
              <span>Statements are released only to an authorised user. Paste the access token you were issued. It is kept for this browser tab only.</span>
            </div>
          </div>
          <div className="txn-account-actions">
            <input
              type="password"
              aria-label="Statement access token"
              value={tokenDraft}
              onChange={(e) => setTokenDraft(e.target.value)}
              autoComplete="off"
            />
            <button className="sf-btn primary" type="submit" disabled={!tokenDraft.trim()}>Use token</button>
          </div>
        </form>
      </div>
    )
  }

  return (
    <div className="txn">
      <div className="txn-subtab">Statements{selectedAccount ? ` · Account ${selectedAccount.accountNumber}` : ''}</div>

      {accounts.status === 'error' && (
        <div className="cu-banner danger" role="alert">
          <AlertCircle size={18} /> <span>{accounts.error}</span>
        </div>
      )}

      <div className="txn-filters">
        <label className="txn-field">
          <span>Account type</span>
          <select value={accountTypeFilter} onChange={(e) => setAccountTypeFilter(e.target.value)} disabled={accountList.length === 0}>
            <option value="ALL">All account types</option>
            {accountTypes.map((t) => (
              <option key={t} value={t}>{ACCOUNT_TYPE_LABEL[t] || t}</option>
            ))}
          </select>
        </label>
        <label className="txn-field">
          <span>Account</span>
          <select
            value={selectedAccount?.id ?? ''}
            onChange={(e) => setAccountId(e.target.value)}
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
        <div className="txn-field">
          <span>Period</span>
          <div className="sf-segmented">
            <button className={periodType === 'MONTHLY' ? 'active' : ''} onClick={() => setPeriodType('MONTHLY')}>Monthly</button>
            <button className={periodType === 'CUSTOM' ? 'active' : ''} onClick={() => setPeriodType('CUSTOM')}>Custom range</button>
          </div>
        </div>
        {periodType === 'MONTHLY' ? (
          <label className="txn-field">
            <span>Month</span>
            <input type="month" value={month} max={isoDay(new Date()).slice(0, 7)} onChange={(e) => setMonth(e.target.value)} />
          </label>
        ) : (
          <>
            <label className="txn-field">
              <span>From</span>
              <input type="date" value={from} max={to || isoDay(new Date())} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label className="txn-field">
              <span>To</span>
              <input type="date" value={to} min={from || undefined} max={isoDay(new Date())} onChange={(e) => setTo(e.target.value)} />
            </label>
          </>
        )}
        <div className="txn-field">
          <span>&nbsp;</span>
          <button className="sf-btn primary" disabled={!canGenerate} onClick={onGenerate}>
            {generating ? <Loader2 size={15} className="cu-spin" /> : <FileText size={15} />} Generate Statement
          </button>
        </div>
      </div>

      {selectedAccount && periodReady && (
        <p className="sf-muted">
          {eligibility.status === 'loading' && 'Checking eligible transactions…'}
          {eligibility.status === 'error' && <span className="txn-range-error">{eligibility.error}</span>}
          {eligibility.status === 'ready' && eligibility.data.canGenerate && (
            `${eligibility.data.eligibleCount} eligible transaction(s) in this period. You can generate the statement.`
          )}
          {eligibility.status === 'ready' && !eligibility.data.canGenerate && (
            `No statement can be generated for this period: at least 2 eligible transactions are needed and ${eligibility.data.eligibleCount} are available. Transactions under review are not included.`
          )}
        </p>
      )}

      {notice && (
        <div className={`cu-banner ${notice.tone}`} role="status">
          <span>{notice.text}</span>
        </div>
      )}

      {history.status === 'loading' && (
        <p className="sf-muted centered"><Loader2 size={15} className="cu-spin" /> Loading statements…</p>
      )}

      {history.status === 'error' && (
        <div className="cu-banner danger" role="alert">
          <AlertCircle size={18} /> <span>{history.error}</span>
          <button className="home-btn" onClick={() => loadHistory(selectedAccount?.id, history.page)}>Retry</button>
        </div>
      )}

      {history.status === 'ready' && history.items.length === 0 && (
        <div className="sf-empty centered">
          <p className="sf-muted">No statements have been generated for this account yet.</p>
          <button className="sf-btn primary" disabled={!canGenerate} onClick={onGenerate}>Generate Statement</button>
        </div>
      )}

      {history.status === 'ready' && history.items.length > 0 && (
        <>
          <div className="sf-table-wrap inset">
            <table className="sf-table">
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Period</th>
                  <th className="num">Opening</th>
                  <th className="num">Closing</th>
                  <th>Status</th>
                  <th>Generated</th>
                  <th>Download</th>
                </tr>
              </thead>
              <tbody>
                {history.items.map((s) => {
                  const link = links[s.id]
                  return (
                    <tr key={s.id}>
                      <td>{s.statementReference}</td>
                      <td>{dateText(s.periodStart)} – {dateText(s.periodEnd)}</td>
                      <td className="num">{money(s.openingBalance)}</td>
                      <td className="num">{money(s.closingBalance)}</td>
                      <td>
                        {s.status === 'GENERATED' && 'Generated'}
                        {s.status === 'WITHDRAWN' && 'Withdrawn: under fraud review'}
                        {s.status === 'FAILED' && `Failed${s.failureReason ? ` · ${s.failureReason}` : ''}`}
                      </td>
                      <td>{dateTimeText(s.generatedAt)}</td>
                      <td>
                        {s.status === 'WITHDRAWN' ? (
                          <small className="sf-muted">Not available. Regenerate this period once the review is resolved.</small>
                        ) : s.status === 'GENERATED' ? (
                          <div className="txn-download">
                            <button className="sf-btn outline" disabled={busy === s.id} onClick={() => onDownload(s)}>
                              {busy === s.id ? <Loader2 size={14} className="cu-spin" /> : <Download size={14} />} Download PDF
                            </button>
                            {link && (
                              <small>
                                Link expires {dateTimeText(link.expiresAt)}{' '}
                                <button className="sf-link-button" disabled={busy === s.id} onClick={() => onRevoke(s)}>Revoke</button>
                              </small>
                            )}
                          </div>
                        ) : '—'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="txn-period">
            <button
              className="sf-btn outline"
              disabled={history.page <= 0}
              onClick={() => loadHistory(selectedAccount.id, history.page - 1)}
            >
              ‹ Newer
            </button>
            <span className="sf-muted">
              Page {history.page + 1} of {Math.max(history.totalPages, 1)} · {history.totalElements} statement(s)
            </span>
            <button
              className="sf-btn outline"
              disabled={history.page + 1 >= history.totalPages}
              onClick={() => loadHistory(selectedAccount.id, history.page + 1)}
            >
              Older ›
            </button>
          </div>
        </>
      )}

      <div className="txn-period">
        <button className="sf-link-button" onClick={clearToken}>Forget access token</button>
        <button className="sf-link-button" onClick={() => loadHistory(selectedAccount?.id, history.page)}>
          <RefreshCw size={12} /> Refresh
        </button>
      </div>
    </div>
  )
}
