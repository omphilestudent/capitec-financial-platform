import { useEffect, useState } from 'react'
import {
  Menu, Search, Star, Plus, HelpCircle, Settings, Bell, ChevronDown,
  LayoutGrid, Briefcase, StickyNote, Home, Crown, ContactRound,
  UserPlus, Pencil, ShieldAlert,
} from 'lucide-react'
import './console.css'
import './pages/Cases.css'
import appLogo from './assets/salesforce-logo.png'
import { CaseList, CaseRecord } from './pages/Cases'
import { FraudList, FraudRecordView } from './pages/Fraud'
import Customers from './pages/Customers'
import HomePage from './pages/Home'

const OBJECT_MENU = [
  { name: 'Home', icon: Home, color: '#ff538a' },
  { name: 'Customers', icon: UserPlus, color: '#5867e8' },
  { name: 'Cases', icon: Briefcase, color: '#e83e8c' },
  { name: 'Fraud', icon: ShieldAlert, color: '#2e844a' },
  { name: 'Leads', icon: Star, color: '#1589ee' },
  { name: 'Opportunities', icon: Crown, color: '#ff7a2b' },
  { name: 'Contacts', icon: ContactRound, color: '#9050e9' },
]
const NAVIGABLE = new Set(['Home', 'Customers', 'Cases', 'Fraud'])
const DEFAULT_OBJECT = 'Home'

const OBJECT_STORAGE_KEY = 'fp.activeObject'

function readStoredObject() {
  try {
    const stored = window.sessionStorage.getItem(OBJECT_STORAGE_KEY)
    return NAVIGABLE.has(stored) ? stored : DEFAULT_OBJECT
  } catch {
    return DEFAULT_OBJECT
  }
}

function App() {
  const [object, setObject] = useState(readStoredObject)
  const [menuOpen, setMenuOpen] = useState(false)
  const [caseId, setCaseId] = useState(null)
  const [fraudId, setFraudId] = useState(null)
  const [customerId, setCustomerId] = useState(null)

  useEffect(() => {
    try {
      window.sessionStorage.setItem(OBJECT_STORAGE_KEY, object)
    } catch {
      return undefined
    }
    return undefined
  }, [object])

  const chooseObject = (name) => {
    if (!NAVIGABLE.has(name)) return
    setCaseId(null)
    setFraudId(null)
    setObject(name)
    setMenuOpen(false)
  }

  const openCase = (id) => {
    setFraudId(null)
    setCaseId(id)
    setObject('Cases')
  }

  const openFraud = (id) => {
    setCaseId(null)
    setFraudId(id)
    setObject('Fraud')
  }

  const openCustomer = (id) => {
    setCustomerId(id)
    setObject('Customers')
  }

  return (
    <div className="sf-app">
      <div className="sf-utility">
        <button className="sf-icon-btn" aria-label="Menu"><Menu size={20} /></button>
        <span className="sf-sandbox"><Settings size={14} /> Sandbox</span>
        <button className="sf-round-btn" aria-label="Account menu"><ChevronDown size={16} /></button>
      </div>

      <div className="sf-searchbar">
        <img className="sf-logo" src={appLogo} alt="Salesforce" />

        <label className="sf-global-search">
          <Search size={16} aria-hidden="true" />
          <input placeholder="Search..." aria-label="Search" />
        </label>

        <div className="sf-global-actions">
          <div className="sf-favorites" aria-label="Favorites">
            <Star size={16} />
            <ChevronDown size={12} />
          </div>
          <button className="sf-add" aria-label="New"><Plus size={18} /></button>
          <button className="sf-icon-btn" aria-label="Help"><HelpCircle size={19} /></button>
          <button className="sf-icon-btn" aria-label="Setup"><Settings size={18} /></button>
          <button className="sf-icon-btn" aria-label="Notifications"><Bell size={18} /></button>
          <button className="sf-avatar-btn" aria-label="User menu">OM</button>
        </div>
      </div>

      <div className="sf-appbar">
        <div className="sf-appbar-left">
          <button className="sf-icon-btn" aria-label="App launcher"><LayoutGrid size={20} /></button>
          <span className="sf-app-name">{object}</span>
        </div>

        <div className="sf-object-switch" onKeyDown={(e) => e.key === 'Escape' && setMenuOpen(false)}>
          <button
            className={`sf-object-trigger${menuOpen ? ' open' : ''}`}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {object}
            <ChevronDown size={14} />
          </button>

          {menuOpen && (
            <>
              <div className="sf-menu-backdrop" onClick={() => setMenuOpen(false)} />
              <div className="sf-object-menu" role="menu" aria-label="Objects">
                {OBJECT_MENU.map(({ name, icon: Icon, color }) => {
                  const selected = object === name
                  const enabled = NAVIGABLE.has(name)
                  return (
                    <button
                      key={name}
                      role="menuitem"
                      className={`sf-menu-item${selected ? ' selected' : ''}`}
                      disabled={!enabled}
                      title={enabled ? undefined : 'Not available: no service provides this yet'}
                      onClick={() => chooseObject(name)}
                    >
                      <span className="sf-menu-icon" style={{ background: color }}>
                        <Icon size={16} />
                      </span>
                      {name}
                    </button>
                  )
                })}
                <button className="sf-menu-edit" disabled>
                  <Pencil size={14} /> Edit
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      <main className="sf-main-area">
        {object === 'Home' && <HomePage onNavigate={chooseObject} />}

        {object === 'Customers' && <Customers openCustomerId={customerId} />}

        {object === 'Cases' && !caseId && <CaseList onOpen={openCase} />}
        {object === 'Cases' && caseId && (
          <CaseRecord
            key={caseId}
            caseId={caseId}
            onBack={() => setCaseId(null)}
            onOpenFraud={openFraud}
            onOpenCustomer={openCustomer}
          />
        )}

        {object === 'Fraud' && !fraudId && <FraudList onOpen={openFraud} onOpenCase={openCase} />}
        {object === 'Fraud' && fraudId && (
          <FraudRecordView key={fraudId} fraudId={fraudId} onBack={() => setFraudId(null)} onOpenCase={openCase} />
        )}
      </main>

      <footer className="sf-footer">
        <span><Bell size={14} /> Notifications</span>
        <span><StickyNote size={14} /> Notes</span>
      </footer>
    </div>
  )
}

export default App
