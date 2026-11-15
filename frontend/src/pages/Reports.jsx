import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

function firstOfMonth() {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
}

function Reports() {
  const [tab, setTab] = useState('trial')
  const [asOf, setAsOf] = useState(today())
  const [fromDate, setFromDate] = useState(firstOfMonth())
  const [toDate, setToDate] = useState(today())
  const [trialBalance, setTrialBalance] = useState([])
  const [balanceSheet, setBalanceSheet] = useState(null)
  const [incomeExpenditure, setIncomeExpenditure] = useState(null)
  const [members, setMembers] = useState([])
  const [memberId, setMemberId] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [downloading, setDownloading] = useState(null)

  useEffect(() => {
    loadMembers()
    loadTrialBalance()
    loadBalanceSheet()
    loadIncomeExpenditure()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadMembers() {
    try {
      const data = await api.getMembers()
      setMembers(data || [])
    } catch (err) {
      setError(err.message)
    }
  }

  async function loadTrialBalance() {
    setLoading(true)
    try {
      const data = await api.getTrialBalanceReport(asOf)
      setTrialBalance(data || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadBalanceSheet() {
    setLoading(true)
    try {
      const data = await api.getBalanceSheet(asOf)
      setBalanceSheet(data)
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadIncomeExpenditure() {
    setLoading(true)
    try {
      const data = await api.getIncomeExpenditure(fromDate, toDate)
      setIncomeExpenditure(data)
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

  async function handleDownload(format) {
    if (!memberId) {
      setError('Select a member before downloading a statement.')
      return
    }
    setError(null)
    setDownloading(format)
    try {
      await api.downloadMemberStatement(memberId, format)
      flashSuccess(`Member statement downloaded as ${format.toUpperCase()}.`)
    } catch (err) {
      setError(err.message)
    } finally {
      setDownloading(null)
    }
  }

  const trialTotals = useMemo(() => {
    return trialBalance.reduce((totals, row) => ({
      debit: totals.debit + Number(row.totalDebit || 0),
      credit: totals.credit + Number(row.totalCredit || 0)
    }), { debit: 0, credit: 0 })
  }, [trialBalance])

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Financial Reports</h2>
          <p className="page-subtitle">Real-time trial balance, balance sheet, income & expenditure and member statements</p>
        </div>
        <div className="header-actions">
          <span className="count-chip">{new Date().toLocaleDateString()}</span>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="tabs">
        <button type="button" className={`tab ${tab === 'trial' ? 'active' : ''}`} onClick={() => setTab('trial')}>Trial Balance</button>
        <button type="button" className={`tab ${tab === 'balance' ? 'active' : ''}`} onClick={() => setTab('balance')}>Balance Sheet</button>
        <button type="button" className={`tab ${tab === 'income' ? 'active' : ''}`} onClick={() => setTab('income')}>Income & Expenditure</button>
        <button type="button" className={`tab ${tab === 'member' ? 'active' : ''}`} onClick={() => setTab('member')}>Member Statement</button>
      </div>

      {tab === 'trial' && (
        <div className="card tab-panel">
          <div className="card-heading">
            <div>
              <h3>Trial Balance</h3>
              <p className="card-subtitle">As of {asOf}</p>
            </div>
            <div className="toolbar" style={{ margin: 0 }}>
              <input type="date" value={asOf} onChange={e => setAsOf(e.target.value)} />
              <button className="btn btn-primary" onClick={loadTrialBalance} disabled={loading}>
                {loading ? 'Generating...' : 'Generate'}
              </button>
            </div>
          </div>
          {loading && trialBalance.length === 0 ? (
            <div className="loading">Generating trial balance...</div>
          ) : trialBalance.length === 0 ? (
            <p className="empty-text">No posted balances yet.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Account</th>
                    <th>Category</th>
                    <th className="text-right">Debit</th>
                    <th className="text-right">Credit</th>
                  </tr>
                </thead>
                <tbody>
                  {trialBalance.map(row => (
                    <tr key={`${row.accountCode}-${row.accountName}`}>
                      <td><strong>{row.accountCode}</strong></td>
                      <td>{row.accountName}</td>
                      <td><span className="badge badge-default">{row.category}</span></td>
                      <td className="text-right">{row.totalDebit > 0 ? formatMoney(row.totalDebit) : '—'}</td>
                      <td className="text-right">{row.totalCredit > 0 ? formatMoney(row.totalCredit) : '—'}</td>
                    </tr>
                  ))}
                  <tr className="total-row">
                    <td colSpan="3"><strong>Totals</strong></td>
                    <td className="text-right"><strong>{formatMoney(trialTotals.debit)}</strong></td>
                    <td className="text-right"><strong>{formatMoney(trialTotals.credit)}</strong></td>
                  </tr>
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === 'balance' && (
        <div className="card tab-panel">
          <div className="card-heading">
            <div>
              <h3>Balance Sheet</h3>
              <p className="card-subtitle">As of {balanceSheet?.asOfDate || asOf}</p>
            </div>
            <div className="toolbar" style={{ margin: 0 }}>
              <input type="date" value={asOf} onChange={e => setAsOf(e.target.value)} />
              <button className="btn btn-primary" onClick={loadBalanceSheet} disabled={loading}>
                {loading ? 'Generating...' : 'Generate'}
              </button>
            </div>
          </div>
          {!balanceSheet ? (
            <div className="loading">Generating balance sheet...</div>
          ) : (
            <div className="report-grid">
              <div className="report-section">
                <h4 className="section-title">Assets</h4>
                <table>
                  <tbody>
                    {(balanceSheet.assets || []).map(line => (
                      <tr key={line.accountCode}>
                        <td>{line.accountName}</td>
                        <td className="text-right">{formatMoney(line.amount)}</td>
                      </tr>
                    ))}
                    <tr className="total-row">
                      <td><strong>Total Assets</strong></td>
                      <td className="text-right"><strong>{formatMoney(balanceSheet.totalAssets)}</strong></td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div className="report-section">
                <h4 className="section-title">Liabilities</h4>
                <table>
                  <tbody>
                    {(balanceSheet.liabilities || []).map(line => (
                      <tr key={line.accountCode}>
                        <td>{line.accountName}</td>
                        <td className="text-right">{formatMoney(line.amount)}</td>
                      </tr>
                    ))}
                    <tr className="total-row">
                      <td><strong>Total Liabilities</strong></td>
                      <td className="text-right"><strong>{formatMoney(balanceSheet.totalLiabilities)}</strong></td>
                    </tr>
                  </tbody>
                </table>

                <h4 className="section-title">Equity</h4>
                <table>
                  <tbody>
                    {(balanceSheet.equity || []).map(line => (
                      <tr key={line.accountCode}>
                        <td>{line.accountName}</td>
                        <td className="text-right">{formatMoney(line.amount)}</td>
                      </tr>
                    ))}
                    <tr className="total-row">
                      <td><strong>Total Equity</strong></td>
                      <td className="text-right"><strong>{formatMoney(balanceSheet.totalEquity)}</strong></td>
                    </tr>
                  </tbody>
                </table>

                <div className={`balance-check ${balanceSheet.balanced ? 'balanced' : 'unbalanced'}`}>
                  <div>
                    <span>Total Liabilities & Equity</span>
                    <strong>{formatMoney(balanceSheet.totalLiabilitiesAndEquity)}</strong>
                  </div>
                  <span className={`badge badge-${balanceSheet.balanced ? 'success' : 'danger'}`}>
                    {balanceSheet.balanced ? 'Balanced' : 'Out of balance'}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {tab === 'income' && (
        <div className="card tab-panel">
          <div className="card-heading">
            <div>
              <h3>Income & Expenditure</h3>
              <p className="card-subtitle">
                {incomeExpenditure?.fromDate || fromDate} to {incomeExpenditure?.toDate || toDate}
              </p>
            </div>
            <div className="toolbar" style={{ margin: 0 }}>
              <input type="date" value={fromDate} onChange={e => setFromDate(e.target.value)} />
              <input type="date" value={toDate} onChange={e => setToDate(e.target.value)} />
              <button className="btn btn-primary" onClick={loadIncomeExpenditure} disabled={loading}>
                {loading ? 'Generating...' : 'Generate'}
              </button>
            </div>
          </div>
          {!incomeExpenditure ? (
            <div className="loading">Generating statement...</div>
          ) : (
            <div className="report-grid">
              <div className="report-section">
                <h4 className="section-title">Income</h4>
                <table>
                  <tbody>
                    {(incomeExpenditure.income || []).map(line => (
                      <tr key={line.accountCode}>
                        <td>{line.accountName}</td>
                        <td className="text-right">{formatMoney(line.amount)}</td>
                      </tr>
                    ))}
                    <tr className="total-row">
                      <td><strong>Total Income</strong></td>
                      <td className="text-right"><strong>{formatMoney(incomeExpenditure.totalIncome)}</strong></td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div className="report-section">
                <h4 className="section-title">Expenditure</h4>
                <table>
                  <tbody>
                    {(incomeExpenditure.expenses || []).length === 0 ? (
                      <tr><td className="empty-text">No expenses posted in this period.</td></tr>
                    ) : (
                      (incomeExpenditure.expenses || []).map(line => (
                        <tr key={line.accountCode}>
                          <td>{line.accountName}</td>
                          <td className="text-right">{formatMoney(line.amount)}</td>
                        </tr>
                      ))
                    )}
                    <tr className="total-row">
                      <td><strong>Total Expenditure</strong></td>
                      <td className="text-right"><strong>{formatMoney(incomeExpenditure.totalExpenses)}</strong></td>
                    </tr>
                  </tbody>
                </table>

                <div className={`balance-check ${incomeExpenditure.netSurplus >= 0 ? 'balanced' : 'unbalanced'}`}>
                  <div>
                    <span>Net Surplus / (Deficit)</span>
                    <strong>{formatMoney(incomeExpenditure.netSurplus)}</strong>
                  </div>
                  <span className={`badge badge-${incomeExpenditure.netSurplus >= 0 ? 'success' : 'danger'}`}>
                    {incomeExpenditure.netSurplus >= 0 ? 'Surplus' : 'Deficit'}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {tab === 'member' && (
        <div className="card tab-panel">
          <div className="card-heading">
            <div>
              <h3>Member Statement</h3>
              <p className="card-subtitle">Download a full financial statement with accounts, savings, shares, loans and repayments</p>
            </div>
          </div>
          <div className="grid-2">
            <div className="form-group">
              <label>Select Member</label>
              <select value={memberId} onChange={e => setMemberId(e.target.value)}>
                <option value="">Select member...</option>
                {members.map(member => (
                  <option key={member.memberId} value={member.memberId}>
                    {member.firstName} {member.lastName} ({member.membershipNo})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Export Format</label>
              <div className="doc-actions" style={{ paddingTop: 8 }}>
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={!memberId || downloading !== null}
                  onClick={() => handleDownload('pdf')}
                >
                  {downloading === 'pdf' ? 'Preparing PDF...' : 'Download PDF'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={!memberId || downloading !== null}
                  onClick={() => handleDownload('csv')}
                >
                  {downloading === 'csv' ? 'Preparing CSV...' : 'Download CSV'}
                </button>
              </div>
            </div>
          </div>
          <p className="field-hint">
            The statement includes all linked accounts, completed savings and share transactions, loan balances and repayment history.
          </p>
        </div>
      )}
    </div>
  )
}

export default Reports
