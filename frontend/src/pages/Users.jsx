import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

const ROLE_BADGES = {
  ADMIN: 'danger',
  RECOVERY_ADMIN: 'danger',
  MANAGER: 'warning',
  AUDITOR: 'info',
  TELLER: 'info',
  LOAN_OFFICER: 'success',
  MEMBER: 'default'
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

function emptyForm() {
  return { username: '', password: '', email: '', memberId: '', role: 'TELLER' }
}

function Users() {
  const currentUserId = Number(localStorage.getItem('userId') || 0)
  const [users, setUsers] = useState([])
  const [roles, setRoles] = useState([])
  const [permissions, setPermissions] = useState({})
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [form, setForm] = useState(emptyForm())
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL')

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const [userData, roleData, memberData] = await Promise.all([
        api.getUsers().catch(() => []),
        api.getRoles().catch(() => ({ roles: [], permissions: {} })),
        api.getMembers().catch(() => [])
      ])
      setUsers(userData || [])
      setRoles(roleData?.roles || [])
      setPermissions(roleData?.permissions || {})
      setMembers(memberData || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function flashSuccess(message) {
    setSuccess(message)
    window.setTimeout(() => setSuccess(null), 4000)
  }

  const memberMap = useMemo(() => {
    const map = {}
    members.forEach(member => {
      map[member.memberId] = `${member.firstName} ${member.lastName} (${member.membershipNo})`
    })
    return map
  }, [members])

  const summary = useMemo(() => ({
    total: users.length,
    active: users.filter(user => user.isActive).length,
    roles: new Set(users.map(user => user.role)).size
  }), [users])

  const filteredUsers = useMemo(() => {
    const query = search.trim().toLowerCase()
    return users.filter(user => {
      const haystack = [user.username, user.email, user.role, user.memberId]
        .filter(Boolean).join(' ').toLowerCase()
      return (!query || haystack.includes(query))
        && (roleFilter === 'ALL' || user.role === roleFilter)
    })
  }, [users, search, roleFilter])

  async function handleCreateUser(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const created = await api.createUser({
        username: form.username.trim(),
        password: form.password,
        email: form.email.trim() || null,
        memberId: form.memberId ? Number(form.memberId) : null,
        role: form.role
      })
      flashSuccess(`User "${created.username}" created with role ${created.role}.`)
      setShowForm(false)
      setForm(emptyForm())
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRoleChange(user, role) {
    if (role === user.role) return
    setError(null)
    try {
      const updated = await api.updateUserRole(user.userId, role)
      flashSuccess(`Role for "${updated.username}" changed to ${updated.role}.`)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleToggleActive(user) {
    setError(null)
    try {
      const updated = user.isActive
        ? await api.deactivateUser(user.userId)
        : await api.activateUser(user.userId)
      flashSuccess(`User "${updated.username}" ${updated.isActive ? 'activated' : 'deactivated'}.`)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleResetPassword(user) {
    const password = window.prompt(`New password for "${user.username}" (at least 6 characters):`)
    if (!password) return
    if (password.length < 6) {
      setError('Password must be at least 6 characters.')
      return
    }
    setError(null)
    try {
      await api.resetUserPassword(user.userId, password)
      flashSuccess(`Password reset for "${user.username}".`)
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Users & Access Control</h2>
          <p className="page-subtitle">Manage system users, roles and the permissions granted to each role</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Cancel' : '+ New User'}
          </button>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collateral-summary users-summary">
        <div className="collateral-summary-card">
          <span>Total Users</span>
          <strong>{summary.total}</strong>
          <small>All accounts on the system</small>
        </div>
        <div className="collateral-summary-card">
          <span>Active Users</span>
          <strong>{summary.active}</strong>
          <small>Accounts allowed to sign in</small>
        </div>
        <div className="collateral-summary-card">
          <span>Roles In Use</span>
          <strong>{summary.roles}</strong>
          <small>Distinct roles assigned to users</small>
        </div>
      </div>

      {showForm && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Create User</h3>
              <p className="card-subtitle">New accounts inherit the permissions of their assigned role</p>
            </div>
            <button type="button" className="modal-close" onClick={() => setShowForm(false)}>×</button>
          </div>
          <form onSubmit={handleCreateUser}>
            <div className="grid-2">
              <div className="form-group">
                <label>Username *</label>
                <input
                  value={form.username}
                  onChange={e => setForm({ ...form, username: e.target.value })}
                  minLength={3}
                  required
                />
              </div>
              <div className="form-group">
                <label>Password *</label>
                <input
                  type="password"
                  value={form.password}
                  onChange={e => setForm({ ...form, password: e.target.value })}
                  minLength={6}
                  required
                />
              </div>
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={e => setForm({ ...form, email: e.target.value })}
                  placeholder="Optional"
                />
              </div>
              <div className="form-group">
                <label>Role *</label>
                <select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })} required>
                  {roles.map(role => <option key={role} value={role}>{role}</option>)}
                </select>
              </div>
            </div>
            <div className="form-group">
              <label>Linked Member (optional)</label>
              <select value={form.memberId} onChange={e => setForm({ ...form, memberId: e.target.value })}>
                <option value="">Not linked to a member</option>
                {members.map(member => (
                  <option key={member.memberId} value={member.memberId}>
                    {member.firstName} {member.lastName} ({member.membershipNo})
                  </option>
                ))}
              </select>
              <p className="field-hint">Link a member account so the user can be identified in member-facing flows.</p>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Creating...' : 'Create User'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>System Users</h3>
            <p className="card-subtitle">{filteredUsers.length} user{filteredUsers.length === 1 ? '' : 's'} found</p>
          </div>
        </div>

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search username, email, role..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="filter-select" value={roleFilter} onChange={e => setRoleFilter(e.target.value)}>
            <option value="ALL">All roles</option>
            {roles.map(role => <option key={role} value={role}>{role}</option>)}
          </select>
        </div>

        {loading ? (
          <div className="loading">Loading users...</div>
        ) : filteredUsers.length === 0 ? (
          <p className="empty-text">No users match your search.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Member</th>
                  <th>Status</th>
                  <th>Last Login</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map(user => (
                  <tr key={user.userId}>
                    <td>
                      <strong>{user.username}</strong>
                      {user.userId === currentUserId && <span className="cell-sub">Current session</span>}
                    </td>
                    <td>{user.email || '—'}</td>
                    <td>
                      <select
                        className="inline-select"
                        value={user.role}
                        onChange={e => handleRoleChange(user, e.target.value)}
                      >
                        {roles.map(role => <option key={role} value={role}>{role}</option>)}
                      </select>
                      <span className={`badge badge-${ROLE_BADGES[user.role] || 'default'}`}>{user.role}</span>
                    </td>
                    <td>{user.memberId ? (memberMap[user.memberId] || `Member #${user.memberId}`) : '—'}</td>
                    <td>
                      <span className={`badge badge-${user.isActive ? 'success' : 'danger'}`}>
                        {user.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>{formatDateTime(user.lastLogin)}</td>
                    <td>
                      <div className="doc-actions">
                        <button type="button" className="link-btn" onClick={() => handleResetPassword(user)}>
                          Reset Password
                        </button>
                        <button
                          type="button"
                          className={`link-btn ${user.isActive ? 'danger-link' : ''}`}
                          onClick={() => handleToggleActive(user)}
                          disabled={user.userId === currentUserId}
                          title={user.userId === currentUserId ? 'You cannot deactivate your own account' : undefined}
                        >
                          {user.isActive ? 'Deactivate' : 'Activate'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Role Permission Matrix</h3>
            <p className="card-subtitle">Effective permissions enforced by the backend for each role</p>
          </div>
        </div>
        <div className="role-matrix">
          {roles.map(role => (
            <div className="role-card" key={role}>
              <div className="role-card-header">
                <strong>{role}</strong>
                <span className="badge badge-default">{(permissions[role] || []).length} permissions</span>
              </div>
              <div className="permission-chips">
                {(permissions[role] || []).length === 0 ? (
                  <span className="field-hint">No permissions assigned.</span>
                ) : (
                  (permissions[role] || []).map(permission => (
                    <span className="permission-chip" key={permission}>{permission}</span>
                  ))
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default Users
