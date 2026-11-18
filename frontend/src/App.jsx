import { useEffect, useState } from 'react'
import {
  Menu, Search, Star, Plus, HelpCircle, Settings, Bell, ChevronDown, X,
  LayoutGrid, Briefcase, ShieldAlert, StickyNote, Home, Crown, ContactRound,
  UserPlus, Pencil,
} from 'lucide-react'
import './console.css'
import appLogo from './assets/salesforce-logo.png'
import { CaseList, CaseRecord, FraudRecord } from './pages/Cases'
import Customers from './pages/Customers'
import HomePage from './pages/Home'
import { getCase, getFraud } from './data/mockCases'

// Object switcher entries. Only objects with a page here are navigable.
const OBJECT_MENU = [
  { name: 'Home', icon: Home, color: '#ff538a' },
  { name: 'Customers', icon: UserPlus, color: '#5867e8' },
  { name: 'Leads', icon: Star, color: '#1589ee' },
  { name: 'Opportunities', icon: Crown, color: '#ff7a2b' },
  { name: 'Contacts', icon: ContactRound, color: '#9050e9' },
  { name: 'Cases', icon: Briefcase, color: '#e83e8c' },
]
const NAVIGABLE = new Set(['Home', 'Cases', 'Customers'])

// Remember the active object per browser tab so a refresh keeps the user on
// the same page. sessionStorage can be unavailable (private mode, blocked
// storage), so every access is guarded and falls back to the default.
const OBJECT_STORAGE_KEY = 'fp.activeObject'
const DEFAULT_OBJECT = 'Cases'

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

  useEffect(() => {
    try {
      window.sessionStorage.setItem(OBJECT_STORAGE_KEY, object)
    } catch {
      // Storage unavailable: the page still works, it just won't survive a refresh.
    }
  }, [object])

  const chooseObject = (name) => {
    if (!NAVIGABLE.has(name)) return
    setObject(name)
    if (name === 'Cases') setActiveCaseId(null)
    setMenuOpen(false)
  }

  // Top row: one tab per opened case. activeCaseId null shows the case list.
  const [openCases, setOpenCases] = useState([])
  const [activeCaseId, setActiveCaseId] = useState(null)

  // Second row: fraud subtabs opened from a case, and which subtab is active
  // (null = the case record itself).
  const [caseFrauds, setCaseFrauds] = useState({})
  const [activeSub, setActiveSub] = useState(null)

  // Set when a case links to a client. The Customers page opens that client.
  const [customerTarget, setCustomerTarget] = useState(null)

  const openCustomerFromCase = (name, customerId = null) => {
    setCustomerTarget({ name, customerId, requestedAt: Date.now() })
    setObject('Customers')
  }

  const openCase = (id) => {
    setOpenCases((ids) => (ids.includes(id) ? ids : [...ids, id]))
    setActiveCaseId(id)
    setActiveSub(null)
    setObject('Cases')
  }

  const closeCase = (id, event) => {
    event.stopPropagation()
    setOpenCases((ids) => ids.filter((x) => x !== id))
    setCaseFrauds(({ [id]: _removed, ...rest }) => rest)
    if (activeCaseId === id) {
      setActiveCaseId(null)
      setActiveSub(null)
    }
  }

  const openFraud = (fraudId) => {
    const caseId = getFraud(fraudId).caseId
    setCaseFrauds((map) => {
      const current = map[caseId] || []
      return current.includes(fraudId) ? map : { ...map, [caseId]: [...current, fraudId] }
    })
    setOpenCases((ids) => (ids.includes(caseId) ? ids : [...ids, caseId]))
    setActiveCaseId(caseId)
    setActiveSub(fraudId)
    setObject('Cases')
  }

  const closeFraud = (fraudId, event) => {
    event.stopPropagation()
    setCaseFrauds((map) => ({
      ...map,
      [activeCaseId]: (map[activeCaseId] || []).filter((f) => f !== fraudId),
    }))
    if (activeSub === fraudId) setActiveSub(null)
  }

  const activeCase = activeCaseId ? getCase(activeCaseId) : null
  const activeFraud = activeSub ? getFraud(activeSub) : null
  const subtabs = activeCaseId ? (caseFrauds[activeCaseId] || []) : []

  return (
    <div className="sf-app">
      {/* Row 1: utility bar with sandbox badge */}
      <div className="sf-utility">
        <button className="sf-icon-btn" aria-label="Menu"><Menu size={20} /></button>
        <span className="sf-sandbox"><Settings size={14} /> Sandbox</span>
        <button className="sf-round-btn" aria-label="Account menu"><ChevronDown size={16} /></button>
      </div>

      {/* Row 2: search + global actions */}
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

      {/* Row 3: app bar, object tab and open case tabs */}
      <div className="sf-appbar">
        <div className="sf-appbar-left">
          <button className="sf-icon-btn" aria-label="App launcher"><LayoutGrid size={20} /></button>
          <span className="sf-app-name">Financial Platform</span>
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
                      title={enabled ? undefined : 'Not available yet'}
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

        <div className="sf-tabstrip" role="tablist" aria-label="Open records">
          {openCases.map((id) => {
            const active = object === 'Cases' && activeCaseId === id
            return (
              <div key={id} className={`sf-rtab${active ? ' active' : ''}`}>
                <button role="tab" aria-selected={active} className="sf-rtab-label" onClick={() => openCase(id)}>
                  <Briefcase size={14} />
                  {id} | Case
                  <ChevronDown size={13} className="sf-rtab-chev" />
                </button>
                <button className="sf-rtab-close" aria-label={`Close case ${id}`} onClick={(e) => closeCase(id, e)}>
                  <X size={13} />
                </button>
              </div>
            )
          })}
        </div>
      </div>

      {/* Row 4: subtabs of the active case (case record + its fraud records) */}
      {object === 'Cases' && activeCaseId && (
        <div className="sf-subtabs" role="tablist" aria-label="Case subtabs">
          <button
            role="tab"
            aria-selected={!activeSub}
            className={`sf-subtab${!activeSub ? ' active' : ''}`}
            onClick={() => setActiveSub(null)}
          >
            <Briefcase size={14} />
            {activeCaseId} | Case
          </button>

          {subtabs.map((fraudId) => (
            <div key={fraudId} className={`sf-subtab fraud${activeSub === fraudId ? ' active' : ''}`}>
              <button role="tab" aria-selected={activeSub === fraudId} className="sf-subtab-label" onClick={() => setActiveSub(fraudId)}>
                <ShieldAlert size={14} />
                {fraudId}
                <ChevronDown size={13} />
              </button>
              <button className="sf-rtab-close" aria-label={`Close ${fraudId}`} onClick={(e) => closeFraud(fraudId, e)}>
                <X size={13} />
              </button>
            </div>
          ))}
        </div>
      )}

      <main className="sf-main-area">
        {object === 'Home' && <HomePage onOpenCase={openCase} onNavigate={chooseObject} />}

        {object === 'Customers' && <Customers target={customerTarget} />}

        {object === 'Cases' && !activeCase && (
          <CaseList onOpen={openCase} onOpenCustomer={openCustomerFromCase} />
        )}

        {object === 'Cases' && activeCase && !activeFraud && (
          <CaseRecord
            key={activeCase.id}
            caseItem={activeCase}
            onOpenFraud={openFraud}
            onOpenCustomer={openCustomerFromCase}
          />
        )}

        {object === 'Cases' && activeCase && activeFraud && (
          <FraudRecord key={activeFraud.id} fraud={activeFraud} onOpenCase={openCase} />
        )}
      </main>

      {/* Bottom utility bar */}
      <footer className="sf-footer">
        <span><Bell size={14} /> Notifications</span>
        <span><StickyNote size={14} /> Notes</span>
      </footer>
    </div>
  )
}

export default App
