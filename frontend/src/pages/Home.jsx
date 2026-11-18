import { useEffect, useMemo, useState } from 'react'
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { ArrowLeftRight, Briefcase, ChevronRight, FileText, ShieldAlert, UserPlus, Wallet } from 'lucide-react'
import { getCustomers } from '../services/customerService'
import { getRecentTransactions } from '../services/transactionService'
import { getServiceHealth } from '../services/healthService'
import './Home.css'

const TYPE_COLOR = { CREDIT: '#0176d3', DEBIT: '#e83e8c' }
const STATUS_COLOR = { PENDING: '#dd7a01', COMPLETED: '#2e844a', FAILED: '#ba0517' }
const STATUS_LABEL = { PENDING: 'Pending', COMPLETED: 'Completed', FAILED: 'Failed' }

const errorMessage = (err) => {
  if (!err.response) return 'The API gateway cannot be reached.'
  return err.response.data?.message || 'The request failed.'
}

const formatMoney = (value, currency = 'ZAR') =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency }).format(Number(value ?? 0))

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

// Each data source loads on its own. A failure is shown where it happened; no
// other data is put in its place.
function useLoad(loader) {
  const [state, setState] = useState({ status: 'loading', data: null, error: '' })

  const load = () => {
    setState({ status: 'loading', data: null, error: '' })
    loader()
      .then((data) => setState({ status: 'ready', data, error: '' }))
      .catch((err) => setState({ status: 'error', data: null, error: errorMessage(err) }))
  }

  useEffect(() => {
    let active = true
    loader()
      .then((data) => { if (active) setState({ status: 'ready', data, error: '' }) })
      .catch((err) => { if (active) setState({ status: 'error', data: null, error: errorMessage(err) }) })
    return () => { active = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return { ...state, reload: load }
}

function count(items, key) {
  return items.reduce((acc, item) => {
    acc[item[key]] = (acc[item[key]] || 0) + 1
    return acc
  }, {})
}

// Transactions per day for the last seven days, from their real creation dates.
function transactionsByDay(transactions) {
  const days = []
  for (let i = 6; i >= 0; i -= 1) {
    const day = new Date()
    day.setHours(0, 0, 0, 0)
    day.setDate(day.getDate() - i)
    days.push({ key: day.toISOString().slice(0, 10), label: day.toLocaleDateString('en-ZA', { weekday: 'short' }), count: 0 })
  }
  transactions.forEach((t) => {
    const key = new Date(t.createdAt).toISOString().slice(0, 10)
    const day = days.find((d) => d.key === key)
    if (day) day.count += 1
  })
  return days
}

function Unavailable({ message }) {
  return <p className="home-muted home-unavailable">{message}</p>
}

export default function Home({ onNavigate }) {
  const customers = useLoad(getCustomers)
  const transactions = useLoad(getRecentTransactions)
  const health = useLoad(getServiceHealth)

  const txList = useMemo(() => transactions.data || [], [transactions.data])

  const byType = useMemo(
    () => Object.entries(count(txList, 'type')).map(([name, value]) => ({ name, value })),
    [txList],
  )
  const byStatus = useMemo(
    () => Object.entries(count(txList, 'status')).map(([name, value]) => ({ name, value })),
    [txList],
  )
  const byDay = useMemo(() => transactionsByDay(txList), [txList])

  const servicesUp = health.status === 'ready'
    ? health.data.filter((s) => s.reachable && s.status === 'UP').length
    : null

  const reports = [
    {
      name: 'Recent transactions',
      category: 'Transactions',
      description: 'Credits and debits recorded against accounts, newest first.',
      icon: ArrowLeftRight,
      color: '#0176d3',
      target: null,
    },
    {
      name: 'Customer registrations',
      category: 'Customers',
      description: 'Customers registered on the platform.',
      icon: UserPlus,
      color: '#5867e8',
      target: 'Customers',
    },
    {
      name: 'Open case ageing',
      category: 'Cases',
      description: 'Open cases grouped by priority and days since they were opened.',
      icon: Briefcase,
      color: '#e83e8c',
      target: 'Cases',
    },
    {
      name: 'Fraud alerts linked to cases',
      category: 'Fraud',
      description: 'Fraud monitoring alerts and the cases they belong to.',
      icon: ShieldAlert,
      color: '#2e844a',
      target: 'Fraud',
    },
    {
      name: 'Statement run log',
      category: 'Statements',
      description: 'Statement generation history for monitored accounts.',
      icon: FileText,
      color: '#706e6b',
      target: null,
    },
  ]

  return (
    <div className="home">
      <header className="home-head">
        <div>
          <span className="home-kicker">Home</span>
          <h1>Operations overview</h1>
          <p className="home-muted">Customers, transactions and service status, from the live platform.</p>
        </div>
        <span className="home-note">Live data</span>
      </header>

      <section className="home-kpis" aria-label="Key figures">
        <Kpi
          icon={UserPlus}
          label="Customers"
          value={customers.status === 'ready' ? customers.data.length : null}
          error={customers.status === 'error' ? customers.error : null}
          onClick={() => onNavigate('Customers')}
        />
        <Kpi
          icon={ArrowLeftRight}
          label="Recent transactions"
          value={transactions.status === 'ready' ? txList.length : null}
          error={transactions.status === 'error' ? transactions.error : null}
        />
        <Kpi
          icon={Wallet}
          label="Services running"
          value={servicesUp === null ? null : `${servicesUp} of ${health.data.length}`}
          error={health.status === 'error' ? health.error : null}
        />
        <Kpi icon={Briefcase} label="Open cases" value={null} error="No case service is available yet." />
      </section>

      <section className="home-charts">
        <Panel title="Transactions by type" onRetry={transactions.reload} state={transactions}>
          {txList.length === 0 ? (
            <Unavailable message="No transactions recorded yet." />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={byType} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e5e5" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="value" name="Transactions" radius={[4, 4, 0, 0]}>
                  {byType.map((entry) => <Cell key={entry.name} fill={TYPE_COLOR[entry.name] || '#9a9895'} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </Panel>

        <Panel title="Transactions by status" onRetry={transactions.reload} state={transactions}>
          {txList.length === 0 ? (
            <Unavailable message="No transactions recorded yet." />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={byStatus} dataKey="value" nameKey="name" innerRadius={55} outerRadius={85} paddingAngle={2}>
                  {byStatus.map((entry) => <Cell key={entry.name} fill={STATUS_COLOR[entry.name] || '#9a9895'} />)}
                </Pie>
                <Tooltip formatter={(value, name) => [value, STATUS_LABEL[name] || name]} />
                <Legend formatter={(name) => STATUS_LABEL[name] || name} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Panel>

        <Panel title="Transactions per day (last 7 days)" wide onRetry={transactions.reload} state={transactions}>
          {txList.length === 0 ? (
            <Unavailable message="No transactions recorded yet." />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={byDay} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e5e5" />
                <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Line type="monotone" dataKey="count" name="Transactions" stroke="#0176d3" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </Panel>
      </section>

      <section className="home-lower">
        <Panel title="Recent transactions" onRetry={transactions.reload} state={transactions}>
          {txList.length === 0 ? (
            <Unavailable message="No transactions recorded yet." />
          ) : (
            <ul className="home-list">
              {txList.slice(0, 8).map((t) => (
                <li key={t.id}>
                  <button type="button">
                    <span className="home-dot" style={{ background: TYPE_COLOR[t.type] || '#9a9895' }} aria-hidden="true" />
                    <div className="home-list-main">
                      <strong>{t.transactionReference}</strong>
                      <span className="home-muted">{t.type} · {STATUS_LABEL[t.status] || t.status} · {formatDate(t.createdAt)}</span>
                    </div>
                    <span className="home-priority">{formatMoney(t.amount, t.currency)}</span>
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title="Service health" onRetry={health.reload} state={health}>
          <ul className="home-list">
            {health.data?.map((s) => (
              <li key={s.name}>
                <div className="home-list-main">
                  <strong>{s.name}</strong>
                  <span className="home-muted">{s.url}</span>
                </div>
                <span className="home-priority">{s.reachable ? s.status : 'Unreachable'}</span>
              </li>
            ))}
          </ul>
        </Panel>
      </section>

      <section className="home-reports" aria-labelledby="home-reports-title">
        <div className="home-section-head">
          <h2 id="home-reports-title">Reports</h2>
          <span className="home-muted">{reports.length} available</span>
        </div>

        <ul className="report-grid">
          {reports.map((r) => {
            const Icon = r.icon
            const ready = Boolean(r.target)
            return (
              <li key={r.name} className="report-tile">
                <div className="report-top">
                  <span className="report-icon" style={{ background: r.color }} aria-hidden="true">
                    <Icon size={18} />
                  </span>
                  <span className="report-category">{r.category}</span>
                </div>

                <h3>{r.name}</h3>
                <p>{r.description}</p>

                <div className="report-foot">
                  {ready ? (
                    <button className="report-action" onClick={() => onNavigate(r.target)}>
                      Open report <ChevronRight size={14} />
                    </button>
                  ) : (
                    <span className="report-soon">Not connected yet</span>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}

function Kpi({ icon: Icon, label, value, error, onClick }) {
  const body = (
    <>
      <span className="home-kpi-icon" aria-hidden="true"><Icon size={18} /></span>
      <div>
        <span className="home-muted">{label}</span>
        {error ? (
          <small className="home-kpi-error">{error}</small>
        ) : (
          <strong>{value ?? '—'}</strong>
        )}
      </div>
    </>
  )
  return onClick ? (
    <button className="home-kpi home-kpi-link" onClick={onClick}>{body}</button>
  ) : (
    <div className="home-kpi">{body}</div>
  )
}

function Panel({ title, wide, state, onRetry, children }) {
  return (
    <section className={`home-card${wide ? ' wide' : ''}`}>
      <div className="home-card-head">
        <h2>{title}</h2>
        {state && state.status === 'error' && (
          <button className="home-btn" onClick={onRetry}>Retry</button>
        )}
      </div>
      {state?.status === 'loading' && <p className="home-muted">Loading…</p>}
      {state?.status === 'error' && <p className="home-kpi-error" role="alert">{state.error}</p>}
      {(!state || state.status === 'ready') && children}
    </section>
  )
}
