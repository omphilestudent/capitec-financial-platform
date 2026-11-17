
import { useState } from 'react'
import {
  LayoutGrid, Home, ArrowLeftRight, ShieldAlert, FileText,
  Settings, Search, Bell, HelpCircle, Plus, ChevronDown,
  Menu, X, UserRound, Wallet, TrendingUp, Clock,
  MoreHorizontal, ArrowUpRight, ArrowDownLeft
} from 'lucide-react'
import './App.css'
import appLogo from './assets/salesforce-logo.png'

const navItems = [
  { name: 'Home', icon: Home },
  { name: 'Transactions', icon: ArrowLeftRight },
  { name: 'Fraud Monitoring', icon: ShieldAlert },
  { name: 'Statements', icon: FileText },
]

const transactions = [
  { id: 'TXN-10021', merchant: 'Woolworths', category: 'Shopping', date: '16 Nov 2026', amount: '-R 1,250.00', status: 'Completed', type: 'debit' },
  { id: 'TXN-10020', merchant: 'Salary Deposit', category: 'Income', date: '15 Nov 2026', amount: '+R 16,880.50', status: 'Completed', type: 'credit' },
  { id: 'TXN-10019', merchant: 'Checkers', category: 'Groceries', date: '14 Nov 2026', amount: '-R 845.60', status: 'Completed', type: 'debit' },
  { id: 'TXN-10018', merchant: 'Uber', category: 'Transport', date: '13 Nov 2026', amount: '-R 186.00', status: 'Completed', type: 'debit' },
]

function App() {
  const [activePage, setActivePage] = useState('Home')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [activeTab, setActiveTab] = useState('Overview')

  const navigate = (page) => {
    setActivePage(page)
    setSidebarOpen(false)
  }

  return (
      <div className="salesforce-shell">
        <header className="global-header">
          <div className="header-brand">
            <button className="launcher-button" aria-label="App launcher">
              <LayoutGrid size={21} />
            </button>
            <img className="app-logo" src={appLogo} alt="App logo" />
            <div className="brand-title">
              <strong>Financial Intelligence</strong>
              <span>CAPITEC</span>
            </div>
          </div>

          <div className="global-search">
            <Search size={17} />
            <input placeholder="Search this application..." />
            <kbd>⌘ K</kbd>
          </div>

          <div className="header-actions">
            <button aria-label="Help"><HelpCircle size={19} /></button>
            <button aria-label="Notifications" className="notification-icon">
              <Bell size={19} /><i />
            </button>
            <div className="header-user">OM</div>
            <button aria-label="User menu"><ChevronDown size={15} /></button>
          </div>
        </header>

        <div className="app-navigation">
          <button className="app-name">
            <span className="app-symbol"><Wallet size={17} /></span>
            Financial Platform
            <ChevronDown size={15} />
          </button>

          <nav className="navigation-tabs">
            {navItems.map(({ name, icon: Icon }) => (
                <button
                    key={name}
                    className={activePage === name ? 'selected' : ''}
                    onClick={() => navigate(name)}
                >
                  <Icon size={16} />
                  {name}
                </button>
            ))}
          </nav>

          <button className="navigation-settings" onClick={() => navigate('Settings')}>
            <Settings size={17} />
          </button>
        </div>

        <div className="mobile-navigation">
          <button onClick={() => setSidebarOpen(!sidebarOpen)}>
            {sidebarOpen ? <X /> : <Menu />}
          </button>
          <strong>{activePage}</strong>
        </div>

        {sidebarOpen && (
            <div className="mobile-sidebar">
              {navItems.map(({ name, icon: Icon }) => (
                  <button key={name} onClick={() => navigate(name)}>
                    <Icon size={17} /> {name}
                  </button>
              ))}
            </div>
        )}

        <main className="workspace">
          <div className="workspace-heading">
            <div>
              <div className="breadcrumbs">Financial Platform <span>›</span> {activePage}</div>
              <h1>{activePage === 'Home' ? 'Financial Overview' : activePage}</h1>
              <p>Welcome back, Omphile. Here's what's happening today.</p>
            </div>
            <div className="heading-actions">
              <button className="lightning-button secondary">
                <Clock size={15} /> Recent activity
              </button>
              <button className="lightning-button primary" onClick={() => navigate('Statements')}>
                <Plus size={16} /> New statement
              </button>
            </div>
          </div>

          {activePage === 'Home' ? (
              <>
                <div className="workspace-tabs">
                  {['Overview', 'My Accounts', 'Analytics'].map(tab => (
                      <button
                          key={tab}
                          onClick={() => setActiveTab(tab)}
                          className={activeTab === tab ? 'active' : ''}
                      >
                        {tab}
                      </button>
                  ))}
                </div>

                <section className="record-banner">
                  <div className="record-avatar"><Wallet size={24} /></div>
                  <div className="record-info">
                    <span>Financial portfolio</span>
                    <h2>Personal Banking Overview</h2>
                    <div className="record-fields">
                      <div><small>Status</small><strong>Active</strong></div>
                      <div><small>Accounts</small><strong>3</strong></div>
                      <div><small>Last updated</small><strong>16 Nov 2026</strong></div>
                    </div>
                  </div>
                  <button className="record-menu"><MoreHorizontal /></button>
                </section>

                {activeTab === 'Overview' && (
                    <>
                      <div className="section-title">
                        <h2>Key Performance Indicators</h2>
                        <span>Demonstration data</span>
                      </div>

                      <section className="kpi-grid">
                        <Kpi title="Total balance" value="R 48,250.75" change="+8.2%" icon={Wallet} />
                        <Kpi title="Total income" value="R 32,880.50" change="+12.5%" icon={ArrowDownLeft} />
                        <Kpi title="Total expenses" value="R 12,450.20" change="-3.4%" icon={ArrowUpRight} />
                        <Kpi title="Active accounts" value="3" change="All active" icon={UserRound} />
                      </section>

                      <div className="content-grid">
                        <section className="lightning-card">
                          <div className="card-heading">
                            <div className="card-title">
                              <span className="card-icon"><ArrowLeftRight size={16} /></span>
                              <div>
                                <h2>Recent Transactions</h2>
                                <p>Latest financial activity</p>
                              </div>
                            </div>
                            <button className="link-button" onClick={() => navigate('Transactions')}>View All</button>
                          </div>

                          <div className="data-table">
                            <table>
                              <thead>
                              <tr>
                                <th>Transaction</th>
                                <th>Category</th>
                                <th>Date</th>
                                <th>Amount</th>
                                <th>Status</th>
                              </tr>
                              </thead>
                              <tbody>
                              {transactions.map(tx => (
                                  <tr key={tx.id}>
                                    <td>
                                      <div className="transaction-cell">
                                  <span className={`transaction-icon ${tx.type}`}>
                                    {tx.type === 'credit' ? <ArrowDownLeft size={16} /> : <ArrowUpRight size={16} />}
                                  </span>
                                        <div><strong>{tx.merchant}</strong><small>{tx.id}</small></div>
                                      </div>
                                    </td>
                                    <td>{tx.category}</td>
                                    <td>{tx.date}</td>
                                    <td className={tx.type === 'credit' ? 'credit' : 'debit'}>{tx.amount}</td>
                                    <td><span className="success-pill">{tx.status}</span></td>
                                  </tr>
                              ))}
                              </tbody>
                            </table>
                          </div>
                        </section>

                        <section className="lightning-card">
                          <div className="card-heading">
                            <div className="card-title">
                              <span className="card-icon card-icon-alert"><ShieldAlert size={16} /></span>
                              <div><h2>Fraud Monitoring</h2><p>Security overview</p></div>
                            </div>
                            <span className="live-pill">LIVE</span>
                          </div>

                          <div className="fraud-banner">
                            <ShieldAlert size={23} />
                            <div><strong>3 alerts require attention</strong><span>Review flagged activity</span></div>
                          </div>

                          <div className="fraud-item">
                            <span className="risk-dot high" />
                            <div><strong>High Risk Alert</strong><small>Unusual transaction pattern</small></div>
                            <span>10:42</span>
                          </div>
                          <div className="fraud-item">
                            <span className="risk-dot medium" />
                            <div><strong>Medium Risk Alert</strong><small>Unrecognised merchant</small></div>
                            <span>09:18</span>
                          </div>
                          <div className="fraud-item">
                            <span className="risk-dot medium" />
                            <div><strong>Medium Risk Alert</strong><small>Multiple transaction attempts</small></div>
                            <span>08:56</span>
                          </div>

                          <button className="link-button full-link" onClick={() => navigate('Fraud Monitoring')}>
                            Open fraud monitoring →
                          </button>
                        </section>
                      </div>
                    </>
                )}

                {activeTab === 'My Accounts' && (
                    <section className="lightning-card empty-tab">
                      <Wallet size={30} />
                      <h2>My Accounts</h2>
                      <p>Account records and balances will appear here.</p>
                    </section>
                )}

                {activeTab === 'Analytics' && (
                    <section className="lightning-card empty-tab">
                      <TrendingUp size={30} />
                      <h2>Financial Analytics</h2>
                      <p>Financial trends and reports will appear here.</p>
                    </section>
                )}
              </>
          ) : (
              <section className="lightning-card empty-tab">
                {activePage === 'Transactions' && <ArrowLeftRight size={32} />}
                {activePage === 'Fraud Monitoring' && <ShieldAlert size={32} />}
                {activePage === 'Statements' && <FileText size={32} />}
                {activePage === 'Settings' && <Settings size={32} />}
                <h2>{activePage}</h2>
                <p>This workspace is ready for its next implementation milestone.</p>
                <button className="lightning-button primary" onClick={() => navigate('Home')}>
                  Return to overview
                </button>
              </section>
          )}
        </main>

        <footer className="utility-bar">
          <span><Bell size={14} /> Notifications</span>
          <span><Clock size={14} /> System Activity</span>
          <span className="environment-label">DEV ENVIRONMENT</span>
        </footer>
      </div>
  )
}

function Kpi({ title, value, change, icon: Icon }) {
  return (
      <section className="lightning-card kpi-card">
        <div className="kpi-heading">
          <span>{title}</span>
          <Icon size={19} />
        </div>
        <strong>{value}</strong>
        <div className="kpi-change"><TrendingUp size={13} /> {change}</div>
      </section>
  )
}

export default App
