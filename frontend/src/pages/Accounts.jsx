import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../services/api'

const ACCOUNT_TYPE_LABELS = {
  SAVINGS: 'Savings Account',
  SHARE_CAPITAL: 'Share Capital Account',
  FIXED_DEPOSIT: 'Fixed / Time Deposit',
  LOAN_DISBURSEMENT: 'Loan Disbursement Account'
}

const STATUS_LABELS = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  FROZEN: 'Frozen',
  DORMANT: 'Dormant',
  CLOSED: 'Closed'
}

const STATUS_BADGES = {
  PENDING: 'warning',
  ACTIVE: 'success',
  FROZEN: 'danger',
  DORMANT: 'default',
  CLOSED: 'default'
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : '—'
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

function emptyForm() {
  return {
    accountType: 'SAVINGS',
    currency: 'UGX',
    primaryMemberId: '',
    fixedDepositTermMonths: '12',
    fixedDepositInterestRate: ''
  }
}

function Accounts() {
  const [searchParams, setSearchParams] = useSearchParams()
  const role = localStorage.getItem('role') || ''
  const canOpenAccount = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER'].includes(role)
  const canEditAccounts = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const [accounts, setAccounts] = useState([])
  const [members, setMembers] = useState([])
  const [balances, setBalances] = useState({})
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [showForm, setShowForm] = useState(searchParams.get('new') === '1')
  const [form, setForm] = useState(emptyForm())
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [selected, setSelected] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailBalance, setDetailBalance] = useState(0)
  const [holders, setHolders] = useState([])

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const [accData, memData] = await Promise.all([
        api.getAccounts().catch(() => []),
        api.getMembers().catch(() => [])
      ])
      setAccounts(accData || [])
      setMembers(memData || [])
      setError(null)

      const balanceEntries = await Promise.all(
        (accData || []).map(async account => {
          try {
            return [account.accountId, await api.getBalance(account.accountId)]
          } catch {
            return [account.accountId, 0]
          }
        })
      )
      setBalances(Object.fromEntries(balanceEntries))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const filteredAccounts = useMemo(() => {
    const query = search.trim().toLowerCase()
    return accounts.filter(account => {
      const haystack = [
        account.accountNumber,
        account.primaryMemberName,
        account.membershipNo,
        ACCOUNT_TYPE_LABELS[account.accountType],
        account.status
      ].filter(Boolean).join(' ').toLowerCase()
      return (!query || haystack.includes(query))
        && (typeFilter === 'ALL' || account.accountType === typeFilter)
        && (statusFilter === 'ALL' || account.status === statusFilter)
    })
  }, [accounts, search, typeFilter, statusFilter])

  const summary = useMemo(() => ({
    total: accounts.length,
    active: accounts.filter(a => a.status === 'ACTIVE').length,
    dormant: accounts.filter(a => a.status === 'DORMANT').length,
    closed: accounts.filter(a => a.status === 'CLOSED').length
  }), [accounts])

  function updateField(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  function openForm() {
    setForm(emptyForm())
    setShowForm(true)
    setError(null)
    const next = new URLSearchParams(searchParams)
    next.set('new', '1')
    setSearchParams(next, { replace: true })
  }

  function closeForm() {
    setShowForm(false)
    const next = new URLSearchParams(searchParams)
    next.delete('new')
    setSearchParams(next, { replace: true })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    if (!form.primaryMemberId) {
      setError('Select a member before opening the account.')
      return
    }
    if (form.accountType === 'FIXED_DEPOSIT'
      && (!form.fixedDepositTermMonths || Number(form.fixedDepositTermMonths) < 1)) {
      setError('Enter a valid fixed deposit term.')
      return
    }
    if (form.accountType === 'FIXED_DEPOSIT' && form.fixedDepositInterestRate === '') {
      setError('Enter the fixed deposit interest rate.')
      return
    }

    setSubmitting(true)
    try {
      const payload = {
        accountType: form.accountType,
        currency: form.currency,
        fixedDepositTermMonths: form.accountType === 'FIXED_DEPOSIT'
          ? Number(form.fixedDepositTermMonths) : null,
        fixedDepositInterestRate: form.accountType === 'FIXED_DEPOSIT'
          ? Number(form.fixedDepositInterestRate) : null
      }
      const created = await api.openAccount(payload, Number(form.primaryMemberId))
      setSuccess(`Account ${created.accountNumber} opened. Activate it to start transacting.`)
      closeForm()
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleAction(account, action) {
    setError(null)
    setSuccess(null)
    try {
      if (action === 'activate') {
        await api.activateAccount(account.accountId)
        setSuccess(`${account.accountNumber} activated.`)
      } else if (action === 'freeze') {
        const reason = window.prompt('Reason for freezing this account:', 'Manual freeze')
        if (!reason) return
        await api.freezeAccount(account.accountId, reason)
        setSuccess(`${account.accountNumber} frozen.`)
      } else if (action === 'unfreeze') {
        await api.unfreezeAccount(account.accountId)
        setSuccess(`${account.accountNumber} reactivated.`)
      } else if (action === 'dormant') {
        if (!window.confirm(`Mark ${account.accountNumber} as dormant?`)) return
        await api.markAccountDormant(account.accountId)
        setSuccess(`${account.accountNumber} marked dormant.`)
      } else if (action === 'close') {
        const reason = window.prompt('Reason for closing this account:')
        if (!reason) return
        await api.closeAccount(account.accountId, reason)
        setSuccess(`${account.accountNumber} closed.`)
      }
      await loadData()
      if (selected?.accountId === account.accountId) {
        await openDetail(account)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleDormancyCheck() {
    setError(null)
    try {
      const count = await api.runDormancyCheck()
      setSuccess(`${count} account(s) marked dormant after 6 months without activity.`)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  async function openDetail(account) {
    setSelected(account)
    setDetailLoading(true)
    setDetailBalance(balances[account.accountId] || 0)
    setHolders([])
    try {
      const [balance, accountHolders] = await Promise.all([
        api.getBalance(account.accountId).catch(() => 0),
        api.getAccountHolders(account.accountId).catch(() => [])
      ])
      setDetailBalance(balance)
      setHolders(accountHolders || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setDetailLoading(false)
    }
  }

  function closeDetail() {
    setSelected(null)
    setHolders([])
  }

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Accounts</h2>
          <p className="page-subtitle">Member accounts, fixed deposits and account lifecycle</p>
        </div>
        <div className="header-actions">
          {canEditAccounts && (
            <button className="btn btn-secondary" onClick={handleDormancyCheck} title="Mark active accounts with 6+ months of no activity as dormant">
              Run Dormancy Check
            </button>
          )}
          {canOpenAccount && (
            <button className="btn btn-primary" onClick={openForm}>+ Open Account</button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collateral-summary account-summary">
        <div className="collateral-summary-card">
          <span>Total Accounts</span>
          <strong>{summary.total}</strong>
          <small>{formatMoney(Object.values(balances).reduce((sum, value) => sum + Number(value || 0), 0))} total balances</small>
        </div>
        <div className="collateral-summary-card">
          <span>Active</span>
          <strong>{summary.active}</strong>
          <small>Ready for deposits and withdrawals</small>
        </div>
        <div className="collateral-summary-card">
          <span>Dormant</span>
          <strong>{summary.dormant}</strong>
          <small>No activity for 6+ months</small>
        </div>
        <div className="collateral-summary-card">
          <span>Closed</span>
          <strong>{summary.closed}</strong>
          <small>Archived accounts</small>
        </div>
      </div>

      {showForm && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Open New Account</h3>
              <p className="card-subtitle">Account numbers are generated automatically by the system</p>
            </div>
            <button type="button" className="modal-close" onClick={closeForm}>×</button>
          </div>
          <form onSubmit={handleSubmit}>
            <div className="grid-2">
              <div className="form-group">
                <label>Account Type *</label>
                <select value={form.accountType} onChange={e => updateField('accountType', e.target.value)}>
                  <option value="SAVINGS">Savings Account</option>
                  <option value="SHARE_CAPITAL">Share Capital Account</option>
                  <option value="FIXED_DEPOSIT">Fixed / Time Deposit</option>
                  <option value="LOAN_DISBURSEMENT">Loan Disbursement Account</option>
                </select>
              </div>
              <div className="form-group">
                <label>Primary Member *</label>
                <select value={form.primaryMemberId} onChange={e => updateField('primaryMemberId', e.target.value)} required>
                  <option value="">Select member...</option>
                  {members.filter(member => member.status === 'ACTIVE').map(member => (
                    <option key={member.memberId} value={member.memberId}>
                      {member.firstName} {member.lastName} ({member.membershipNo})
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label>Currency</label>
                <select value={form.currency} onChange={e => updateField('currency', e.target.value)}>
                  <option value="UGX">UGX</option>
                  <option value="USD">USD</option>
                </select>
              </div>
              <div className="form-group">
                <label>Branch ID (optional)</label>
                <input type="number" min="1" placeholder="Branch reference" />
              </div>
            </div>

            {form.accountType === 'FIXED_DEPOSIT' && (
              <div className="grid-2">
                <div className="form-group">
                  <label>Term (months) *</label>
                  <input
                    type="number"
                    min="1"
                    value={form.fixedDepositTermMonths}
                    onChange={e => updateField('fixedDepositTermMonths', e.target.value)}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Interest Rate (% per annum) *</label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.fixedDepositInterestRate}
                    onChange={e => updateField('fixedDepositInterestRate', e.target.value)}
                    required
                  />
                </div>
              </div>
            )}

            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={closeForm}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Opening...' : 'Open Account'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Account List</h3>
            <p className="card-subtitle">{filteredAccounts.length} account{filteredAccounts.length === 1 ? '' : 's'} found</p>
          </div>
        </div>

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search account number, member, membership no..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="filter-select" value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
            <option value="ALL">All account types</option>
            {Object.entries(ACCOUNT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <select className="filter-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All statuses</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {loading ? (
          <div className="loading">Loading accounts...</div>
        ) : filteredAccounts.length === 0 ? (
          <p className="empty-text">
            {accounts.length === 0
              ? 'No accounts opened yet. Register a member, then open their first account.'
              : 'No accounts match your search.'}
          </p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Account No.</th>
                  <th>Member</th>
                  <th>Type</th>
                  <th>Balance</th>
                  <th>Status</th>
                  <th>Last Activity</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filteredAccounts.map(account => (
                  <tr key={account.accountId} className="clickable-row" onClick={() => openDetail(account)}>
                    <td>
                      <strong>{account.accountNumber}</strong>
                      <span className="cell-sub">{account.currency}</span>
                    </td>
                    <td>
                      <strong>{account.primaryMemberName || 'Unknown member'}</strong>
                      <span className="cell-sub">{account.membershipNo || '—'}</span>
                    </td>
                    <td>
                      {ACCOUNT_TYPE_LABELS[account.accountType] || account.accountType}
                      {account.accountType === 'FIXED_DEPOSIT' && (
                        <span className="cell-sub">
                          Matures {formatDate(account.fixedDepositMaturityDate)}
                        </span>
                      )}
                    </td>
                    <td>{formatMoney(balances[account.accountId])}</td>
                    <td>
                      <span className={`badge badge-${STATUS_BADGES[account.status] || 'default'}`}>
                        {STATUS_LABELS[account.status] || account.status}
                      </span>
                    </td>
                    <td>{formatDateTime(account.lastActivityAt)}</td>
                    <td>
                      <div className="doc-actions">
                        <button type="button" className="link-btn" onClick={event => { event.stopPropagation(); openDetail(account) }}>
                          View
                        </button>
                        {account.status === 'PENDING' && (
                          <button type="button" className="link-btn" onClick={event => { event.stopPropagation(); handleAction(account, 'activate') }}>
                            Activate
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selected && (
        <div className="modal-overlay" onClick={closeDetail}>
          <div className="modal collateral-detail-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Account Details</h3>
              <button type="button" className="modal-close" onClick={closeDetail}>×</button>
            </div>
            <div className="modal-body">
              {detailLoading ? (
                <div className="loading">Loading account...</div>
              ) : (
                <>
                  <div className="collateral-profile-header">
                    <div className="asset-avatar">
                      {(selected.accountType || 'AC').slice(0, 2)}
                    </div>
                    <div className="profile-heading">
                      <h3>{selected.accountNumber}</h3>
                      <p>
                        {ACCOUNT_TYPE_LABELS[selected.accountType] || selected.accountType}
                        {' · '}
                        <span className={`badge badge-${STATUS_BADGES[selected.status] || 'default'}`}>
                          {STATUS_LABELS[selected.status] || selected.status}
                        </span>
                      </p>
                      <div className="profile-stats">
                        <div>
                          <span>Available Balance</span>
                          <strong>{formatMoney(detailBalance)}</strong>
                        </div>
                        <div>
                          <span>Member</span>
                          <strong>{selected.primaryMemberName || '—'}</strong>
                        </div>
                        <div>
                          <span>Currency</span>
                          <strong>{selected.currency}</strong>
                        </div>
                      </div>
                    </div>
                  </div>

                  <h4 className="section-title">Account Information</h4>
                  <div className="info-grid">
                    <div className="info-row"><span>Membership No.</span><strong>{selected.membershipNo || '—'}</strong></div>
                    <div className="info-row"><span>Opened</span><strong>{formatDateTime(selected.openedAt)}</strong></div>
                    <div className="info-row"><span>Last Activity</span><strong>{formatDateTime(selected.lastActivityAt)}</strong></div>
                    <div className="info-row"><span>Dormant Since</span><strong>{formatDateTime(selected.dormantSince)}</strong></div>
                    <div className="info-row"><span>Closed At</span><strong>{formatDateTime(selected.closedAt)}</strong></div>
                    {selected.accountType === 'FIXED_DEPOSIT' && (
                      <>
                        <div className="info-row"><span>Term</span><strong>{selected.fixedDepositTermMonths || '—'} months</strong></div>
                        <div className="info-row"><span>Interest Rate</span><strong>{selected.fixedDepositInterestRate != null ? `${selected.fixedDepositInterestRate}% p.a.` : '—'}</strong></div>
                        <div className="info-row"><span>Maturity Date</span><strong>{formatDate(selected.fixedDepositMaturityDate)}</strong></div>
                      </>
                    )}
                  </div>

                  <h4 className="section-title">Account Holders</h4>
                  {holders.length === 0 ? (
                    <p className="empty-text">No holder records found.</p>
                  ) : (
                    <div className="info-grid">
                      {holders.map(holder => (
                        <div className="info-row" key={`${holder.accountId}-${holder.memberId}`}>
                          <span>{holder.holderType} holder</span>
                          <strong>Member #{holder.memberId}</strong>
                        </div>
                      ))}
                    </div>
                  )}

                  {canEditAccounts && (
                    <div className="action-row">
                      {selected.status === 'PENDING' && (
                        <button type="button" className="btn btn-success" onClick={() => handleAction(selected, 'activate')}>
                          Activate Account
                        </button>
                      )}
                      {selected.status === 'ACTIVE' && (
                        <>
                          <button type="button" className="btn btn-danger" onClick={() => handleAction(selected, 'freeze')}>
                            Freeze
                          </button>
                          <button type="button" className="btn btn-secondary" onClick={() => handleAction(selected, 'dormant')}>
                            Mark Dormant
                          </button>
                        </>
                      )}
                      {(selected.status === 'FROZEN' || selected.status === 'DORMANT') && (
                        <button type="button" className="btn btn-success" onClick={() => handleAction(selected, 'unfreeze')}>
                          Reactivate
                        </button>
                      )}
                      {selected.status !== 'CLOSED' && (
                        <button type="button" className="btn btn-ghost" onClick={() => handleAction(selected, 'close')}>
                          Close Account
                        </button>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Accounts
