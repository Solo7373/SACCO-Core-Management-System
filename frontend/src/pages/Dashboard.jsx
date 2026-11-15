import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend
} from 'chart.js'
import { Doughnut } from 'react-chartjs-2'
import CandlestickChart from '../components/CandlestickChart'
import { api } from '../services/api'

ChartJS.register(
  ArcElement,
  Tooltip,
  Legend
)

const ACTION_LABELS = {
  MEMBER_REGISTERED: 'New member registered',
  MEMBER_REJECTED: 'Member rejected',
  MEMBER_CLOSED: 'Member closed',
  MEMBER_PHOTO_UPLOADED: 'Member passport photo uploaded',
  IDENTITY_DOCUMENT_ADDED: 'Identity document added',
  IDENTITY_DOCUMENT_UPLOADED: 'Identity document scan uploaded',
  VERIFICATION_SUBMITTED: 'Document verification submitted',
  ACCOUNT_OPENED: 'New account opened',
  ACCOUNT_ACTIVATED: 'Account activated',
  ACCOUNT_FROZEN: 'Account frozen',
  ACCOUNT_UNFROZEN: 'Account unfrozen',
  ACCOUNT_CLOSED: 'Account closed',
  ACCOUNT_HOLDER_ADDED: 'Account holder added',
  SAVINGS_TRANSACTION_POSTED: 'Savings transaction posted',
  SAVINGS_TRANSACTION_REVERSED: 'Savings transaction reversed',
  SHARE_PRODUCT_CREATED: 'Share product created',
  SHARE_TRANSACTION_RECORDED: 'Share transaction recorded',
  SHARE_TRANSACTION_APPROVED: 'Share transaction approved',
  SHARE_TRANSACTION_REJECTED: 'Share transaction rejected',
  COLLATERAL_REGISTERED: 'Collateral registered',
  COLLATERAL_UPDATED: 'Collateral details updated',
  COLLATERAL_RELEASED: 'Collateral released to member',
  COLLATERAL_MARKED_DEFAULT: 'Collateral marked under default / auction',
  COLLATERAL_DOCUMENT_UPLOADED: 'Collateral document uploaded',
  COLLATERAL_DOCUMENT_DELETED: 'Collateral document deleted',
  LOAN_PRODUCT_CREATED: 'Loan product created',
  LOAN_APPLICATION_SUBMITTED: 'Loan application submitted',
  LOAN_REVIEWED: 'Loan application reviewed',
  LOAN_APPROVED: 'Loan approved',
  LOAN_REJECTED: 'Loan rejected',
  LOAN_DISBURSED: 'Loan disbursed',
  LOAN_REPAYMENT_POSTED: 'Loan repayment posted',
  USER_CREATED: 'User account created',
  USER_ROLE_CHANGED: 'User role changed',
  USER_ACTIVATED: 'User account activated',
  USER_DEACTIVATED: 'User account deactivated',
  USER_PASSWORD_RESET: 'User password reset'
}

const STATUS_COLORS = {
  ACTIVE: '#2e7d32',
  PENDING: '#ef6c00',
  FROZEN: '#c62828',
  DORMANT: '#6d4c41',
  CLOSED: '#9e9e9e'
}

function useDarkMode() {
  const [dark, setDark] = useState(() => document.body.classList.contains('dark-mode'))

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setDark(document.body.classList.contains('dark-mode'))
    })
    observer.observe(document.body, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  return dark
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function badgeClass(status) {
  const map = {
    ACTIVE: 'success', COMPLETED: 'success', VERIFIED: 'success',
    PENDING: 'warning', FROZEN: 'danger', SUSPENDED: 'danger',
    REJECTED: 'danger', DORMANT: 'default', CLOSED: 'default',
    DISBURSED: 'success', IN_ARREARS: 'danger', APPROVED: 'success'
  }
  return `badge badge-${map[status] || 'default'}`
}

function statusBadge(status) {
  const map = {
    ACTIVE: 'success',
    PENDING: 'warning',
    FROZEN: 'danger',
    SUSPENDED: 'danger',
    REJECTED: 'danger',
    DORMANT: 'default',
    CLOSED: 'default'
  }
  return `badge badge-${map[status] || 'default'}`
}

function Dashboard() {
  const navigate = useNavigate()
  const dark = useDarkMode()
  const role = localStorage.getItem('role') || ''
  const isMemberPortal = role === 'MEMBER'
  const memberId = localStorage.getItem('memberId') || ''
  const [summary, setSummary] = useState(null)
  const [memberProfile, setMemberProfile] = useState(null)
  const [memberLoans, setMemberLoans] = useState([])
  const [memberMonthly, setMemberMonthly] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [chartFrom, setChartFrom] = useState('')
  const [chartTo, setChartTo] = useState('')
  const [chartGranularity, setChartGranularity] = useState('MONTHLY')
  const [memberChartFrom, setMemberChartFrom] = useState('')
  const [memberChartTo, setMemberChartTo] = useState('')
  const [memberChartGranularity, setMemberChartGranularity] = useState('MONTHLY')

  useEffect(() => {
    loadData()
  }, [])

  useEffect(() => {
    if (!loading) loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartFrom, chartTo, chartGranularity, memberChartFrom, memberChartTo, memberChartGranularity])

  async function loadData() {
    try {
      if (isMemberPortal && memberId) {
        const [profile, loans, monthly] = await Promise.all([
          api.getMemberProfile(memberId).catch(() => null),
          api.getLoansByMember(memberId).catch(() => []),
          api.getMemberMonthlySavings(memberId, {
            fromDate: memberChartFrom, toDate: memberChartTo, granularity: memberChartGranularity
          }).catch(() => [])
        ])
        setMemberProfile(profile)
        setMemberLoans(loans || [])
        setMemberMonthly(monthly || [])
      } else {
        const data = await api.getDashboardSummary({
          fromDate: chartFrom, toDate: chartTo, granularity: chartGranularity
        })
        setSummary(data)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Loading dashboard...</div>
  }

  if (error) {
    return (
      <div>
        <div className="page-header"><h2>Dashboard</h2></div>
        <div className="error-message">{error}</div>
      </div>
    )
  }

  if (isMemberPortal) {
    const profile = memberProfile || {}
    const member = profile.member || {}
    const accounts = profile.accounts || []
    const loans = memberLoans || []
    const activeLoans = loans.filter(l => l.status === 'DISBURSED' || l.status === 'IN_ARREARS')
    const totalOutstanding = activeLoans.reduce(
      (sum, l) => sum + Number(l.outstandingPrincipal || 0) + Number(l.outstandingInterest || 0), 0
    )
    const totalPaid = loans.reduce((sum, l) => sum + Number(l.totalPaid || 0), 0)
    const collateral = profile.collateral || []

    return (
      <div>
        <div className="page-header">
          <div>
            <h2>My Financial Standing</h2>
            <p className="page-subtitle">
              Personal savings, shares, loans and collateral — your private cooperative record
            </p>
          </div>
          <span className="count-chip">Member portal</span>
        </div>

        <div className="financial-grid">
          <div className="financial-card financial-green">
            <span className="financial-label">Total Savings</span>
            <span className="financial-value">{formatMoney(profile.totalSavings)}</span>
            <span className="financial-sub">{accounts.length} account{accounts.length === 1 ? '' : 's'}</span>
          </div>
          <div className="financial-card financial-blue">
            <span className="financial-label">Share Capital</span>
            <span className="financial-value">{formatMoney(profile.totalShareCapital)}</span>
            <span className="financial-sub">Cooperative equity</span>
          </div>
          <div className="financial-card financial-orange">
            <span className="financial-label">Loan Outstanding</span>
            <span className="financial-value">{formatMoney(totalOutstanding)}</span>
            <span className="financial-sub">{activeLoans.length} active loan{activeLoans.length === 1 ? '' : 's'}</span>
          </div>
          <div className="financial-card financial-purple">
            <span className="financial-label">Total Repaid</span>
            <span className="financial-value">{formatMoney(totalPaid)}</span>
            <span className="financial-sub">Across all loans</span>
          </div>
        </div>

        <div className="card">
          <div className="card-heading">
            <div>
              <h3>My Savings Progress</h3>
              <p className="card-subtitle">
                Opening, peak, withdrawal trough and closing balance for your savings over the last 12 months
              </p>
            </div>
            <span className="chart-badge">OHLC · Personal</span>
          </div>
          <div className="chart-timeframe-bar">
            <select
              className="filter-select"
              value={memberChartGranularity}
              onChange={e => setMemberChartGranularity(e.target.value)}
            >
              <option value="DAILY">Daily</option>
              <option value="MONTHLY">Monthly</option>
              <option value="YEARLY">Yearly</option>
            </select>
            <input
              type="date"
              className="search-input"
              value={memberChartFrom}
              onChange={e => setMemberChartFrom(e.target.value)}
            />
            <input
              type="date"
              className="search-input"
              value={memberChartTo}
              onChange={e => setMemberChartTo(e.target.value)}
            />
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => { setMemberChartFrom(''); setMemberChartTo(''); setMemberChartGranularity('MONTHLY') }}
            >
              Reset
            </button>
          </div>
          <div className="chart-body chart-body-candlestick">
            <CandlestickChart points={memberMonthly} dark={dark} />
          </div>
        </div>

        <div className="card">
          <div className="card-heading">
            <div>
              <h3>My Accounts</h3>
              <p className="card-subtitle">Savings and deposit accounts in your name</p>
            </div>
          </div>
          {accounts.length === 0 ? (
            <p className="empty-text">No accounts on file yet.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Account No.</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map(acc => (
                    <tr key={acc.accountId}>
                      <td className="mono-cell">{acc.accountNumber}</td>
                      <td>{acc.accountType}</td>
                      <td><span className={badgeClass(acc.status)}>{acc.status}</span></td>
                      <td className="numeric-cell"><strong>{formatMoney(acc.balance)}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {loans.length > 0 && (
          <div className="card">
            <div className="card-heading">
              <div>
                <h3>My Loans</h3>
                <p className="card-subtitle">Credit facilities and repayment progress</p>
              </div>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Loan No.</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Outstanding</th>
                    <th>Total Paid</th>
                  </tr>
                </thead>
                <tbody>
                  {loans.map(loan => (
                    <tr key={loan.loanId}>
                      <td className="mono-cell">{loan.loanNumber}</td>
                      <td className="numeric-cell">{formatMoney(loan.amount)}</td>
                      <td><span className={badgeClass(loan.status)}>{loan.status}</span></td>
                      <td className="numeric-cell">
                        {formatMoney(Number(loan.outstandingPrincipal || 0) + Number(loan.outstandingInterest || 0))}
                      </td>
                      <td className="numeric-cell">{formatMoney(loan.totalPaid)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {collateral.length > 0 && (
          <div className="card">
            <div className="card-heading">
              <div>
                <h3>My Collateral</h3>
                <p className="card-subtitle">Registered securities against your facilities</p>
              </div>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Asset</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Value (UGX)</th>
                  </tr>
                </thead>
                <tbody>
                  {collateral.map(item => (
                    <tr key={item.collateralId}>
                      <td>{item.description || item.titleNumber || '—'}</td>
                      <td>{item.assetType}</td>
                      <td><span className={badgeClass(item.status)}>{item.status}</span></td>
                      <td className="numeric-cell">{formatMoney(item.estimatedValue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div className="info-banner">
          Member portal — view only. Deposits, withdrawals, and loan applications are initiated
          through your SACCO officer. Contact the branch for any corrections.
        </div>
      </div>
    )
  }

  const data = summary || {}
  const monthly = data.monthlySavings || []
  const statusBreakdown = data.accountStatusBreakdown || {}
  const textColor = dark ? '#e0e0e0' : '#555'

  const doughnutLabels = Object.keys(statusBreakdown)
  const doughnutData = {
    labels: doughnutLabels.length ? doughnutLabels : ['No accounts yet'],
    datasets: [
      {
        data: doughnutLabels.length ? doughnutLabels.map(key => statusBreakdown[key]) : [1],
        backgroundColor: doughnutLabels.length
          ? doughnutLabels.map(key => STATUS_COLORS[key] || '#9e9e9e')
          : ['#e0e0e0'],
        borderWidth: 0,
        hoverOffset: 6
      }
    ]
  }

  const doughnutOptions = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '68%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: { color: textColor, boxWidth: 12, usePointStyle: true, padding: 16 }
      }
    }
  }

  const activePercent = data.totalMembers
    ? Math.round((data.activeMembers / data.totalMembers) * 100)
    : 0
  const pendingPercent = data.totalMembers
    ? Math.round((data.pendingMembers / data.totalMembers) * 100)
    : 0
  const suspendedPercent = data.totalMembers
    ? Math.round((data.suspendedMembers / data.totalMembers) * 100)
    : 0

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Dashboard</h2>
          <p className="page-subtitle">SACCO performance, activity and financial health at a glance</p>
        </div>
        <span className="today-chip">
          {new Date().toLocaleDateString(undefined, {
            weekday: 'short',
            day: 'numeric',
            month: 'short',
            year: 'numeric'
          })}
        </span>
      </div>

      <div className="quick-actions">
        <button className="quick-action-btn" onClick={() => navigate('/members?new=1')}>
          <span className="quick-action-icon">+</span> Register Member
        </button>
        <button className="quick-action-btn" onClick={() => navigate('/accounts?new=1')}>
          <span className="quick-action-icon">+</span> Open Account
        </button>
        <button className="quick-action-btn" onClick={() => navigate('/savings?new=1')}>
          <span className="quick-action-icon">+</span> Post Savings
        </button>
        <button className="quick-action-btn" onClick={() => navigate('/shares?new=1')}>
          <span className="quick-action-icon">+</span> Record Share
        </button>
        <button className="quick-action-btn" onClick={() => navigate('/loans?new=1')}>
          <span className="quick-action-icon">+</span> New Loan
        </button>
        <button className="quick-action-btn" onClick={() => navigate('/collateral?new=1')}>
          <span className="quick-action-icon">+</span> Register Collateral
        </button>
      </div>

      <div className="financial-grid">
        <div className="financial-card financial-orange">
          <span className="financial-label">Total Savings Portfolio</span>
          <span className="financial-value">{formatMoney(data.totalSavings)}</span>
          <span className="financial-hint">Across all savings accounts</span>
        </div>
        <div className="financial-card financial-green">
          <span className="financial-label">Total Share Capital</span>
          <span className="financial-value">{formatMoney(data.totalShareCapital)}</span>
          <span className="financial-hint">Net completed share transactions</span>
        </div>
        <div className="financial-card financial-red">
          <span className="financial-label">Loan Portfolio Outstanding</span>
          <span className="financial-value">{formatMoney(data.totalLoansOutstanding)}</span>
          <span className="financial-hint">
            {data.activeLoans || 0} active loan{data.activeLoans === 1 ? '' : 's'}
            {data.loansInArrears > 0 ? ` · ${data.loansInArrears} in arrears` : ''}
          </span>
        </div>
        <div className="financial-card financial-teal">
          <span className="financial-label">Collateral Held</span>
          <span className="financial-value">{formatMoney(data.collateralHeldValue)}</span>
          <span className="financial-hint">{data.collateralHeldCount || 0} asset{data.collateralHeldCount === 1 ? '' : 's'} in SACCO custody</span>
        </div>
        <div className="financial-card financial-blue">
          <span className="financial-label">Total Members</span>
          <span className="financial-value">{data.totalMembers || 0}</span>
          <span className="financial-hint">{data.activeMembers || 0} active members</span>
        </div>
        <div className="financial-card financial-purple">
          <span className="financial-label">Active Accounts</span>
          <span className="financial-value">{data.activeAccounts || 0}</span>
          <span className="financial-hint">of {data.totalAccounts || 0} total accounts</span>
        </div>
      </div>

      <div className="chart-grid">
        <div className="card chart-card chart-card-wide">
          <div className="card-heading">
            <div>
              <h3>Monthly Savings Growth</h3>
              <p className="card-subtitle">
                OHLC candlestick view of opening, peak, withdrawal trough and closing savings balances
                over the last 12 months. Y-axis auto-scales to the portfolio's range.
              </p>
            </div>
            <span className="chart-badge">OHLC · Auto</span>
          </div>
          <div className="chart-timeframe-bar">
            <select
              className="filter-select"
              value={chartGranularity}
              onChange={e => setChartGranularity(e.target.value)}
            >
              <option value="DAILY">Daily</option>
              <option value="MONTHLY">Monthly</option>
              <option value="YEARLY">Yearly</option>
            </select>
            <input
              type="date"
              className="search-input"
              value={chartFrom}
              onChange={e => setChartFrom(e.target.value)}
              placeholder="From"
            />
            <input
              type="date"
              className="search-input"
              value={chartTo}
              onChange={e => setChartTo(e.target.value)}
              placeholder="To"
            />
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => { setChartFrom(''); setChartTo(''); setChartGranularity('MONTHLY') }}
            >
              Reset
            </button>
          </div>
          <div className="chart-body chart-body-candlestick">
            <CandlestickChart points={monthly} dark={dark} />
          </div>
        </div>

        <div className="card chart-card">
          <div className="card-heading">
            <div>
              <h3>Account Status Breakdown</h3>
              <p className="card-subtitle">Active vs. inactive accounts</p>
            </div>
          </div>
          <div className="chart-body chart-body-doughnut">
            <Doughnut data={doughnutData} options={doughnutOptions} />
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Recent System Activity</h3>
              <p className="card-subtitle">Audit trail of the latest actions</p>
            </div>
          </div>
          {(data.recentActivities || []).length === 0 ? (
            <p className="empty-text">No activity recorded yet.</p>
          ) : (
            <div className="activity-feed">
              {data.recentActivities.map(event => (
                <div className="activity-item" key={event.eventId}>
                  <span className="activity-dot" />
                  <div className="activity-content">
                    <div className="activity-action">
                      {ACTION_LABELS[event.action] || event.action}
                    </div>
                    <div className="activity-meta">
                      {event.entityType} #{event.entityId} ·{' '}
                      {new Date(event.timestamp).toLocaleString()}
                    </div>
                    {event.reason && <div className="activity-reason">{event.reason}</div>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Membership Overview</h3>
              <p className="card-subtitle">Member status distribution</p>
            </div>
          </div>
          <div className="progress-list">
            <div className="progress-row">
              <div className="progress-label"><span>Active</span><strong>{data.activeMembers || 0}</strong></div>
              <div className="progress-track"><div className="progress-fill progress-green" style={{ width: `${activePercent}%` }} /></div>
            </div>
            <div className="progress-row">
              <div className="progress-label"><span>Pending</span><strong>{data.pendingMembers || 0}</strong></div>
              <div className="progress-track"><div className="progress-fill progress-orange" style={{ width: `${pendingPercent}%` }} /></div>
            </div>
            <div className="progress-row">
              <div className="progress-label"><span>Suspended</span><strong>{data.suspendedMembers || 0}</strong></div>
              <div className="progress-track"><div className="progress-fill progress-red" style={{ width: `${suspendedPercent}%` }} /></div>
            </div>
          </div>

          <div className="mini-stat-row">
            <div className="mini-stat">
              <span className="mini-stat-label">Pending KYC</span>
              <span className="mini-stat-value">{data.pendingMembers || 0}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-stat-label">Inactive Accounts</span>
              <span className="mini-stat-value">{data.inactiveAccounts || 0}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Recent Members</h3>
              <p className="card-subtitle">Latest registrations</p>
            </div>
            <button className="link-btn" onClick={() => navigate('/members')}>View all</button>
          </div>
          {(data.recentMembers || []).length === 0 ? (
            <p className="empty-text">No members registered yet.</p>
          ) : (
            <table>
              <thead><tr><th>Member No</th><th>Name</th><th>Status</th></tr></thead>
              <tbody>
                {data.recentMembers.map(member => (
                  <tr key={member.memberId}>
                    <td>{member.membershipNo}</td>
                    <td>{member.firstName} {member.lastName}</td>
                    <td><span className={statusBadge(member.status)}>{member.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Recent Accounts</h3>
              <p className="card-subtitle">Newly opened accounts</p>
            </div>
            <button className="link-btn" onClick={() => navigate('/accounts')}>View all</button>
          </div>
          {(data.recentAccounts || []).length === 0 ? (
            <p className="empty-text">No accounts opened yet.</p>
          ) : (
            <table>
              <thead><tr><th>Account No</th><th>Type</th><th>Status</th></tr></thead>
              <tbody>
                {data.recentAccounts.map(account => (
                  <tr key={account.accountId}>
                    <td>{account.accountNumber}</td>
                    <td>{account.accountType}</td>
                    <td><span className={statusBadge(account.status)}>{account.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}

export default Dashboard
