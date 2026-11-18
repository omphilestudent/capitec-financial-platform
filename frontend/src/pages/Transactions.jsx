import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, CheckCircle2, ChevronRight, Loader2, Plus, RefreshCw, Search } from 'lucide-react'
import { getCustomers } from '../services/customerService'
import { getAccountsByCustomer } from '../services/accountService'
import {
  createTransaction, getRecentTransactions, getTransactionById,
} from '../services/transactionService'
import './Transactions.css'

const TYPES = [
  { value: 'CREDIT', label: 'Credit (money in)' },
  { value: 'DEBIT', label: 'Debit (money out)' },
]

const STATUS_LABEL = { PENDING: 'Pending', COMPLETED: 'Completed', FAILED: 'Failed' }

const EMPTY_FORM = { customerId: '', accountId: '', type: 'CREDIT', amount: '', currency: 'ZAR', description: '' }

const errorMessage = (err) => {
  if (!err.response) return 'The API gateway cannot be reached. Check that the platform is running.'
  const data = err.response.data
  if (data?.fieldErrors) return Object.values(data.fieldErrors).join(' ')
  return data?.message || 'The request failed. Please try again.'
}

const formatMoney = (value, currency = 'ZAR') =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency }).format(Number(value ?? 0))

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

export default function Transactions({ initialTransactionId = null }) {
  const [view, setView] = useState(initialTransactionId ? 'detail' : 'list')
  const [selectedId, setSelectedId] = useState(initialTransactionId)

  const [transactions, setTransactions] = useState({ status: 'loading', data: [], error: '' })
  const [query, setQuery] = useState('')
  const [notice, setNotice] = useState(null) // { tone: 'success' | 'danger', text }

  const [detail, setDetail] = useState({ status: 'idle', data: null, error: '' })

  const [customers, setCustomers] = useState({ status: 'loading', data: [], error: '' })
  const [accounts, setAccounts] = useState({ status: 'idle', data: [], error: '' })
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const loadTransactions = () => {
    setTransactions((t) => ({ ...t, status: 'loading', error: '' }))
    getRecentTransactions()
      .then((data) => setTransactions({ status: 'ready', data, error: '' }))
      .catch((err) => setTransactions({ status: 'error', data: [], error: errorMessage(err) }))
  }

  useEffect(() => {
    let active = true
    getRecentTransactions()
      .then((data) => { if (active) setTransactions({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setTransactions({ status: 'error', data: [], error: errorMessage(err) }) })
    getCustomers()
      .then((data) => { if (active) setCustomers({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setCustomers({ status: 'error', data: [], error: errorMessage(err) }) })
    return () => { active = false }
  }, [])

  // Opens a transaction's detail, reading it from the service.
  useEffect(() => {
    if (!selectedId || view !== 'detail') return undefined
    let active = true
    getTransactionById(selectedId)
      .then((data) => { if (active) setDetail({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setDetail({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
  }, [selectedId, view])

  // Accounts for the chosen customer come from account-service.
  useEffect(() => {
    if (!form.customerId) {
      setAccounts({ status: 'idle', data: [], error: '' })
      return undefined
    }
    let active = true
    setAccounts({ status: 'loading', data: [], error: '' })
    getAccountsByCustomer(form.customerId)
      .then((data) => { if (active) setAccounts({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setAccounts({ status: 'error', data: [], error: errorMessage(err) }) })
    return () => { active = false }
  }, [form.customerId])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return transactions.data
    return transactions.data.filter((t) =>
      [t.transactionReference, t.type, t.status, t.description || '', t.accountId]
        .some((v) => String(v).toLowerCase().includes(q)),
    )
  }, [transactions.data, query])

  const openDetail = (id) => {
    setNotice(null)
    setDetail({ status: 'loading', data: null, error: '' })
    setSelectedId(id)
    setView('detail')
  }

  const goToList = () => {
    setView('list')
    setSelectedId(null)
    setFieldErrors({})
  }

  const validate = () => {
    const errors = {}
    if (!form.customerId) errors.customerId = 'Choose a customer.'
    if (!form.accountId) errors.accountId = 'Choose an account.'
    const amount = Number(form.amount)
    if (!form.amount || Number.isNaN(amount) || amount <= 0) errors.amount = 'Enter an amount greater than zero.'
    if (!/^[A-Za-z]{3}$/.test(form.currency.trim())) errors.currency = 'Enter a three-letter currency code, for example ZAR.'
    if (form.description.length > 500) errors.description = 'Description cannot exceed 500 characters.'
    return errors
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSaving(true)
    setNotice(null)
    try {
      const created = await createTransaction({
        accountId: form.accountId,
        type: form.type,
        amount: Number(form.amount),
        currency: form.currency.trim().toUpperCase(),
        description: form.description.trim() || null,
      })
      setForm(EMPTY_FORM)
      setNotice({ tone: 'success', text: `Transaction ${created.transactionReference} was recorded.` })
      loadTransactions()
      openDetail(created.id)
    } catch (err) {
      setNotice({ tone: 'danger', text: errorMessage(err) })
    } finally {
      setSaving(false)
    }
  }

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((f) => ({ ...f, [name]: value, ...(name === 'customerId' ? { accountId: '' } : {}) }))
    setFieldErrors((e) => ({ ...e, [name]: undefined }))
  }

  if (view === 'new') {
    return (
      <section className="tx-page">
        <header className="tx-heading">
          <div>
            <span className="sf-kicker">Transactions</span>
            <h1>New transaction</h1>
          </div>
          <button className="sf-pill-btn" onClick={goToList}>Back to transactions</button>
        </header>

        {notice && (
          <div className={`cu-banner ${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'}>
            {notice.tone === 'danger' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
            <span>{notice.text}</span>
          </div>
        )}

        {customers.status === 'loading' && <p className="sf-muted">Loading customers…</p>}
        {customers.status === 'error' && (
          <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{customers.error}</span></div>
        )}

        {customers.status === 'ready' && (
          <form className="sf-card tx-form" onSubmit={handleSubmit} noValidate aria-busy={saving}>
            <div className="tx-grid">
              <Field label="Customer" id="tx-customer" error={fieldErrors.customerId} required>
                <select id="tx-customer" name="customerId" value={form.customerId} onChange={handleChange} disabled={saving}>
                  <option value="">Select a customer</option>
                  {customers.data.map((c) => (
                    <option key={c.id} value={c.id}>{c.firstName} {c.lastName}</option>
                  ))}
                </select>
              </Field>

              <Field label="Account" id="tx-account" error={fieldErrors.accountId} required>
                <select
                  id="tx-account"
                  name="accountId"
                  value={form.accountId}
                  onChange={handleChange}
                  disabled={saving || !form.customerId || accounts.status !== 'ready'}
                >
                  <option value="">
                    {!form.customerId ? 'Choose a customer first' : accounts.status === 'loading' ? 'Loading accounts…' : 'Select an account'}
                  </option>
                  {accounts.data.map((a) => (
                    <option key={a.id} value={a.id}>{a.accountNumber} · {a.accountType} · {a.status}</option>
                  ))}
                </select>
                {accounts.status === 'error' && <small className="cu-error">{accounts.error}</small>}
                {form.customerId && accounts.status === 'ready' && accounts.data.length === 0 && (
                  <small className="cu-helper">This customer has no accounts. Open one on the Customers page first.</small>
                )}
              </Field>

              <Field label="Type" id="tx-type" required>
                <select id="tx-type" name="type" value={form.type} onChange={handleChange} disabled={saving}>
                  {TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </Field>

              <Field label="Amount" id="tx-amount" error={fieldErrors.amount} required>
                <input id="tx-amount" name="amount" type="number" min="0.01" step="0.01" value={form.amount} onChange={handleChange} disabled={saving} />
              </Field>

              <Field label="Currency" id="tx-currency" error={fieldErrors.currency} required>
                <input id="tx-currency" name="currency" maxLength={3} value={form.currency} onChange={handleChange} disabled={saving} />
              </Field>

              <Field label="Description" id="tx-description" error={fieldErrors.description}>
                <input id="tx-description" name="description" maxLength={500} value={form.description} onChange={handleChange} disabled={saving} />
              </Field>
            </div>

            <footer className="tx-form-actions">
              <button type="button" className="sf-btn outline" onClick={goToList} disabled={saving}>Cancel</button>
              <button type="submit" className="sf-btn primary" disabled={saving}>
                {saving ? <Loader2 size={15} className="cu-spin" /> : null}
                {saving ? 'Recording…' : 'Record transaction'}
              </button>
            </footer>
          </form>
        )}
      </section>
    )
  }

  if (view === 'detail') {
    return (
      <section className="tx-page">
        <header className="tx-heading">
          <div>
            <span className="sf-kicker">Transaction</span>
            <h1>{detail.data?.transactionReference || 'Transaction'}</h1>
          </div>
          <button className="sf-pill-btn" onClick={goToList}>Back to transactions</button>
        </header>

        {notice && (
          <div className={`cu-banner ${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'}>
            {notice.tone === 'danger' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
            <span>{notice.text}</span>
          </div>
        )}

        {detail.status === 'loading' && <p className="sf-muted centered">Loading transaction…</p>}
        {detail.status === 'error' && (
          <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{detail.error}</span></div>
        )}

        {detail.status === 'ready' && detail.data && (
          <div className="sf-card tx-detail">
            <dl className="tx-detail-grid">
              <div><dt>Reference</dt><dd>{detail.data.transactionReference}</dd></div>
              <div><dt>Type</dt><dd>{detail.data.type}</dd></div>
              <div><dt>Amount</dt><dd>{formatMoney(detail.data.amount, detail.data.currency)}</dd></div>
              <div><dt>Status</dt><dd>{STATUS_LABEL[detail.data.status] || detail.data.status}</dd></div>
              <div><dt>Account ID</dt><dd>{detail.data.accountId}</dd></div>
              <div><dt>Transaction ID</dt><dd>{detail.data.id}</dd></div>
              <div><dt>Description</dt><dd>{detail.data.description || '—'}</dd></div>
              <div><dt>Created</dt><dd>{formatDate(detail.data.createdAt)}</dd></div>
            </dl>
          </div>
        )}
      </section>
    )
  }

  // list view
  return (
    <section className="tx-page">
      <header className="tx-heading">
        <div>
          <span className="sf-kicker">Transactions</span>
          <h1>Recent transactions</h1>
          <span className="sf-muted">
            {transactions.status === 'ready' ? `${filtered.length} shown · most recent 100` : 'Loading…'}
          </span>
        </div>
        <div className="tx-heading-actions">
          <button className="sf-pill-btn" onClick={loadTransactions} aria-label="Refresh transactions">
            <RefreshCw size={14} /> Refresh
          </button>
          <button className="sf-btn primary" onClick={() => { setNotice(null); setView('new') }}>
            <Plus size={15} /> New transaction
          </button>
        </div>
      </header>

      {notice && (
        <div className={`cu-banner ${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'}>
          {notice.tone === 'danger' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
          <span>{notice.text}</span>
        </div>
      )}

      <div className="tx-toolbar">
        <label className="sf-search">
          <Search size={15} aria-hidden="true" />
          <input
            type="search"
            placeholder="Search this list..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search transactions"
          />
        </label>
      </div>

      {transactions.status === 'error' && (
        <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{transactions.error}</span></div>
      )}

      <div className="sf-card sf-table-wrap">
        <table className="sf-table">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Type</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Created</th>
              <th className="narrow" aria-label="Open" />
            </tr>
          </thead>
          <tbody>
            {transactions.status === 'loading' && (
              <tr><td colSpan={6} className="sf-empty">Loading transactions…</td></tr>
            )}
            {transactions.status === 'ready' && transactions.data.length === 0 && (
              <tr><td colSpan={6} className="sf-empty">No transactions yet. Select <strong>New transaction</strong> to record one.</td></tr>
            )}
            {filtered.map((t) => (
              <tr key={t.id} onClick={() => openDetail(t.id)}>
                <td><button className="sf-link-button" onClick={(e) => { e.stopPropagation(); openDetail(t.id) }}>{t.transactionReference}</button></td>
                <td>{t.type}</td>
                <td>{formatMoney(t.amount, t.currency)}</td>
                <td>{STATUS_LABEL[t.status] || t.status}</td>
                <td>{formatDate(t.createdAt)}</td>
                <td className="narrow"><ChevronRight size={15} aria-hidden="true" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function Field({ label, id, error, required, children }) {
  return (
    <div className={`tx-field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>
        {label}
        {required && <abbr title="required" className="cu-required">*</abbr>}
      </label>
      {children}
      {error && <small className="cu-error">{error}</small>}
    </div>
  )
}
