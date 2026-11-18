import { useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, CheckCircle2, ChevronRight, Loader2, Plus, RefreshCw, Search, Users,
} from 'lucide-react'
import { createCustomer, getCustomerById, getCustomers } from '../services/customerService'
import './Customers.css'

const CLIENT_TYPES = [
  {
    value: 'REGISTERED_BUSINESS',
    label: 'Registered Business',
    description: 'A company or close corporation registered with CIPC.',
  },
  {
    value: 'GROUP',
    label: 'Group',
    description: 'Several related clients managed together.',
  },
  {
    value: 'INDIVIDUAL',
    label: 'Individual',
    description: 'A personal banking customer.',
  },
  {
    value: 'SOLE_PROPRIETOR',
    label: 'Sole Proprietor',
    description: 'An individual trading under their own name.',
  },
  {
    value: 'VENDOR',
    label: 'Vendor',
    description: 'A third party such as an attorney, appraiser or supplier.',
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

const formatDate = (value) => {
  if (!value) return ''
  return new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' })
}

const errorMessage = (err) => {
  if (!err.response) return 'Cannot reach the server. Check that the backend is running and try again.'
  return err.response.data?.message || 'Something went wrong. Please try again.'
}

export default function Customers() {
  const [view, setView] = useState('list') // list | pick | new | detail
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [query, setQuery] = useState('')

  const [clientType, setClientType] = useState('REGISTERED_BUSINESS')
  const [formData, setFormData] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const [selected, setSelected] = useState(null)
  const [banner, setBanner] = useState('')

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

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return customers
    return customers.filter((c) =>
      [c.firstName, c.lastName, c.email, c.phoneNumber || '', TYPE_LABEL[c.clientType] || '']
        .some((v) => v.toLowerCase().includes(q)),
    )
  }, [customers, query])

  const openDetail = async (id) => {
    setBanner('')
    setView('detail')
    setSelected(null)
    try {
      setSelected(await getCustomerById(id))
    } catch (err) {
      setBanner(errorMessage(err))
    }
  }

  const goToList = () => {
    setView('list')
    setBanner('')
    setFormError('')
    setFieldErrors({})
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
      setBanner('Customer created successfully.')
      setView('detail')
      loadCustomers()
    } catch (err) {
      setFormError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  /* ---------- Views ---------- */

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
      <section className="cu-page">
        <CustomersHeading
          kicker="Customer"
          title={selected ? `${selected.firstName} ${selected.lastName}` : 'Customer'}
          meta={selected ? TYPE_LABEL[selected.clientType] : ''}
          actions={<button className="sf-pill-btn" onClick={goToList}>Back to customers</button>}
        />

        {banner && (
          <div className={`cu-banner ${selected ? 'success' : 'danger'}`} role={selected ? 'status' : 'alert'}>
            {selected ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
            <span>{banner}</span>
          </div>
        )}

        {!selected && !banner && <p className="sf-muted centered">Loading customer…</p>}

        {selected && (
          <div className="sf-card cu-detail">
            <dl className="cu-detail-grid">
              <DetailItem label="Customer ID" value={selected.id} />
              <DetailItem label="First name" value={selected.firstName} />
              <DetailItem label="Last name" value={selected.lastName} />
              <DetailItem label="Email address" value={selected.email} />
              <DetailItem label="Phone number" value={selected.phoneNumber || '—'} />
              <DetailItem label="Client type" value={TYPE_LABEL[selected.clientType] || selected.clientType} />
              <DetailItem label="Created" value={formatDate(selected.createdAt)} />
              <DetailItem label="Last updated" value={formatDate(selected.updatedAt)} />
            </dl>
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

function DetailItem({ label, value }) {
  return (
    <div className="cu-detail-item">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}
