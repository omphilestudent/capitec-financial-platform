import { useEffect, useState } from 'react'
import {
  AlertCircle, CheckCircle2, ChevronDown, ChevronRight, Loader2, Pencil, Trash2, Wallet,
} from 'lucide-react'
import { getAccountById, removeAccount, updateAccount } from '../services/accountService'
import './Accounts.css'

export const ACCOUNT_TYPES = [
  { value: 'SAVINGS', label: 'Savings' },
  { value: 'CHEQUE', label: 'Cheque' },
  { value: 'CREDIT', label: 'Credit' },
]
export const ACCOUNT_TYPE_LABEL = Object.fromEntries(ACCOUNT_TYPES.map((t) => [t.value, t.label]))

// CLOSED is only set by removing the account, so it is not an editable option.
const EDITABLE_STATUSES = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'FROZEN', label: 'Frozen' },
]
export const ACCOUNT_STATUS_LABEL = { ACTIVE: 'Active', FROZEN: 'Frozen', CLOSED: 'Closed' }

export const formatMoney = (value, currency = 'ZAR') =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency }).format(Number(value ?? 0))

export const formatDate = (value) =>
  value ? new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

const errorMessage = (err) => {
  if (!err.response) return 'Cannot reach the server. Check that the backend is running and try again.'
  return err.response.data?.message || 'Something went wrong. Please try again.'
}

// account-service has no update or close endpoint yet, so Edit and Remove stay
// disabled. Set this to true once it exposes PUT and DELETE on /accounts/{id}.
const ACCOUNT_CHANGES_AVAILABLE = false
const CHANGES_UNAVAILABLE_TEXT =
  'Editing and removing accounts is not available yet: account-service does not expose update or close endpoints.'

export function StatusBadge({ status }) {
  return (
    <span className={`acc-status ${status.toLowerCase()}`}>{ACCOUNT_STATUS_LABEL[status] || status}</span>
  )
}

/**
 * Financial account record page. Opened from a customer's Accounts tab.
 * onBack(customerId) returns to that customer; onRemoved(message) is called
 * after the account is closed so the customer page can show a notice.
 */
export default function AccountRecord({ accountId, onBack, onRemoved }) {
  const [account, setAccount] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')

  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({ accountType: 'SAVINGS', status: 'ACTIVE' })
  const [saving, setSaving] = useState(false)

  const [confirmRemove, setConfirmRemove] = useState(false)
  const [removing, setRemoving] = useState(false)

  useEffect(() => {
    // The parent renders this with key={accountId}, so state starts fresh per account.
    let active = true
    getAccountById(accountId)
      .then((data) => { if (active) setAccount(data) })
      .catch((err) => { if (active) setLoadError(errorMessage(err)) })
    return () => { active = false }
  }, [accountId])

  if (!account) {
    return (
      <section className="acc-page">
        {loadError ? (
          <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{loadError}</span></div>
        ) : (
          <p className="sf-muted centered">Loading account…</p>
        )}
        <button className="sf-pill-btn" onClick={() => onBack(null)}>Back to customers</button>
      </section>
    )
  }

  const closed = account.status === 'CLOSED'

  const startEdit = () => {
    setForm({ accountType: account.accountType, status: account.status })
    setActionError('')
    setNotice('')
    setConfirmRemove(false)
    setEditing(true)
  }

  const handleSave = async (event) => {
    event.preventDefault()
    setSaving(true)
    setActionError('')
    try {
      const updated = await updateAccount(account.id, {
        accountType: form.accountType,
        status: form.status,
      })
      setAccount(updated)
      setEditing(false)
      setNotice('Account updated successfully.')
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleRemove = async () => {
    setRemoving(true)
    setActionError('')
    try {
      await removeAccount(account.id)
      onRemoved(`Account ${account.accountNumber} was removed. It is closed and kept on the customer's record.`)
    } catch (err) {
      setActionError(errorMessage(err))
      setRemoving(false)
    }
  }

  return (
    <section className="sf-record acc-page">
      <header className="sf-record-head acc-record-head">
        <div className="sf-title-row">
          <div className="sf-title">
            <span className="sf-icon account" aria-hidden="true"><Wallet size={20} /></span>
            <div>
              <span className="sf-kicker">Financial Account</span>
              <h1>{account.accountNumber}</h1>
            </div>
          </div>

          <div className="sf-actions">
            <button className="sf-pill-btn" onClick={() => onBack(account.customerId)}>Back to customer</button>
            <button
              className="sf-pill-btn"
              onClick={startEdit}
              disabled={!ACCOUNT_CHANGES_AVAILABLE || closed || editing}
              title={ACCOUNT_CHANGES_AVAILABLE ? undefined : CHANGES_UNAVAILABLE_TEXT}
            >
              <Pencil size={14} /> Edit
            </button>
            <button
              className="sf-pill-btn acc-danger"
              onClick={() => { setNotice(''); setEditing(false); setConfirmRemove(true) }}
              disabled={!ACCOUNT_CHANGES_AVAILABLE || closed || confirmRemove}
              title={ACCOUNT_CHANGES_AVAILABLE ? undefined : CHANGES_UNAVAILABLE_TEXT}
            >
              <Trash2 size={14} /> Remove
            </button>
          </div>
        </div>

        <dl className="sf-highlights">
          <div><dt>Balance</dt><dd>{formatMoney(account.balance, account.currency)}</dd></div>
          <div><dt>Status</dt><dd><StatusBadge status={account.status} /></dd></div>
          <div><dt>Account Type</dt><dd>{ACCOUNT_TYPE_LABEL[account.accountType] || account.accountType}</dd></div>
        </dl>
      </header>

      {!ACCOUNT_CHANGES_AVAILABLE && (
        <p className="acc-unavailable sf-muted">{CHANGES_UNAVAILABLE_TEXT}</p>
      )}

      {notice && (
        <div className="cu-banner success" role="status"><CheckCircle2 size={18} /> <span>{notice}</span></div>
      )}

      {actionError && (
        <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{actionError}</span></div>
      )}

      {confirmRemove && (
        <div className="acc-confirm" role="alertdialog" aria-labelledby="acc-confirm-title">
          <div>
            <strong id="acc-confirm-title">Remove account {account.accountNumber}?</strong>
            <p>
              The account will be closed and kept on the customer's record. Its balance must be
              zero before it can be removed.
            </p>
          </div>
          <div className="acc-confirm-actions">
            <button className="sf-btn outline" onClick={() => setConfirmRemove(false)} disabled={removing}>
              Cancel
            </button>
            <button className="sf-btn acc-danger-solid" onClick={handleRemove} disabled={removing}>
              {removing ? <Loader2 size={15} className="cu-spin" /> : null}
              {removing ? 'Removing…' : 'Remove account'}
            </button>
          </div>
        </div>
      )}

      <div className="sf-body single">
        <div className="sf-card sf-main">
          <Section title="Account Information">
            {editing ? (
              <form onSubmit={handleSave} className="acc-form" aria-busy={saving}>
                <div className="acc-form-grid">
                  <div className="acc-field">
                    <label htmlFor="acc-type">Account type</label>
                    <select
                      id="acc-type"
                      value={form.accountType}
                      disabled={saving}
                      onChange={(e) => setForm((f) => ({ ...f, accountType: e.target.value }))}
                    >
                      {ACCOUNT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                    </select>
                  </div>
                  <div className="acc-field">
                    <label htmlFor="acc-status">Status</label>
                    <select
                      id="acc-status"
                      value={form.status}
                      disabled={saving}
                      onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
                    >
                      {EDITABLE_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                    </select>
                    <small>Frozen accounts cannot transact. Remove the account to close it.</small>
                  </div>
                </div>
                <div className="acc-form-actions">
                  <button type="button" className="sf-btn outline" onClick={() => setEditing(false)} disabled={saving}>
                    Cancel
                  </button>
                  <button type="submit" className="sf-btn primary" disabled={saving}>
                    {saving ? <Loader2 size={15} className="cu-spin" /> : null}
                    {saving ? 'Saving…' : 'Save'}
                  </button>
                </div>
              </form>
            ) : (
              <FieldGrid
                fields={[
                  ['Account Number', account.accountNumber],
                  ['Account Type', ACCOUNT_TYPE_LABEL[account.accountType] || account.accountType],
                  ['Status', <StatusBadge key="s" status={account.status} />],
                  ['Currency', account.currency],
                  ['Account ID', account.id],
                  ['Customer ID', account.customerId],
                ]}
              />
            )}
          </Section>

          <Section title="Summary">
            <FieldGrid
              fields={[
                ['Balance', formatMoney(account.balance, account.currency)],
                ['Opened', formatDate(account.createdAt)],
                ['Last updated', formatDate(account.updatedAt)],
              ]}
            />
          </Section>
        </div>
      </div>
    </section>
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

export function FieldGrid({ fields }) {
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
