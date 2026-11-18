import { useMemo } from 'react'
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { Briefcase, ChevronRight, FileText, ShieldAlert, UserPlus, Wallet } from 'lucide-react'
import { cases, allFrauds } from '../data/mockCases'
import './Home.css'

const PRIORITY_RANK = { Critical: 0, High: 1, Medium: 2, Low: 3 }
const PRIORITY_COLOR = { Critical: '#ba0517', High: '#dd7a01', Medium: '#0176d3', Low: '#54698d' }
const STATUS_COLOR = '#0176d3'

// Monthly volumes are placeholder values for layout only, until the
// backend provides case history.
const MONTHLY_VOLUME = [
  { month: 'Aug', opened: 6, closed: 4 },
  { month: 'Sep', opened: 9, closed: 7 },
  { month: 'Oct', opened: 7, closed: 8 },
  { month: 'Nov', opened: 11, closed: 6 },
]

function count(items, key) {
  return items.reduce((acc, item) => {
    acc[item[key]] = (acc[item[key]] || 0) + 1
    return acc
  }, {})
}

export default function Home({ onOpenCase, onNavigate }) {
  const stats = useMemo(() => {
    const open = cases.filter((c) => c.status !== 'Closed')
    const disputesOpen = open.reduce((sum, c) => sum + (c.disputes?.length || 0), 0)
    return {
      openCases: open.length,
      critical: cases.filter((c) => c.priority === 'Critical' && c.status !== 'Closed').length,
      fraudAlerts: allFrauds.length,
      disputesOpen,
    }
  }, [])

  const byStatus = useMemo(
    () => Object.entries(count(cases, 'status')).map(([status, total]) => ({ status, total })),
    [],
  )

  const byPriority = useMemo(
    () => Object.entries(count(cases, 'priority')).map(([name, value]) => ({ name, value })),
    [],
  )

  const attention = useMemo(
    () => cases
      .filter((c) => c.status !== 'Closed')
      .sort((a, b) => (PRIORITY_RANK[a.priority] ?? 9) - (PRIORITY_RANK[b.priority] ?? 9)),
    [],
  )

  const reports = [
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
      target: 'Cases',
    },
    {
      name: 'Dispute register',
      category: 'Disputes',
      description: 'Disputes raised against open and closed cases.',
      icon: Wallet,
      color: '#0176d3',
      target: 'Cases',
    },
    {
      name: 'Customer registrations',
      category: 'Customers',
      description: 'New customers registered on the platform.',
      icon: UserPlus,
      color: '#5867e8',
      target: 'Customers',
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
          <p className="home-muted">Cases, fraud monitoring, disputes and customer onboarding at a glance.</p>
        </div>
        <span className="home-note">Demonstration data</span>
      </header>

      <section className="home-kpis" aria-label="Key figures">
        <Kpi icon={Briefcase} label="Open cases" value={stats.openCases} />
        <Kpi icon={ShieldAlert} label="Critical open cases" value={stats.critical} tone="danger" />
        <Kpi icon={FileText} label="Fraud alerts" value={stats.fraudAlerts} />
        <Kpi icon={Wallet} label="Open disputes" value={stats.disputesOpen} />
      </section>

      <section className="home-charts">
        <Panel title="Cases by status">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={byStatus} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e5e5" />
              <XAxis dataKey="status" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="total" name="Cases" fill={STATUS_COLOR} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Panel>

        <Panel title="Cases by priority">
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie data={byPriority} dataKey="value" nameKey="name" innerRadius={55} outerRadius={85} paddingAngle={2}>
                {byPriority.map((entry) => (
                  <Cell key={entry.name} fill={PRIORITY_COLOR[entry.name] || '#9a9895'} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </Panel>

        <Panel title="Cases opened vs closed" wide>
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={MONTHLY_VOLUME} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e5e5" />
              <XAxis dataKey="month" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="opened" stroke="#0176d3" strokeWidth={2} />
              <Line type="monotone" dataKey="closed" stroke="#2e844a" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </Panel>
      </section>

      <section className="home-lower">
        <div className="home-card">
          <div className="home-card-head">
            <h2>Needs attention</h2>
            <span className="home-muted">{attention.length} open</span>
          </div>
          <ul className="home-list">
            {attention.map((c) => (
              <li key={c.id}>
                <button onClick={() => onOpenCase(c.id)}>
                  <span className="home-dot" style={{ background: PRIORITY_COLOR[c.priority] || '#9a9895' }} aria-hidden="true" />
                  <div className="home-list-main">
                    <strong>{c.id} · {c.title}</strong>
                    <span className="home-muted">{c.owner} · {c.status}</span>
                  </div>
                  <span className="home-priority">{c.priority}</span>
                  <ChevronRight size={16} aria-hidden="true" />
                </button>
              </li>
            ))}
          </ul>
        </div>

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

function Kpi({ icon: Icon, label, value, tone }) {
  return (
    <div className={`home-kpi${tone === 'danger' ? ' danger' : ''}`}>
      <span className="home-kpi-icon" aria-hidden="true"><Icon size={18} /></span>
      <div>
        <span className="home-muted">{label}</span>
        <strong>{value}</strong>
      </div>
    </div>
  )
}

function Panel({ title, wide, children }) {
  return (
    <section className={`home-card${wide ? ' wide' : ''}`}>
      <div className="home-card-head">
        <h2>{title}</h2>
      </div>
      {children}
    </section>
  )
}
