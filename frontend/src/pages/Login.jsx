import { useState, useEffect } from 'react'
import { api } from '../services/api'

function Login({ onLogin, noticeText = null }) {
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [recovery, setRecovery] = useState({
    username: 'admin',
    recoveryKey: '',
    newPassword: '',
    confirmPassword: ''
  })
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [loading, setLoading] = useState(false)

  // Clear any leftover credentials on mount
  useEffect(() => {
    setUsername('')
    setPassword('')
    setShowPassword(false)
    setError(null)
    setNotice(null)
  }, [])

  function switchMode(nextMode) {
    setMode(nextMode)
    setError(null)
    setNotice(null)
    setShowPassword(false)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setNotice(null)
    setLoading(true)
    try {
      const response = await api.login({ username, password })
      localStorage.setItem('token', response.token)
      localStorage.setItem('userId', response.userId ?? '1')
      localStorage.setItem('username', response.username)
      localStorage.setItem('role', response.role)
      localStorage.setItem('memberId', response.memberId ? String(response.memberId) : '')
      localStorage.setItem('passwordResetRequired', response.passwordResetRequired ? 'true' : 'false')
      onLogin(response)
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  async function handleRecovery(e) {
    e.preventDefault()
    setError(null)
    setNotice(null)
    if (recovery.newPassword !== recovery.confirmPassword) {
      setError('The new passwords do not match')
      return
    }
    setLoading(true)
    try {
      await api.recoverAdmin({
        username: recovery.username,
        recoveryKey: recovery.recoveryKey,
        newPassword: recovery.newPassword
      })
      setUsername(recovery.username)
      setPassword('')
      setRecovery({ username: 'admin', recoveryKey: '', newPassword: '', confirmPassword: '' })
      setMode('login')
      setNotice('Administrator password reset successfully. Sign in with the new password.')
    } catch (err) {
      setError(err.message || 'Recovery failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <h1>SCMS</h1>
          <p>SACCO Core Management System</p>
        </div>

        {noticeText && <div className="info-banner">{noticeText}</div>}
        {error && <div className="error-message">{error}</div>}
        {notice && <div className="success-message">{notice}</div>}

        {mode === 'login' ? (
          <>
            <form onSubmit={handleSubmit} autoComplete="off">
              <div className="form-group">
                <label htmlFor="username">Username</label>
                <input
                  id="username"
                  name="username"
                  type="text"
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  placeholder="Enter username"
                  autoComplete="off"
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="password">Password</label>
                <div className="password-wrapper">
                  <input
                    id="password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    placeholder="Enter password"
                    autoComplete="new-password"
                    required
                  />
                  <button
                    type="button"
                    className="toggle-password"
                    onClick={() => setShowPassword(!showPassword)}
                    title={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
              </div>
              <button type="submit" className="btn-login" disabled={loading}>
                {loading ? 'Signing in...' : 'Sign In'}
              </button>
            </form>
            <div className="login-links">
              <button type="button" className="login-link-btn" onClick={() => switchMode('recover')}>
                Forgot password or locked out?
              </button>
            </div>
          </>
        ) : (
          <form onSubmit={handleRecovery} autoComplete="off">
            <p className="recovery-hint">
              Administrator recovery requires the recovery key configured on the server.
            </p>
            <div className="form-group">
              <label htmlFor="recovery-username">Administrator Username</label>
              <input
                id="recovery-username"
                type="text"
                value={recovery.username}
                onChange={e => setRecovery({ ...recovery, username: e.target.value })}
                placeholder="admin"
                autoComplete="off"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="recovery-key">Recovery Key</label>
              <input
                id="recovery-key"
                type="password"
                value={recovery.recoveryKey}
                onChange={e => setRecovery({ ...recovery, recoveryKey: e.target.value })}
                placeholder="Enter the server recovery key"
                autoComplete="off"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="recovery-password">New Password</label>
              <input
                id="recovery-password"
                type="password"
                value={recovery.newPassword}
                onChange={e => setRecovery({ ...recovery, newPassword: e.target.value })}
                placeholder="At least 8 characters"
                autoComplete="new-password"
                minLength={8}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="recovery-confirm">Confirm New Password</label>
              <input
                id="recovery-confirm"
                type="password"
                value={recovery.confirmPassword}
                onChange={e => setRecovery({ ...recovery, confirmPassword: e.target.value })}
                placeholder="Repeat the new password"
                autoComplete="new-password"
                minLength={8}
                required
              />
            </div>
            <button type="submit" className="btn-login" disabled={loading}>
              {loading ? 'Resetting...' : 'Reset Administrator Password'}
            </button>
            <div className="login-links">
              <button type="button" className="login-link-btn" onClick={() => switchMode('login')}>
                Back to sign in
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default Login
