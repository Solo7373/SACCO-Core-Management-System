import { useEffect, useState } from 'react'
import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Members from './pages/Members'
import Accounts from './pages/Accounts'
import Savings from './pages/Savings'
import Shares from './pages/Shares'
import Loans from './pages/Loans'
import Collections from './pages/Collections'
import Collateral from './pages/Collateral'
import Reports from './pages/Reports'
import Users from './pages/Users'
import Inventory from './pages/Inventory'
import Compliance from './pages/Compliance'
import Backups from './pages/Backups'
import Setup from './pages/Setup'
import Reminders from './pages/Reminders'
import { api } from './services/api'

function App() {
  const [user, setUser] = useState(() => {
    const token = localStorage.getItem('token')
    if (token) {
      return {
        userId: localStorage.getItem('userId'),
        username: localStorage.getItem('username'),
        role: localStorage.getItem('role'),
        token
      }
    }
    return null
  })
  const [setup, setSetup] = useState({ loading: true, status: null, error: null })

  useEffect(() => {
    refreshSetup()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function refreshSetup() {
    setSetup(previous => ({ ...previous, loading: true, error: null }))
    try {
      const status = await api.getSetupStatus()
      setSetup({ loading: false, status, error: null })
    } catch (err) {
      setSetup({
        loading: false,
        status: null,
        error: err.message || 'Could not read the system setup status.'
      })
    }
  }

  function handleSetupComplete(status) {
    setSetup({ loading: false, status, error: null })
  }

  if (setup.loading) {
    return (
      <div className="setup-page">
        <div className="setup-shell">
          <div className="loading">Checking system setup…</div>
        </div>
      </div>
    )
  }

  if (setup.error) {
    return (
      <div className="setup-page">
        <div className="setup-shell">
          <div className="card setup-card">
            <div className="setup-section-heading">
              <h2>The system is not reachable</h2>
              <p>{setup.error}</p>
            </div>
            <div className="setup-actions">
              <button type="button" className="btn btn-primary" onClick={refreshSetup}>
                Try again
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (setup.status?.setupRequired) {
    // An existing administrator must sign in before the setup wizard can
    // change the organisation, sender identities or billing account.
    if (!user && setup.status.adminConfigured) {
      return (
        <Login
          onLogin={setUser}
          noticeText="System setup is not complete. Sign in with the administrator account to finish the go-live configuration."
        />
      )
    }
    return (
      <Setup
        initialStatus={setup.status}
        embedded={Boolean(user)}
        onLogin={setUser}
        onComplete={handleSetupComplete}
      />
    )
  }

  if (!user) {
    return <Login onLogin={setUser} />
  }

  return (
    <Routes>
      <Route element={<Layout onLogout={() => setUser(null)} />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/members" element={<Members />} />
        <Route path="/accounts" element={<Accounts />} />
        <Route path="/savings" element={<Savings />} />
        <Route path="/shares" element={<Shares />} />
        <Route path="/loans" element={<Loans />} />
        <Route path="/collections" element={<Collections />} />
        <Route path="/collateral" element={<Collateral />} />
        <Route path="/inventory" element={<Inventory />} />
        <Route path="/compliance" element={<Compliance />} />
        <Route path="/backups" element={<Backups />} />
        <Route path="/reminders" element={<Reminders />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/users" element={<Users />} />
        <Route
          path="/settings"
          element={
            <Setup
              embedded
              liveMode
              initialStatus={setup.status}
              onComplete={handleSetupComplete}
            />
          }
        />
        <Route
          path="/setup"
          element={
            <Setup
              embedded
              initialStatus={setup.status}
              onComplete={handleSetupComplete}
            />
          }
        />
      </Route>
    </Routes>
  )
}

export default App
