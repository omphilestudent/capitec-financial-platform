import { useMemo, useState } from 'react'
import {
  Briefcase, Building2, Check, ChevronDown, ChevronRight, Download, FileText,
  Pencil, Plus, Search, ShieldAlert, TrendingUp, UserRound, Wallet,
} from 'lucide-react'
import { cases, CASE_STATUS_PATH, getCase } from '../data/mockCases'
import './Cases.css'

const TODAY = '2026-11-17'

const money = (value) => {
  const sign = value < 0 ? '-' : ''
  const formatted = Math.abs(value).toLocaleString('en-ZA', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  return `${sign}R ${formatted}`
}

const initials = (name) => name.split(' ').slice(0, 2).map((p) => p[0]).join('').toUpperCase()

/* ------------------------------------------------------------------
   Shared Lightning pieces
------------------------------------------------------------------ */

function Priority({ value }) {
  const tone = value.toLowerCase()
  return (
    <span className={`sf-priority ${tone}`}>
      <i aria-hidden="true" />
      {value}
    </span>
  )
}

function Section({ title, defaultOpen = true, children }) {
  const [open, setOpen] = useState(defaultOpen)
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

function LongText({ text }) {
  const [expanded, setExpanded] = useState(false)
  const lines = text.split('\n')
  const clipped = lines.length > 6 && !expanded
  return (
    <div className="sf-long">
      <pre>{clipped ? lines.slice(0, 6).join('\n') : text}</pre>
      {lines.length > 6 && (
        <button className="sf-text-link" onClick={() => setExpanded((e) => !e)}>
          {expanded ? 'Show less' : 'Show more'}
        </button>
      )}
    </div>
  )
}

function Field({ label, value, kind }) {
  let content
  if (kind === 'check') {
    content = (
      <span className={`sf-checkbox${value ? ' on' : ''}`} role="img" aria-label={`${label}: ${value ? 'checked' : 'not checked'}`}>
        {value && <Check size={11} />}
      </span>
    )
  } else if (kind === 'link' && value) {
    content = <span className="sf-link">{value}</span>
  } else if (kind === 'long') {
    content = value ? <LongText text={value} /> : <span className="sf-blank" />
  } else {
    content = value || <span className="sf-blank" />
  }

  return (
    <div className={`sf-field${kind === 'long' ? ' full' : ''}`}>
      <div className="sf-field-label">{label}</div>
      <div className="sf-field-value">{content}</div>
      <Pencil size={13} className="sf-edit" aria-hidden="true" />
    </div>
  )
}

function FieldGrid({ fields }) {
  return (
    <div className="sf-fields">
      {fields.map(([label, value, kind]) => (
        <Field key={label} label={label} value={value} kind={kind} />
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------
   Case list (Cases object home)
------------------------------------------------------------------ */

export function CaseList({ onOpen }) {
  const [query, setQuery] = useState('')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return cases
    return cases.filter((c) =>
      [c.id, c.title, c.client, c.owner, c.status, c.priority].some((v) => v.toLowerCase().includes(q)),
    )
  }, [query])

  return (
    <section className="sf-list">
      <div className="sf-list-head">
        <div>
          <span className="sf-kicker">Cases</span>
          <h1>All Open Cases</h1>
          <span className="sf-muted">{filtered.length} item{filtered.length === 1 ? '' : 's'} • Sorted by Case Number</span>
        </div>
        <label className="sf-search">
          <Search size={15} aria-hidden="true" />
          <input
            type="search"
            placeholder="Search this list..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search cases"
          />
        </label>
      </div>

      <div className="sf-card sf-table-wrap">
        <table className="sf-table">
          <thead>
            <tr>
              <th className="narrow"><input type="checkbox" aria-label="Select all cases" /></th>
              <th>Case Number</th>
              <th>Subject</th>
              <th>Client Name</th>
              <th>Priority</th>
              <th>Status</th>
              <th>Case Owner</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((c) => (
              <tr key={c.id}>
                <td className="narrow"><input type="checkbox" aria-label={`Select case ${c.id}`} /></td>
                <td><button className="sf-link-button" onClick={() => onOpen(c.id)}>{c.id}</button></td>
                <td>{c.title}</td>
                <td>{c.client}</td>
                <td><Priority value={c.priority} /></td>
                <td>{c.status}</td>
                <td>{c.owner}</td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan={7} className="sf-empty">No cases match “{query}”.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------
   Case record
------------------------------------------------------------------ */

const CASE_TABS = [
  { key: 'details', label: 'Details' },
  { key: 'transactions', label: 'Transaction History' },
  { key: 'fraud', label: 'Fraud Monitoring' },
  { key: 'statements', label: 'Statements' },
]

export function CaseRecord({ caseItem, onOpenFraud }) {
  const [tab, setTab] = useState('details')
  const closed = caseItem.status === 'Closed'

  return (
    <div className="sf-record">
      <header className="sf-record-head">
        <div className="sf-title-row">
          <div className="sf-title">
            <span className="sf-icon case" aria-hidden="true"><Briefcase size={20} /></span>
            <div>
              <span className="sf-kicker">Case</span>
              <h1>{caseItem.title}</h1>
            </div>
          </div>

          <div className="sf-actions">
            <button className="sf-pill-btn"><Plus size={14} /> Follow</button>
            <button className="sf-pill-btn">Edit</button>
            <button className="sf-pill-btn">Change Owner</button>
            <button className="sf-pill-btn">Accept</button>
            <button className="sf-pill-btn">View Case Hierarchy</button>
            <button className="sf-pill-btn">Send Email</button>
          </div>
        </div>

        <dl className="sf-highlights">
          <div><dt>Priority</dt><dd>{caseItem.priority}</dd></div>
          <div><dt>Status</dt><dd>{caseItem.status}</dd></div>
          <div><dt>Case Number</dt><dd>{caseItem.id}</dd></div>
        </dl>

        <div className="sf-path-row">
          <ol className="sf-path" aria-label="Case status">
            {CASE_STATUS_PATH.map((step) => {
              const current = step === caseItem.status
              return (
                <li
                  key={step}
                  className={current ? (closed ? 'current closed' : 'current') : ''}
                  aria-current={current ? 'step' : undefined}
                >
                  {step}
                </li>
              )
            })}
          </ol>
          <button className="sf-btn primary sf-path-complete" disabled={closed}>
            <Check size={14} /> Mark Status as Complete
          </button>
        </div>
      </header>

      <div className="sf-body">
        <div className="sf-card sf-main">
          <div className="sf-tabs" role="tablist">
            {CASE_TABS.map((t) => (
              <button
                key={t.key}
                role="tab"
                aria-selected={tab === t.key}
                className={tab === t.key ? 'active' : ''}
                onClick={() => setTab(t.key)}
              >
                {t.label}
              </button>
            ))}
          </div>

          <div className="sf-tabpanel" role="tabpanel">
            {tab === 'details' && <CaseDetails caseItem={caseItem} onOpenFraud={onOpenFraud} />}
            {tab === 'transactions' && <TransactionHistory caseItem={caseItem} />}
            {tab === 'fraud' && <FraudMonitoring caseItem={caseItem} onOpenFraud={onOpenFraud} />}
            {tab === 'statements' && <Statements caseItem={caseItem} />}
          </div>
        </div>

        <aside className="sf-side">
          {caseItem.sidebar === 'risk' ? <ClientHealth caseItem={caseItem} /> : <SlaCard caseItem={caseItem} />}
          {caseItem.disputes && <DisputesCard disputes={caseItem.disputes} />}
          <div className="sf-card sf-side-card">
            <h2>Case Comments ({0})</h2>
            <p className="sf-muted">No comments yet.</p>
          </div>
          <div className="sf-card sf-side-card">
            <h2>Related Cases (0)</h2>
          </div>
          <FeedCard items={caseItem.feed} />
        </aside>
      </div>
    </div>
  )
}

function CaseDetails({ caseItem, onOpenFraud }) {
  return (
    <>
      {caseItem.sections.map((s) => (
        <Section key={s.title} title={s.title} defaultOpen>
          <FieldGrid fields={s.fields} />
        </Section>
      ))}

      {caseItem.frauds.length > 0 && (
        <Section title={`Fraud (${caseItem.frauds.length})`} defaultOpen>
          <RelatedFraudTable frauds={caseItem.frauds} onOpenFraud={onOpenFraud} />
        </Section>
      )}
    </>
  )
}

function ClientHealth({ caseItem }) {
  return (
    <div className="sf-card sf-side-card">
      <h2><UserRound size={16} /> Client Profile</h2>
      <div className="sf-flag-row">
        <div>
          <span className="sf-flag-title">KYC</span>
          <span className="sf-muted">Last review</span>
        </div>
        <strong>Last KYC: {caseItem.lastKyc}</strong>
      </div>
      <div className="sf-flag-row">
        <div>
          <span className="sf-flag-title">Risk Grading</span>
          <span className="sf-muted">Risk Grading flag</span>
        </div>
        <strong>{caseItem.riskGrading}</strong>
      </div>
    </div>
  )
}

function SlaCard({ caseItem }) {
  const closed = caseItem.status === 'Closed'
  return (
    <div className="sf-card sf-sla">
      <div className="sf-sla-top">
        <span className="sf-kicker upper">Service Level</span>
        <span className={`sf-sla-badge${closed ? ' closed' : ''}`}>
          <i aria-hidden="true" /> {closed ? 'Case Closed' : 'SLA Active'}
        </span>
      </div>
      <h2>5 Day SLA</h2>
      <div className="sf-sla-empty">
        <strong>SLA Not Started</strong>
        <span className="sf-muted">This case has not reached the first stage yet.</span>
      </div>
    </div>
  )
}

function DisputesCard({ disputes }) {
  return (
    <div className="sf-card sf-side-card">
      <h2><Wallet size={16} className="sf-warn" /> Disputes ({disputes.length})</h2>
      <ul className="sf-disputes">
        {disputes.map((d) => (
          <li key={d.id}>
            <span className="sf-link">{d.id}</span>
            <dl>
              <div><dt>Trace Number</dt><dd>{d.trace}</dd></div>
              <div><dt>Disputed Amount</dt><dd>{d.amount}</dd></div>
              <div><dt>Transaction Date</dt><dd>{d.date}</dd></div>
            </dl>
          </li>
        ))}
      </ul>
    </div>
  )
}

function FeedCard({ items }) {
  const [query, setQuery] = useState('')
  const visible = items.filter((i) =>
    [i.who, i.text, i.detail || ''].some((v) => v.toLowerCase().includes(query.trim().toLowerCase())),
  )

  return (
    <div className="sf-card sf-feed">
      <div className="sf-feed-tabs">
        <button className="active">Feed</button>
      </div>
      <div className="sf-post">
        <span className="sf-post-tab">Post</span>
        <div className="sf-post-box">Share an update...</div>
      </div>
      <div className="sf-feed-filter">
        <span>Most Recent Activity <ChevronDown size={13} /></span>
        <label className="sf-search small">
          <Search size={13} aria-hidden="true" />
          <input
            type="search"
            placeholder="Search this feed..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search this feed"
          />
        </label>
      </div>
      <ul className="sf-feed-items">
        {visible.map((item) => (
          <li key={`${item.who}-${item.when}-${item.text}`}>
            <span className="sf-avatar">{initials(item.who)}</span>
            <div>
              <strong className="sf-link">{item.who}</strong>
              <span className="sf-muted sf-when">{item.when}</span>
              <div className="sf-feed-event"><TrendingUp size={12} /> {item.text}</div>
              {item.detail && <div className="sf-muted">{item.detail}</div>}
            </div>
          </li>
        ))}
        {visible.length === 0 && <li className="sf-muted">No activity matches “{query}”.</li>}
      </ul>
    </div>
  )
}

function RelatedFraudTable({ frauds, onOpenFraud }) {
  return (
    <div className="sf-table-wrap inset">
      <table className="sf-table">
        <thead>
          <tr>
            <th>Fraud Name</th>
            <th>Rule Name</th>
            <th className="num">Amount</th>
            <th>Priority</th>
          </tr>
        </thead>
        <tbody>
          {frauds.map((f) => (
            <tr key={f.id}>
              <td><button className="sf-link-button" onClick={() => onOpenFraud(f.id)}>{f.id}</button></td>
              <td className="clip">{f.rule}</td>
              <td className="num debit">{money(f.amount)}</td>
              <td><Priority value={f.priority} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FraudMonitoring({ caseItem, onOpenFraud }) {
  if (caseItem.frauds.length === 0) {
    return <p className="sf-muted centered">No fraud monitoring records are linked to this case.</p>
  }
  return (
    <>
      <div className="sf-tab-heading">
        <ShieldAlert size={16} /> Fraud monitoring ({caseItem.frauds.length})
      </div>
      <RelatedFraudTable frauds={caseItem.frauds} onOpenFraud={onOpenFraud} />
    </>
  )
}

function TransactionHistory({ caseItem }) {
  const [range, setRange] = useState('all')
  const [from, setFrom] = useState('2024-10-01')
  const [to, setTo] = useState(TODAY)
  const [money2, setMoney2] = useState('BOTH')
  const [classification, setClassification] = useState('ALL')
  const [loaded, setLoaded] = useState(false)

  const applyPreset = (days, key) => {
    const end = new Date(`${TODAY}T00:00:00`)
    const start = new Date(end)
    start.setDate(end.getDate() - days)
    setRange(key)
    setFrom(start.toISOString().slice(0, 10))
    setTo(TODAY)
  }

  const results = useMemo(() => caseItem.transactions.filter((t) => {
    if (t.date < from || t.date > to) return false
    if (money2 !== 'BOTH' && t.money !== money2) return false
    if (classification !== 'ALL' && t.classification !== classification) return false
    return true
  }), [caseItem, from, to, money2, classification])

  const classifications = [...new Set(caseItem.transactions.map((t) => t.classification))]

  return (
    <div className="txn">
      <div className="txn-subtab">Transaction History</div>

      <div className="txn-filters">
        <div className="txn-field">
          <span>Select</span>
          <div className="sf-segmented">
            <button className={range === '7' ? 'active' : ''} onClick={() => applyPreset(7, '7')}>Last 7 Days</button>
            <button className={range === '30' ? 'active' : ''} onClick={() => applyPreset(30, '30')}>Last 30 Days</button>
          </div>
        </div>
        <label className="txn-field">
          <span>From Date</span>
          <input type="date" value={from} onChange={(e) => { setRange('all'); setFrom(e.target.value) }} />
        </label>
        <label className="txn-field">
          <span>To Date</span>
          <input type="date" value={to} onChange={(e) => { setRange('all'); setTo(e.target.value) }} />
        </label>
        <label className="txn-field">
          <span>Account Number</span>
          <select value={caseItem.accountNumber} onChange={() => {}}>
            <option>{caseItem.accountNumber}</option>
          </select>
        </label>
        <label className="txn-field">
          <span>Money Type</span>
          <select value={money2} onChange={(e) => setMoney2(e.target.value)}>
            <option value="BOTH">BOTH</option>
            <option value="CREDIT">CREDIT</option>
            <option value="DEBIT">DEBIT</option>
          </select>
        </label>
        <label className="txn-field">
          <span>Classification Type</span>
          <select value={classification} onChange={(e) => setClassification(e.target.value)}>
            <option value="ALL">ALL</option>
            {classifications.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
      </div>

      <div className="txn-account-card">
        <div className="txn-account-title">
          <span className="sf-icon account" aria-hidden="true"><Building2 size={18} /></span>
          <div>
            <strong>Account</strong>
            <span>Transaction History</span>
          </div>
        </div>
        <div className="txn-account-actions">
          <button className="sf-btn primary" onClick={() => setLoaded(true)}>Get Transactions</button>
          <button className="sf-btn outline" disabled title="Dispute workflow not connected yet">Dispute Transaction</button>
        </div>
      </div>

      {!loaded ? (
        <p className="sf-muted centered">No transactions to display. Click <strong>Get Transactions</strong> to load results.</p>
      ) : results.length === 0 ? (
        <p className="sf-muted centered">No transactions match these filters.</p>
      ) : (
        <div className="sf-table-wrap inset">
          <table className="sf-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Classification</th>
                <th className="num">Amount</th>
                <th className="num">Balance</th>
              </tr>
            </thead>
            <tbody>
              {results.map((t) => (
                <tr key={t.id}>
                  <td>{t.date.replaceAll('-', '/')} <small>{t.time}</small></td>
                  <td><strong>{t.description}</strong><small>{t.id}</small></td>
                  <td>{t.classification}</td>
                  <td className={`num ${t.amount < 0 ? 'debit' : 'credit'}`}>{money(t.amount)}</td>
                  <td className="num">{money(t.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function Statements({ caseItem }) {
  if (caseItem.statements.length === 0) {
    return <p className="sf-muted centered">No statements available for this account.</p>
  }
  return (
    <div className="sf-table-wrap inset">
      <table className="sf-table">
        <thead>
          <tr>
            <th>Statement Period</th>
            <th>Account</th>
            <th>Generated</th>
            <th className="num">Action</th>
          </tr>
        </thead>
        <tbody>
          {caseItem.statements.map((s) => (
            <tr key={s.period}>
              <td><span className="sf-statement"><FileText size={15} /> {s.period}</span></td>
              <td>{s.account}</td>
              <td>{s.generated}</td>
              <td className="num">
                <button className="sf-btn outline small" disabled title="Statement download not connected yet">
                  <Download size={14} /> Download
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ------------------------------------------------------------------
   Fraud record (FRA-…), opened from a case's Fraud Monitoring tab
------------------------------------------------------------------ */

export function FraudRecord({ fraud, onOpenCase }) {
  const [tab, setTab] = useState('details')
  const parent = getCase(fraud.caseId)

  const fields = [
    ['Fraud Name', fraud.id],
    ['Rule Name', fraud.rule],
    ['Amount', money(fraud.amount)],
    ['Beneficiary Bank', fraud.beneficiaryBank],
    ['Rule Description', fraud.ruleDescription],
    ['From Account Number', fraud.fromAccount],
    ['Post Datetime Core', fraud.postDate],
    ['Case Number', fraud.caseNumber, 'link'],
    ['Priority', fraud.priority],
    ['Channel', ''],
    ['Destination Account Number', fraud.destinationAccount],
    ['Action Type', ''],
    ['Account Number', ''],
    ['Reason Code', ''],
    ['Reason', ''],
    ['Requested Amount', ''],
    ['Processed Amount', ''],
    ['Journal Number', fraud.journalNumber],
    ['Transaction Date', fraud.transactionDate],
    ['Consultant Identifier', ''],
    ['Account Product Type', ''],
    ['Service Status Code', ''],
    ['Success', false, 'check'],
    ['Error Message', ''],
    ['Transaction Group ID', ''],
    ['Financial Account', ''],
    ['Method', ''],
  ]

  return (
    <div className="sf-record">
      <header className="sf-record-head">
        <div className="sf-title-row">
          <div className="sf-title">
            <span className="sf-icon fraud" aria-hidden="true"><ShieldAlert size={20} /></span>
            <div>
              <span className="sf-kicker">Fraud</span>
              <h1>{fraud.id}</h1>
            </div>
          </div>
          <div className="sf-actions">
            <button className="sf-pill-btn">New Case</button>
            <button className="sf-pill-btn">Edit</button>
            <button className="sf-pill-btn">Delete</button>
          </div>
        </div>
      </header>

      <div className="sf-body">
        <div className="sf-card sf-main">
          <div className="sf-tabs" role="tablist">
            <button role="tab" aria-selected={tab === 'related'} className={tab === 'related' ? 'active' : ''} onClick={() => setTab('related')}>Related</button>
            <button role="tab" aria-selected={tab === 'details'} className={tab === 'details' ? 'active' : ''} onClick={() => setTab('details')}>Details</button>
          </div>

          <div className="sf-tabpanel">
            {tab === 'related' ? (
              <div className="sf-related-list">
                <div className="sf-tab-heading">Parent case</div>
                {parent && (
                  <button className="sf-related-row" onClick={() => onOpenCase(parent.id)}>
                    <span className="sf-icon case small" aria-hidden="true"><Briefcase size={14} /></span>
                    <div>
                      <strong>{parent.id} | Case</strong>
                      <span className="sf-muted">{parent.title} • {parent.status}</span>
                    </div>
                  </button>
                )}
              </div>
            ) : (
              <div className="fraud-details">
                <div className="sf-fields single">
                  {fields.map(([label, value, kind]) => (
                    <Field key={label} label={label} value={value} kind={kind} />
                  ))}
                  <Field label="Created By" value={fraud.createdBy} />
                  <Field label="Last Modified By" value={fraud.lastModifiedBy} />
                </div>
              </div>
            )}
          </div>
        </div>

        <aside className="sf-side">
          <div className="sf-card sf-side-card activity">
            <div className="sf-activity-tabs"><button className="active">Activity</button></div>
            <div className="sf-activity-links">
              <span className="sf-muted">Filters: All time • All activities • All types</span>
              <span className="sf-link-row"><button className="sf-text-link">Refresh</button> • <button className="sf-text-link">Expand All</button> • <button className="sf-text-link">View All</button></span>
            </div>
            <div className="sf-activity-empty">
              <strong>Upcoming &amp; Overdue</strong>
              <p className="sf-muted">No activities to show.</p>
              <p className="sf-muted">Get started by sending an email, scheduling a task, and more.</p>
            </div>
            <button className="sf-btn primary full">Show All Activities</button>
          </div>
        </aside>
      </div>
    </div>
  )
}
