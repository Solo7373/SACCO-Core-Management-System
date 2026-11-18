import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import ThemeToggle from './ThemeToggle'
import { api } from '../services/api'

function Layout({ onLogout }) {
  const username = localStorage.getItem('username') || 'User'
  const role = localStorage.getItem('role') || 'MEMBER'
  const userId = localStorage.getItem('userId') || '1'
  const memberId = localStorage.getItem('memberId') || ''
  const canManageUsers = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER'].includes(role)
  const canViewReports = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const canViewInventory = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER', 'AUDITOR'].includes(role)
  const canViewFinance = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const canViewCompliance = ['ADMIN', 'RECOVERY_ADMIN', 'AUDITOR'].includes(role)
  const canViewBackups = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const canManageSettings = ['ADMIN', 'RECOVERY_ADMIN'].includes(role)
  const canViewCollections = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER', 'AUDITOR'].includes(role)
  const isMemberPortal = role === 'MEMBER'

  const [resetRequired, setResetRequired] = useState(
    () => localStorage.getItem('passwordResetRequired') === 'true'
  )

  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const [showPassword, setShowPassword] = useState(false)
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  })
  const [passwordState, setPasswordState] = useState({ loading: false, error: null, success: null })

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    localStorage.removeItem('passwordResetRequired')
    onLogout()
  }

  function openPasswordModal() {
    setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
    setPasswordState({ loading: false, error: null, success: null })
    setShowPassword(true)
  }

  async function submitPassword(event) {
    event.preventDefault()
    setPasswordState({ loading: true, error: null, success: null })
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordState({ loading: false, error: 'The new passwords do not match', success: null })
      return
    }
    try {
      await api.changePassword(userId, {
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword
      })
      localStorage.setItem('passwordResetRequired', 'false')
      setResetRequired(false)
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      setPasswordState({ loading: false, error: null, success: 'Password updated successfully.' })
    } catch (err) {
      setPasswordState({ loading: false, error: err.message, success: null })
    }
  }

  return (
    <div className="layout">
      <button
        type="button"
        className="mobile-menu-toggle"
        onClick={() => setMobileMenuOpen(true)}
        aria-label="Open navigation menu"
      >
        ☰
      </button>
      {mobileMenuOpen && (
        <div className="mobile-menu-overlay" onClick={() => setMobileMenuOpen(false)} />
      )}
      <aside className={`sidebar ${mobileMenuOpen ? 'sidebar-open' : ''}`}>
        <button
          type="button"
          className="mobile-menu-close"
          onClick={() => setMobileMenuOpen(false)}
          aria-label="Close navigation menu"
        >
          ×
        </button>
        <h1>SCMS</h1>
        <div className="sidebar-user">
          <span className="user-name">{username}</span>
          <span className="user-role">{role}</span>
        </div>
        <nav onClick={() => setMobileMenuOpen(false)}>
          <NavLink to="/" end>Dashboard</NavLink>
          {!isMemberPortal && <NavLink to="/members">Members</NavLink>}
          {isMemberPortal && memberId && (
            <NavLink to={`/members/${memberId}`}>My Profile</NavLink>
          )}
          <NavLink to="/accounts">Accounts</NavLink>
          <NavLink to="/savings">Savings</NavLink>
          <NavLink to="/shares">Shares</NavLink>
          <NavLink to="/loans">Loans</NavLink>
          {!isMemberPortal && canViewCollections && <NavLink to="/collections">Collections</NavLink>}
          <NavLink to="/collateral">Collateral</NavLink>
          {!isMemberPortal && canViewInventory && <NavLink to="/inventory">Inventory</NavLink>}
          {!isMemberPortal && canViewFinance && <NavLink to="/finance-settings">Finance Settings</NavLink>}
          {!isMemberPortal && canViewCompliance && <NavLink to="/compliance">Compliance</NavLink>}
          {!isMemberPortal && canViewReports && <NavLink to="/reports">Reports</NavLink>}
          {!isMemberPortal && canViewBackups && <NavLink to="/backups">Backups</NavLink>}
          {!isMemberPortal && canManageSettings && <NavLink to="/settings">System Settings</NavLink>}
          {!isMemberPortal && canManageUsers && <NavLink to="/users">Users</NavLink>}
        </nav>
        <button className="logout-btn" onClick={handleLogout}>Logout</button>
        <button className="password-btn" onClick={openPasswordModal}>Change Password</button>
      </aside>
      <main className="main-content">
        {resetRequired && (
          <div className="warning-banner">
            A password reset is required for this account before it can be used normally.
            <button className="warning-banner-btn" onClick={openPasswordModal}>Change password now</button>
          </div>
        )}
        {role === 'AUDITOR' && (
          <div className="info-banner">
            Auditor compliance workspace. Exports, record edits, soft-deletions and restorations are
            audited. Financial posting and reversal actions remain restricted.
          </div>
        )}
        <Outlet />
      </main>
      <ThemeToggle />

      {showPassword && (
        <div className="modal-overlay" onClick={() => setShowPassword(false)}>
          <div className="modal password-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Change Password</h3>
              <button className="modal-close" onClick={() => setShowPassword(false)}>x</button>
            </div>
            <div className="modal-body">
              {passwordState.error && <div className="modal-feedback error">{passwordState.error}</div>}
              {passwordState.success && <div className="modal-feedback success">{passwordState.success}</div>}
              <form onSubmit={submitPassword}>
                <div className="form-group">
                  <label>Current Password *</label>
                  <input
                    type="password"
                    value={passwordForm.currentPassword}
                    onChange={e => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                    autoComplete="current-password"
                    required
                  />
                </div>
                <div className="form-group">
                  <label>New Password *</label>
                  <input
                    type="password"
                    value={passwordForm.newPassword}
                    onChange={e => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                    autoComplete="new-password"
                    minLength={8}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Confirm New Password *</label>
                  <input
                    type="password"
                    value={passwordForm.confirmPassword}
                    onChange={e => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                    autoComplete="new-password"
                    minLength={8}
                    required
                  />
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setShowPassword(false)}>
                    Close
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={passwordState.loading}>
                    {passwordState.loading ? 'Updating...' : 'Update Password'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Layout
