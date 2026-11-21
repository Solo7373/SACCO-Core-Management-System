import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../services/api'
import Receipt from '../components/Receipt'

const TYPE_LABELS = {
  DEPOSIT: 'Deposit',
  WITHDRAWAL: 'Withdrawal',
  TRANSFER_IN: 'Transfer In',
  TRANSFER_OUT: 'Transfer Out',
  INTEREST: 'Interest',
  FEE: 'Fee'
}

const TYPE_BADGES = {
  DEPOSIT: 'success',
  WITHDRAWAL: 'warning',
  TRANSFER_IN: 'info',
  TRANSFER_OUT: 'warning',
  INTEREST: 'success',
  FEE: 'danger'
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

function emptyForm(mode = 'DEPOSIT') {
  return {
    accountId: '',
    toAccountId: '',
    transactionType: mode,
    amount: '',
    currency: 'UGX',
    channel: mode === 'TRANSFER' ? 'INTERNAL' : 'CASH',
    description: '',
    authorizedBy: '',
    approvalReference: ''
  }
}

function Savings() {
  const [searchParams] = useSearchParams()
  const role = localStorage.getItem('role') || ''
  const canPostSavings = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER'].includes(role)
  const canReverseSavings = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER'].includes(role)
  const [accounts, setAccounts] = useState([])
  const [transactions, setTransactions] = useState([])
  const [balances, setBalances] = useState({})
  const [threshold, setThreshold] = useState(100000)
  const [showForm, setShowForm] = useState(searchParams.get('new') === '1')
  const [mode, setMode] = useState('DEPOSIT')
  const [form, setForm] = useState(emptyForm('DEPOSIT'))
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [receipt, setReceipt] = useState(null)
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState('ALL')

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    try {
      const [accData, txData, thresholdData] = await Promise.all([
        api.getAccounts().catch(() => []),
        api.getAllTransactions().catch(() => []),
        api.getWithdrawalThreshold().catch(() => 100000)
      ])
      const activeAccounts = (accData || []).filter(account => account.status === 'ACTIVE')
      setAccounts(activeAccounts)
      setTransactions(txData || [])
      setThreshold(Number(thresholdData || 100000))

      const entries = await Promise.all(activeAccounts.map(async account => {
        try {
          return [account.accountId, await api.getBalance(account.accountId)]
        } catch {
          return [account.accountId, 0]
        }
      }))
      setBalances(Object.fromEntries(entries))
    } catch (err) {
      setError(err.message)
    }
  }

  function selectMode(nextMode) {
    setMode(nextMode)
    setForm(emptyForm(nextMode))
    setShowForm(true)
    setError(null)
    setSuccess(null)
  }

  function updateField(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  const selectedAccount = accounts.find(account => String(account.accountId) === String(form.accountId))
  const selectedBalance = selectedAccount ? Number(balances[selectedAccount.accountId] || 0) : 0
  const isDebit = mode === 'WITHDRAWAL' || mode === 'TRANSFER'
  const needsApproval = isDebit && Number(form.amount || 0) >= threshold

  const filteredTransactions = useMemo(() => {
    const query = search.trim().toLowerCase()
    return transactions.filter(tx => {
      const account = accounts.find(item => item.accountId === tx.accountId)
      const haystack = [
        tx.reference,
        tx.externalReference,
        tx.description,
        account?.accountNumber,
        account?.primaryMemberName,
        account?.membershipNo,
        TYPE_LABELS[tx.transactionType]
      ].filter(Boolean).join(' ').toLowerCase()
      return (!query || haystack.includes(query))
        && (typeFilter === 'ALL' || tx.transactionType === typeFilter)
    })
  }, [transactions, accounts, search, typeFilter])

  const summary = useMemo(() => ({
    deposits: transactions
      .filter(tx => tx.status === 'COMPLETED' && ['DEPOSIT', 'TRANSFER_IN', 'INTEREST'].includes(tx.transactionType))
      .reduce((sum, tx) => sum + Number(tx.netAmount ?? tx.amount ?? 0), 0),
    withdrawals: transactions
      .filter(tx => tx.status === 'COMPLETED' && ['WITHDRAWAL', 'TRANSFER_OUT', 'FEE'].includes(tx.transactionType))
      .reduce((sum, tx) => sum + Number(tx.amount || 0), 0),
    total: Object.values(balances).reduce((sum, value) => sum + Number(value || 0), 0)
  }), [transactions, balances])

  function validate() {
    if (!form.accountId) return 'Select the account to transact.'
    if (mode === 'TRANSFER' && !form.toAccountId) return 'Select the destination account.'
    if (mode === 'TRANSFER' && form.accountId === form.toAccountId) return 'Source and destination accounts must be different.'
    if (!form.amount || Number(form.amount) <= 0) return 'Enter a valid amount.'
    if (isDebit && Number(form.amount) > selectedBalance) {
      return `Insufficient balance. Available: ${formatMoney(selectedBalance)}`
    }
    if (needsApproval && !form.authorizedBy.trim()) {
      return `Supervisor authorization is required for ${mode === 'TRANSFER' ? 'transfers' : 'withdrawals'} of ${formatMoney(threshold)} and above.`
    }
    return null
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const message = validate()
    if (message) {
      setError(message)
      return
    }

    setSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      if (mode === 'TRANSFER') {
        const result = await api.postTransfer({
          fromAccountId: Number(form.accountId),
          toAccountId: Number(form.toAccountId),
          amount: Number(form.amount),
          channel: form.channel,
          description: form.description.trim(),
          authorizedBy: form.authorizedBy.trim(),
          approvalReference: form.approvalReference.trim()
        })
        setSuccess(`Transfer ${result.reference} posted successfully.`)
        setReceipt({ kind: 'TRANSFER', data: result })
      } else {
        const tx = await api.postTransaction({
          accountId: Number(form.accountId),
          transactionType: mode,
          amount: Number(form.amount),
          currency: selectedAccount?.currency || 'UGX',
          channel: form.channel,
          description: form.description.trim(),
          authorizedBy: form.authorizedBy.trim(),
          approvalReference: form.approvalReference.trim()
        })
        setSuccess(`${TYPE_LABELS[tx.transactionType]} ${tx.reference} posted successfully.`)
        setReceipt({ kind: 'TRANSACTION', data: tx })
      }
      setShowForm(false)
      setForm(emptyForm(mode))
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleReverse(tx) {
    const reason = window.prompt(`Reason for reversing ${tx.reference}:`)
    if (!reason) return
    setError(null)
    try {
      await api.reverseTransaction(tx.transactionId, reason)
      setSuccess(`Transaction ${tx.reference} reversed.`)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Savings & Cash Flow</h2>
          <p className="page-subtitle">Deposits, withdrawals, transfers and member receipts</p>
        </div>
        <div className="header-actions">
          <span className="count-chip">{accounts.length} active account{accounts.length === 1 ? '' : 's'}</span>
          {canPostSavings && (
            <button className="btn btn-primary" onClick={() => selectMode('DEPOSIT')}>+ Post Transaction</button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collateral-summary savings-summary">
        <div className="collateral-summary-card">
          <span>Total Savings Balance</span>
          <strong>{formatMoney(summary.total)}</strong>
          <small>Across all active accounts</small>
        </div>
        <div className="collateral-summary-card">
          <span>Total Deposits</span>
          <strong>{formatMoney(summary.deposits)}</strong>
          <small>Completed credits</small>
        </div>
        <div className="collateral-summary-card">
          <span>Total Withdrawals</span>
          <strong>{formatMoney(summary.withdrawals)}</strong>
          <small>Completed debits</small>
        </div>
        <div className="collateral-summary-card">
          <span>Approval Threshold</span>
          <strong>{formatMoney(threshold)}</strong>
          <small>Supervisor sign-off required at or above</small>
        </div>
      </div>

      {showForm && canPostSavings && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Post Savings Transaction</h3>
              <p className="card-subtitle">Every transaction receives a unique receipt reference</p>
            </div>
            <button type="button" className="modal-close" onClick={() => setShowForm(false)}>×</button>
          </div>

          <div className="tabs">
            {[['DEPOSIT', 'Deposit'], ['WITHDRAWAL', 'Withdrawal'], ['TRANSFER', 'Transfer']].map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={`tab ${mode === value ? 'active' : ''}`}
                onClick={() => selectMode(value)}
              >
                {label}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit}>
            <div className="grid-2">
              <div className="form-group">
                <label>{mode === 'TRANSFER' ? 'From Account *' : 'Account *'}</label>
                <select value={form.accountId} onChange={e => updateField('accountId', e.target.value)} required>
                  <option value="">Select account...</option>
                  {accounts.map(account => (
                    <option key={account.accountId} value={account.accountId}>
                      {account.accountNumber} — {account.primaryMemberName || 'Unknown'} ({formatMoney(balances[account.accountId])})
                    </option>
                  ))}
                </select>
                {selectedAccount && (
                  <p className="field-hint">Available balance: {formatMoney(selectedBalance)}</p>
                )}
              </div>

              {mode === 'TRANSFER' ? (
                <div className="form-group">
                  <label>To Account *</label>
                  <select value={form.toAccountId} onChange={e => updateField('toAccountId', e.target.value)} required>
                    <option value="">Select destination account...</option>
                    {accounts.filter(account => String(account.accountId) !== String(form.accountId)).map(account => (
                      <option key={account.accountId} value={account.accountId}>
                        {account.accountNumber} — {account.primaryMemberName || 'Unknown'}
                      </option>
                    ))}
                  </select>
                </div>
              ) : (
                <div className="form-group">
                  <label>Amount (UGX) *</label>
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={form.amount}
                    onChange={e => updateField('amount', e.target.value)}
                    required
                  />
                </div>
              )}
            </div>

            {mode === 'TRANSFER' && (
              <div className="form-group">
                <label>Amount (UGX) *</label>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={form.amount}
                  onChange={e => updateField('amount', e.target.value)}
                  required
                />
              </div>
            )}

            <div className="form-group">
              <label>Description / Narration</label>
              <input
                value={form.description}
                onChange={e => updateField('description', e.target.value)}
                placeholder="e.g. Monthly savings deposit"
              />
            </div>

            <div className="form-group">
              <label>Channel *</label>
              <select value={form.channel} onChange={e => updateField('channel', e.target.value)}>
                {mode === 'TRANSFER' ? (
                  <>
                    <option value="INTERNAL">Internal transfer</option>
                    <option value="BANK">Bank transfer</option>
                    <option value="CROSS_BANK">Cross-bank transfer</option>
                    <option value="MOBILE_MONEY">Mobile money payout</option>
                  </>
                ) : (
                  <>
                    <option value="CASH">Cash at counter</option>
                    <option value="BANK">Bank</option>
                    <option value="MOBILE_MONEY">Mobile money</option>
                  </>
                )}
              </select>
              <p className="field-hint">
                The channel is recorded for audit and reporting. No automatic charges are applied.
              </p>
            </div>

            {needsApproval && (
              <div className="approval-panel">
                <h4>Supervisor Authorization</h4>
                <p className="field-hint">
                  This transaction is at or above {formatMoney(threshold)}. Enter the authorizing supervisor or manager.
                </p>
                <div className="grid-2">
                  <div className="form-group">
                    <label>Authorized By *</label>
                    <input
                      value={form.authorizedBy}
                      onChange={e => updateField('authorizedBy', e.target.value)}
                      placeholder="Supervisor / manager name"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Approval Reference</label>
                    <input
                      value={form.approvalReference}
                      onChange={e => updateField('approvalReference', e.target.value)}
                      placeholder="Optional approval note / reference"
                    />
                  </div>
                </div>
              </div>
            )}

            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Posting...' : mode === 'TRANSFER' ? 'Post Transfer' : `Post ${TYPE_LABELS[mode]}`}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Transaction History</h3>
            <p className="card-subtitle">{filteredTransactions.length} transaction{filteredTransactions.length === 1 ? '' : 's'} found</p>
          </div>
        </div>

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search reference, account, member or narration..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="filter-select" value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
            <option value="ALL">All transaction types</option>
            {Object.entries(TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {filteredTransactions.length === 0 ? (
          <p className="empty-text">
            {transactions.length === 0
              ? 'No transactions yet. Open and activate an account, then post a deposit.'
              : 'No transactions match your search.'}
          </p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Account / Member</th>
                  <th>Type</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Posted</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filteredTransactions.map(tx => {
                  const account = accounts.find(item => item.accountId === tx.accountId)
                  return (
                    <tr key={tx.transactionId}>
                      <td>
                        <strong>{tx.reference}</strong>
                        {tx.externalReference && <span className="cell-sub">{tx.externalReference}</span>}
                      </td>
                      <td>
                        <strong>{account?.accountNumber || `Account #${tx.accountId}`}</strong>
                        <span className="cell-sub">{account?.primaryMemberName || '—'}</span>
                      </td>
                      <td>
                        <span className={`badge badge-${TYPE_BADGES[tx.transactionType] || 'default'}`}>
                          {TYPE_LABELS[tx.transactionType] || tx.transactionType}
                        </span>
                      </td>
                      <td>{formatMoney(tx.amount)}</td>
                      <td>
                        <span className={`badge badge-${tx.status === 'COMPLETED' ? 'success' : tx.status === 'REVERSED' ? 'danger' : 'warning'}`}>
                          {tx.status}
                        </span>
                      </td>
                      <td>{formatDateTime(tx.createdAt)}</td>
                      <td>
                        <div className="doc-actions">
                          <button type="button" className="link-btn" onClick={() => setReceipt({ kind: 'TRANSACTION', data: tx })}>
                            Receipt
                          </button>
                          {tx.status === 'COMPLETED' && canReverseSavings && (
                            <button type="button" className="link-btn danger-link" onClick={() => handleReverse(tx)}>
                              Reverse
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {receipt && (
        <Receipt receipt={receipt} accounts={accounts} onClose={() => setReceipt(null)} />
      )}
    </div>
  )
}

export default Savings
