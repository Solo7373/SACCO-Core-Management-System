import { useState } from 'react'
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
import FinanceSettings from './pages/FinanceSettings'
import Backups from './pages/Backups'

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
        <Route path="/finance-settings" element={<FinanceSettings />} />
        <Route path="/backups" element={<Backups />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/users" element={<Users />} />
      </Route>
    </Routes>
  )
}

export default App
