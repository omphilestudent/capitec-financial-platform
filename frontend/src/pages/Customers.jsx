import { useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, CheckCircle2, ChevronRight, Loader2, Plus, RefreshCw, Search, Users, Wallet,
} from 'lucide-react'
import { createCustomer, getCustomerById, getCustomers } from '../services/customerService'
import { createAccount, getAccountsByCustomer } from '../services/accountService'
import AccountRecord, {
  ACCOUNT_TYPE_LABEL, ACCOUNT_TYPES, FieldGrid, StatusBadge, formatDate, formatMoney,
} from './Accounts'
import './Customers.css'

// customer-service accepts only these two client types.
const CLIENT_TYPES = [
  {
    value: 'INDIVIDUAL',
    label: 'Individual',
    description: 'A personal banking customer.',
  },
  {
    value: 'BUSINESS',
    label: 'Business',
    description: 'A company or other organisation.',
  },
]

const TYPE_LABEL = Object.fromEntries(CLIENT_TYPES.map((t) => [t.value, t.label]))

const EMPTY_FORM = { firstName: '', lastName: '', email: '', phoneNumber: '' }
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const FIELDS = [
  { name: 'firstName', label: 'First name', required: true, autoComplete: 'given-name', maxLength: 100 },
  { name: 'lastName', label: 'Last name', required: true, autoComplete: 'family-name', maxLength: 100 },
  { name: 'email', label: 'Email address', type: 'email', required: true, autoComplete: 'email', maxLength: 255 },
  {
    name: 'phoneNumber',
    label: 'Phone number',
    type: 'tel',
    autoComplete: 'tel',
    maxLength: 20,
    helper: 'Optional. Include the country code, for example +27 82 555 0100.',
  },
]

const NO_BANNER = { text: '', tone: 'success' }

// Mirrors the backend CreateCustomerRequest constraints.
function validate(values) {
  const errors = {}
  FIELDS.forEach(({ name, label, required, maxLength }) => {
    const value = values[name].trim()
    if (required && !value) errors[name] = `${label} is required`
    else if (value.length > maxLength) errors[name] = `${label} cannot exceed ${maxLength} characters`
  })
  if (!errors.email && values.email.trim() && !EMAIL_PATTERN.test(values.email.trim())) {
    errors.email = 'Enter a valid email address, for example name@example.com'
  }
  return errors
}

// Where the user is, kept per browser tab so a refresh returns them to the same screen.
const NAV_KEY = 'fp.customers.nav'

function readSavedNav() {
  try {
    const saved = JSON.parse(window.sessionStorage.getItem(NAV_KEY) || 'null')
    if (saved?.view === 'detail' && saved.customerId) return saved
    if (saved?.view === 'account' && saved.customerId && saved.accountId) return saved
  } catch {
    // Storage unavailable or corrupt: start at the list.
  }
  return null
}

function saveNav(nav) {
  try {
    if (nav) window.sessionStorage.setItem(NAV_KEY, JSON.stringify(nav))
    else window.sessionStorage.removeItem(NAV_KEY)
  } catch {
    // Storage unavailable: navigation still works, it just will not survive a refresh.
  }
}

const errorMessage = (err) => {
  if (!err.response) return 'Cannot reach the server. Check that the backend is running and try again.'
  return err.response.data?.message || 'Something went wrong. Please try again.'
}

export default function Customers({ openCustomerId = null }) {
  const [savedNav] = useState(readSavedNav)

  const [view, setView] = useState(savedNav ? savedNav.view : 'list') // list | pick | new | detail | account
  const [activeCustomerId, setActiveCustomerId] = useState(savedNav?.customerId ?? null)
  const [activeAccountId, setActiveAccountId] = useState(savedNav?.accountId ?? null)

  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [query, setQuery] = useState('')

  const [clientType, setClientType] = useState('INDIVIDUAL')
  const [formData, setFormData] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const [selected, setSelected] = useState(null)
  const [accounts, setAccounts] = useState([])
  const [accountsError, setAccountsError] = useState('')
  const [detailTab, setDetailTab] = useState('details') // details | accounts
  const [newAccountType, setNewAccountType] = useState('SAVINGS')
  const [openingAccount, setOpeningAccount] = useState(false)
  const [banner, setBanner] = useState(NO_BANNER)

  const loadCustomers = async () => {
    setLoading(true)
    setLoadError('')
    try {
      setCustomers(await getCustomers())
    } catch (err) {
      setLoadError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    getCustomers()
      .then((data) => { if (active) setCustomers(data) })
      .catch((err) => { if (active) setLoadError(errorMessage(err)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const loadCustomer = async (customerId) => {
    setSelected(null)
    setAccounts([])
    setAccountsError('')
    try {
      const [customer, list] = await Promise.all([
        getCustomerById(customerId),
        getAccountsByCustomer(customerId),
      ])
      setSelected(customer)
      setAccounts(list)
    } catch (err) {
      setBanner({ text: errorMessage(err), tone: 'danger' })
    }
  }

  // After a refresh on the customer record, reload that customer.
  useEffect(() => {
    if (openCustomerId) {
      setActiveCustomerId(openCustomerId)
      setDetailTab('details')
      setView('detail')
      loadCustomer(openCustomerId)
      return
    }
    if (savedNav?.view === 'detail') loadCustomer(savedNav.customerId)
  }, [])

  useEffect(() => {
    if (view === 'detail' && activeCustomerId) {
      saveNav({ view, customerId: activeCustomerId })
    } else if (view === 'account' && activeCustomerId && activeAccountId) {
      saveNav({ view, customerId: activeCustomerId, accountId: activeAccountId })
    } else {
      saveNav(null)
    }
  }, [view, activeCustomerId, activeAccountId])

  const filtered = useMemo(() => {
    // customer-service does not sort, so sort newest first here.
    const newestFirst = [...customers].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
    const q = query.trim().toLowerCase()
    if (!q) return newestFirst
    return newestFirst.filter((c) =>
      [c.firstName, c.lastName, c.email, c.phoneNumber || '', TYPE_LABEL[c.clientType] || '']
        .some((v) => v.toLowerCase().includes(q)),
    )
  }, [customers, query])

  const goToList = () => {
    setView('list')
    setSelected(null)
    setAccounts([])
    setActiveCustomerId(null)
    setActiveAccountId(null)
    setBanner(NO_BANNER)
    setFormError('')
    setFieldErrors({})
  }

  const openDetail = (id) => {
    setBanner(NO_BANNER)
    setDetailTab('details')
    setActiveCustomerId(id)
    setView('detail')
    loadCustomer(id)
  }

  const openAccount = (accountId) => {
    setBanner(NO_BANNER)
    setActiveAccountId(accountId)
    setView('account')
  }

  // Back from an account record to its customer. A null id means the account never loaded.
  const backFromAccount = (customerId) => {
    if (customerId) openDetail(customerId)
    else goToList()
  }

  const accountRemoved = (message) => {
    openDetail(activeCustomerId)
    setBanner({ text: message, tone: 'success' })
  }

  const handleChange = (event) => {
    const { name, value } = event.target
    setFormData((prev) => ({ ...prev, [name]: value }))
    setFieldErrors((prev) => {
      if (!prev[name]) return prev
      const next = { ...prev }
      delete next[name]
      return next
    })
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setFormError('')

    const errors = validate(formData)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSaving(true)
    try {
      const created = await createCustomer({
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        email: formData.email.trim(),
        phoneNumber: formData.phoneNumber.trim() || null,
        clientType,
      })
      setFormData(EMPTY_FORM)
      setSelected(created)
      setAccounts([])
      setDetailTab('details')
      setActiveCustomerId(created.id)
      setBanner({ text: 'Customer created successfully.', tone: 'success' })
      setView('detail')
      loadCustomers()
    } catch (err) {
      setFormError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleOpenAccount = async (event) => {
    event.preventDefault()
    setOpeningAccount(true)
    setAccountsError('')
    try {
      const created = await createAccount({ customerId: selected.id, accountType: newAccountType })
      setAccounts((list) => [...list, created])
      setBanner({ text: `Account ${created.accountNumber} opened.`, tone: 'success' })
    } catch (err) {
      setAccountsError(errorMessage(err))
    } finally {
      setOpeningAccount(false)
    }
  }

  /* ---------- Views ---------- */

  if (view === 'account' && activeAccountId) {
    return (
      <AccountRecord
        key={activeAccountId}
        accountId={activeAccountId}
        onBack={backFromAccount}
        onRemoved={accountRemoved}
      />
    )
  }

  if (view === 'pick') {
    return (
      <section className="cu-picker-wrap">
        <div className="cu-picker sf-card">
          <h1>New Client</h1>

          <div className="cu-options" role="radiogroup" aria-label="Client type">
            {CLIENT_TYPES.map((type) => (
              <label key={type.value} className={`cu-option${clientType === type.value ? ' selected' : ''}`}>
                <input
                  type="radio"
                  name="clientType"
                  value={type.value}
                  checked={clientType === type.value}
                  onChange={() => setClientType(type.value)}
                />
                <span>
                  <strong>{type.label}</strong>
                  <small>{type.description}</small>
                </span>
              </label>
            ))}
          </div>

          <footer className="cu-picker-actions">
            <button className="sf-btn outline" onClick={goToList}>Cancel</button>
            <button className="sf-btn primary" onClick={() => { setFormData(EMPTY_FORM); setView('new') }}>
              Next
            </button>
          </footer>
        </div>
      </section>
    )
  }

  if (view === 'new') {
    return (
      <section className="cu-page">
        <CustomersHeading
          kicker="Customers"
          title="New Client"
          meta={TYPE_LABEL[clientType]}
          actions={
            <button className="sf-pill-btn" onClick={() => setView('pick')}>Change type</button>
          }
        />

        <form onSubmit={handleSubmit} noValidate className="sf-card cu-form" aria-busy={saving}>
          {formError && (
            <div className="cu-banner danger" role="alert">
              <AlertCircle size={18} /> <span>{formError}</span>
            </div>
          )}

          <div className="cu-grid">
            {FIELDS.map((field) => {
              const error = fieldErrors[field.name]
              const id = `customer-${field.name}`
              return (
                <div key={field.name} className={`cu-field${error ? ' has-error' : ''}`}>
                  <label htmlFor={id}>
                    {field.label}
                    {field.required && <abbr title="required" className="cu-required">*</abbr>}
                  </label>
                  <input
                    id={id}
                    name={field.name}
                    type={field.type || 'text'}
                    value={formData[field.name]}
                    onChange={handleChange}
                    required={field.required}
                    autoComplete={field.autoComplete}
                    maxLength={field.maxLength}
                    disabled={saving}
                    aria-invalid={Boolean(error)}
                    aria-describedby={error ? `${id}-error` : field.helper ? `${id}-helper` : undefined}
                  />
                  {error && <small id={`${id}-error`} className="cu-error">{error}</small>}
                  {!error && field.helper && <small id={`${id}-helper`} className="cu-helper">{field.helper}</small>}
                </div>
              )
            })}
          </div>

          <footer className="cu-form-actions">
            <button type="button" className="sf-btn outline" onClick={goToList} disabled={saving}>Cancel</button>
            <button type="submit" className="sf-btn primary" disabled={saving}>
              {saving ? <Loader2 size={15} className="cu-spin" /> : null}
              {saving ? 'Saving…' : 'Save'}
            </button>
          </footer>
        </form>
      </section>
    )
  }

  if (view === 'detail') {
    return (
      <section className="sf-record cu-page">
        <header className="sf-record-head acc-record-head">
          <div className="sf-title-row">
            <div className="sf-title">
              <span className="sf-icon customer" aria-hidden="true"><Users size={20} /></span>
              <div>
                <span className="sf-kicker">Customer</span>
                <h1>{selected ? `${selected.firstName} ${selected.lastName}` : 'Customer'}</h1>
              </div>
            </div>

            <div className="sf-actions">
              <button className="sf-pill-btn" onClick={goToList}>Back to customers</button>
            </div>
          </div>

          {selected && (
            <dl className="sf-highlights">
              <div><dt>Client Type</dt><dd>{TYPE_LABEL[selected.clientType] || selected.clientType}</dd></div>
              <div><dt>Email</dt><dd>{selected.email}</dd></div>
              <div><dt>Phone</dt><dd>{selected.phoneNumber || '—'}</dd></div>
              <div><dt>Accounts</dt><dd>{accounts.length}</dd></div>
            </dl>
          )}
        </header>

        {banner.text && (
          <div className={`cu-banner ${banner.tone}`} role={banner.tone === 'danger' ? 'alert' : 'status'}>
            {banner.tone === 'danger' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} />}
            <span>{banner.text}</span>
          </div>
        )}

        {!selected && !banner.text && <p className="sf-muted centered">Loading customer…</p>}

        {selected && (
          <div className="sf-body single">
            <div className="sf-card sf-main">
              <div className="sf-tabs" role="tablist">
                <button
                  role="tab"
                  aria-selected={detailTab === 'details'}
                  className={detailTab === 'details' ? 'active' : ''}
                  onClick={() => setDetailTab('details')}
                >
                  Details
                </button>
                <button
                  role="tab"
                  aria-selected={detailTab === 'accounts'}
                  className={detailTab === 'accounts' ? 'active' : ''}
                  onClick={() => setDetailTab('accounts')}
                >
                  Accounts ({accounts.length})
                </button>
              </div>

              <div className="sf-tabpanel" role="tabpanel">
                {detailTab === 'details' ? (
                  <FieldGrid
                    fields={[
                      ['Customer ID', selected.id],
                      ['First name', selected.firstName],
                      ['Last name', selected.lastName],
                      ['Email address', selected.email],
                      ['Phone number', selected.phoneNumber],
                      ['Client type', TYPE_LABEL[selected.clientType] || selected.clientType],
                      ['Created', formatDate(selected.createdAt)],
                      ['Last updated', formatDate(selected.updatedAt)],
                    ]}
                  />
                ) : (
                  <>
                    <form className="acc-open" onSubmit={handleOpenAccount} aria-busy={openingAccount}>
                      <div>
                        <label htmlFor="new-account-type">Account type</label>
                        <select
                          id="new-account-type"
                          value={newAccountType}
                          disabled={openingAccount}
                          onChange={(e) => setNewAccountType(e.target.value)}
                        >
                          {ACCOUNT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                      </div>
                      <button type="submit" className="sf-btn primary" disabled={openingAccount}>
                        {openingAccount ? <Loader2 size={15} className="cu-spin" /> : <Plus size={15} />}
                        {openingAccount ? 'Opening…' : 'Open account'}
                      </button>
                    </form>

                    {accountsError && (
                      <div className="cu-banner danger" role="alert">
                        <AlertCircle size={18} /> <span>{accountsError}</span>
                      </div>
                    )}

                    {accounts.length === 0 ? (
                      <p className="sf-muted centered">This customer has no accounts yet.</p>
                    ) : (
                      <div className="sf-related-list">
                        {accounts.map((account) => (
                          <button key={account.id} className="sf-related-row" onClick={() => openAccount(account.id)}>
                            <span className="acc-row-icon" aria-hidden="true"><Wallet size={16} /></span>
                            <span className="acc-row-body">
                              <span className="acc-row-title">{account.accountNumber}</span>
                              <span className="acc-row-meta">
                                {ACCOUNT_TYPE_LABEL[account.accountType] || account.accountType} · <StatusBadge status={account.status} />
                              </span>
                            </span>
                            <span className="acc-row-balance">{formatMoney(account.balance, account.currency)}</span>
                            <ChevronRight size={15} aria-hidden="true" />
                          </button>
                        ))}
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </section>
    )
  }

  // list view
  return (
    <section className="cu-page">
      <CustomersHeading
        kicker="Customers"
        title="All Customers"
        meta={loading ? 'Loading…' : `${filtered.length} item${filtered.length === 1 ? '' : 's'} • Newest first`}
        actions={
          <>
            <button className="sf-pill-btn" onClick={loadCustomers} aria-label="Refresh list">
              <RefreshCw size={14} /> Refresh
            </button>
            <button className="sf-btn primary" onClick={() => setView('pick')}>
              <Plus size={15} /> New
            </button>
          </>
        }
      />

      {banner.text && (
        <div className={`cu-banner ${banner.tone}`} role="alert">
          <AlertCircle size={18} /> <span>{banner.text}</span>
        </div>
      )}

      <div className="cu-toolbar">
        <label className="sf-search">
          <Search size={15} aria-hidden="true" />
          <input
            type="search"
            placeholder="Search this list..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search customers"
          />
        </label>
      </div>

      {loadError && (
        <div className="cu-banner danger" role="alert"><AlertCircle size={18} /> <span>{loadError}</span></div>
      )}

      <div className="sf-card sf-table-wrap">
        <table className="sf-table">
          <thead>
            <tr>
              <th className="narrow">#</th>
              <th>Customer Name</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Client Type</th>
              <th>Created</th>
              <th className="narrow" aria-label="Open" />
            </tr>
          </thead>
          <tbody>
            {loading && customers.length === 0 && (
              <tr><td colSpan={7} className="sf-empty">Loading customers…</td></tr>
            )}
            {!loading && !loadError && customers.length === 0 && (
              <tr><td colSpan={7} className="sf-empty">No customers yet. Select <strong>New</strong> to register one.</td></tr>
            )}
            {filtered.map((c, index) => (
              <tr key={c.id} onClick={() => openDetail(c.id)}>
                <td className="narrow">{index + 1}</td>
                <td>
                  <button
                    className="sf-link-button"
                    onClick={(e) => { e.stopPropagation(); openDetail(c.id) }}
                  >
                    {c.firstName} {c.lastName}
                  </button>
                </td>
                <td>{c.email}</td>
                <td>{c.phoneNumber || '—'}</td>
                <td>{TYPE_LABEL[c.clientType] || c.clientType}</td>
                <td>{formatDate(c.createdAt)}</td>
                <td className="narrow"><ChevronRight size={15} aria-hidden="true" /></td>
              </tr>
            ))}
            {!loading && customers.length > 0 && filtered.length === 0 && (
              <tr><td colSpan={7} className="sf-empty">No customers match “{query}”.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function CustomersHeading({ kicker, title, meta, actions }) {
  return (
    <header className="cu-heading">
      <div className="cu-heading-title">
        <span className="cu-heading-icon" aria-hidden="true"><Users size={20} /></span>
        <div>
          <span className="sf-kicker">{kicker}</span>
          <h1>{title}</h1>
          {meta && <span className="sf-muted">{meta}</span>}
        </div>
      </div>
      <div className="cu-heading-actions">{actions}</div>
    </header>
  )
}
