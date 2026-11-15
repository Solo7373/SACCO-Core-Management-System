import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../services/api'

const TYPE_LABELS = {
  PURCHASE: 'Purchase',
  TRANSFER_IN: 'Transfer In',
  TRANSFER_OUT: 'Transfer Out',
  REDEMPTION: 'Redemption'
}

const TYPE_BADGES = {
  PURCHASE: 'success',
  TRANSFER_IN: 'info',
  TRANSFER_OUT: 'warning',
  REDEMPTION: 'danger'
}

const STATUS_BADGES = {
  PENDING: 'warning',
  COMPLETED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'default'
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : '—'
}

function emptyProductForm() {
  return { name: '', description: '', minShares: 1, pricePerShare: '' }
}

function emptyTxForm() {
  return {
    memberId: '',
    productId: '',
    quantity: '',
    amount: '',
    transactionType: 'PURCHASE',
    reference: ''
  }
}

function Shares() {
  const [searchParams] = useSearchParams()
  const role = localStorage.getItem('role') || 'MEMBER'
  const canManageProducts = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER'].includes(role)
  const canRecordTransaction = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER'].includes(role)
  const canApprove = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER'].includes(role)
  const [members, setMembers] = useState([])
  const [products, setProducts] = useState([])
  const [transactions, setTransactions] = useState([])
  const [portfolio, setPortfolio] = useState(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [showProductForm, setShowProductForm] = useState(false)
  const [showTxForm, setShowTxForm] = useState(searchParams.get('new') === '1' && canRecordTransaction)
  const [showDividends, setShowDividends] = useState(false)
  const [dividendRate, setDividendRate] = useState('15')
  const [dividends, setDividends] = useState(null)
  const [dividendLoading, setDividendLoading] = useState(false)
  const [memberSummary, setMemberSummary] = useState(null)
  const [summaryMemberId, setSummaryMemberId] = useState('')
  const [summaryLoading, setSummaryLoading] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [productForm, setProductForm] = useState(emptyProductForm())
  const [txForm, setTxForm] = useState(emptyTxForm())

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const [memberData, productData, txData, portfolioData] = await Promise.all([
        api.getMembers().catch(() => []),
        api.getActiveShareProducts().catch(() => []),
        api.getAllShareTransactions().catch(() => []),
        api.getSharePortfolioSummary().catch(() => null)
      ])
      setMembers(memberData || [])
      setProducts(productData || [])
      setTransactions(txData || [])
      setPortfolio(portfolioData)
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const memberMap = useMemo(() => {
    const map = {}
    members.forEach(member => {
      map[member.memberId] = `${member.firstName} ${member.lastName} (${member.membershipNo})`
    })
    return map
  }, [members])

  const productMap = useMemo(() => {
    const map = {}
    products.forEach(product => { map[product.productId] = product })
    return map
  }, [products])

  const activeMembers = useMemo(
    () => members.filter(member => member.status === 'ACTIVE'),
    [members]
  )

  const selectedProduct = productMap[Number(txForm.productId)]
  const computedAmount = selectedProduct && txForm.quantity
    ? Number(selectedProduct.pricePerShare) * Number(txForm.quantity)
    : 0

  const summary = useMemo(() => {
    const totalShares = portfolio?.totalShares ?? 0
    const totalValue = Number(portfolio?.totalValue ?? 0)
    const membersWithShares = portfolio?.totalMembersWithShares ?? 0
    const minimumRequired = products.reduce(
      (max, product) => Math.max(max, Number(product.minShares || 0)),
      0
    )
    return { totalShares, totalValue, membersWithShares, minimumRequired }
  }, [portfolio, products])

  const pendingCount = useMemo(
    () => transactions.filter(tx => tx.status === 'PENDING').length,
    [transactions]
  )

  const filteredTransactions = useMemo(() => {
    const query = search.trim().toLowerCase()
    return transactions.filter(tx => {
      const product = productMap[tx.productId]
      const haystack = [
        tx.reference,
        memberMap[tx.memberId],
        product?.name,
        TYPE_LABELS[tx.transactionType],
        tx.status
      ].filter(Boolean).join(' ').toLowerCase()
      return (!query || haystack.includes(query))
        && (typeFilter === 'ALL' || tx.transactionType === typeFilter)
        && (statusFilter === 'ALL' || tx.status === statusFilter)
    })
  }, [transactions, productMap, memberMap, search, typeFilter, statusFilter])

  function flashSuccess(message) {
    setSuccess(message)
    window.setTimeout(() => setSuccess(null), 4000)
  }

  function updateTxField(field, value) {
    setTxForm(prev => {
      const next = { ...prev, [field]: value }
      if (field === 'productId' || field === 'quantity') {
        const product = products.find(item => String(item.productId) === String(next.productId))
        if (product && next.quantity) {
          next.amount = String(Number(product.pricePerShare) * Number(next.quantity))
        } else {
          next.amount = ''
        }
      }
      return next
    })
  }

  async function handleCreateProduct(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const created = await api.createShareProduct({
        ...productForm,
        minShares: Number(productForm.minShares),
        pricePerShare: Number(productForm.pricePerShare)
      })
      flashSuccess(`Share product "${created.name}" created at ${formatMoney(created.pricePerShare)} per share.`)
      setShowProductForm(false)
      setProductForm(emptyProductForm())
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCreateTransaction(event) {
    event.preventDefault()
    setError(null)
    if (!txForm.memberId || !txForm.productId || !txForm.quantity) {
      setError('Select a member, product and quantity.')
      return
    }
    if (selectedProduct && Number(txForm.quantity) < Number(selectedProduct.minShares)) {
      setError(`This product requires a minimum of ${selectedProduct.minShares} shares.`)
      return
    }
    setSubmitting(true)
    try {
      const created = await api.recordShareTransaction({
        memberId: Number(txForm.memberId),
        productId: Number(txForm.productId),
        quantity: Number(txForm.quantity),
        amount: Number(txForm.amount),
        transactionType: txForm.transactionType,
        reference: txForm.reference.trim() || null
      })
      flashSuccess(`Share transaction ${created.reference || ''} recorded. Awaiting approval.`)
      setShowTxForm(false)
      setTxForm(emptyTxForm())
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleApprove(tx) {
    setError(null)
    try {
      await api.approveShareTransaction(tx.transactionId)
      flashSuccess(`Transaction ${tx.reference || tx.transactionId} approved and posted to member holdings.`)
      await loadData()
      if (summaryMemberId) {
        await loadMemberSummary(summaryMemberId)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleReject(tx) {
    if (!window.confirm(`Reject share transaction ${tx.reference || tx.transactionId}?`)) return
    setError(null)
    try {
      await api.rejectShareTransaction(tx.transactionId)
      flashSuccess(`Transaction ${tx.reference || tx.transactionId} rejected.`)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  async function loadMemberSummary(memberId) {
    if (!memberId) {
      setMemberSummary(null)
      return
    }
    setSummaryLoading(true)
    try {
      const data = await api.getShareSummary(memberId)
      setMemberSummary(data)
    } catch (err) {
      setError(err.message)
      setMemberSummary(null)
    } finally {
      setSummaryLoading(false)
    }
  }

  function handleSummaryMemberChange(value) {
    setSummaryMemberId(value)
    loadMemberSummary(value)
  }

  async function handleCalculateDividends(event) {
    event.preventDefault()
    setDividendLoading(true)
    setError(null)
    try {
      const result = await api.calculateDividends(Number(dividendRate))
      setDividends(result)
    } catch (err) {
      setError(err.message)
    } finally {
      setDividendLoading(false)
    }
  }

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Shares & Share Capital</h2>
          <p className="page-subtitle">Share products, member holdings, approvals and dividend planning</p>
        </div>
        <div className="header-actions">
          {pendingCount > 0 && <span className="count-chip">{pendingCount} pending approval{pendingCount === 1 ? '' : 's'}</span>}
          <button className="btn btn-secondary" onClick={() => { setShowDividends(true); setDividends(null) }}>
            Dividend Calculator
          </button>
          {canManageProducts && (
            <button className="btn btn-secondary" onClick={() => setShowProductForm(!showProductForm)}>
              {showProductForm ? 'Cancel Product' : '+ New Product'}
            </button>
          )}
          {canRecordTransaction && (
            <button className="btn btn-primary" onClick={() => setShowTxForm(!showTxForm)}>
              {showTxForm ? 'Cancel Transaction' : '+ Record Transaction'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collateral-summary shares-summary">
        <div className="collateral-summary-card">
          <span>Collective Share Capital</span>
          <strong>{formatMoney(summary.totalValue)}</strong>
          <small>SACCO total share pool</small>
        </div>
        <div className="collateral-summary-card">
          <span>Members With Shares</span>
          <strong>{summary.membersWithShares}</strong>
          <small>Members holding completed shares</small>
        </div>
        <div className="collateral-summary-card">
          <span>Total Shares</span>
          <strong>{Number(summary.totalShares).toLocaleString()}</strong>
          <small>Units issued across all products</small>
        </div>
        <div className="collateral-summary-card">
          <span>Minimum Membership</span>
          <strong>{summary.minimumRequired} shares</strong>
          <small>Highest product minimum requirement</small>
        </div>
      </div>

      {showProductForm && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Create Share Product</h3>
              <p className="card-subtitle">Define share pricing and the minimum shares for full membership</p>
            </div>
            <button type="button" className="modal-close" onClick={() => setShowProductForm(false)}>×</button>
          </div>
          <form onSubmit={handleCreateProduct}>
            <div className="grid-2">
              <div className="form-group">
                <label>Product Name *</label>
                <input
                  value={productForm.name}
                  onChange={e => setProductForm({ ...productForm, name: e.target.value })}
                  placeholder="e.g. Founding Member Shares"
                  required
                />
              </div>
              <div className="form-group">
                <label>Description</label>
                <input
                  value={productForm.description}
                  onChange={e => setProductForm({ ...productForm, description: e.target.value })}
                  placeholder="Optional description"
                />
              </div>
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>Price Per Share (UGX) *</label>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={productForm.pricePerShare}
                  onChange={e => setProductForm({ ...productForm, pricePerShare: e.target.value })}
                  placeholder="10000"
                  required
                />
              </div>
              <div className="form-group">
                <label>Minimum Shares *</label>
                <input
                  type="number"
                  min="1"
                  value={productForm.minShares}
                  onChange={e => setProductForm({ ...productForm, minShares: e.target.value })}
                  required
                />
                <p className="field-hint">Members must hold at least this many shares for full membership.</p>
              </div>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowProductForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Creating...' : 'Create Product'}
              </button>
            </div>
          </form>
        </div>
      )}

      {showTxForm && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Record Share Transaction</h3>
              <p className="card-subtitle">Purchases are recorded as pending until a manager approves them</p>
            </div>
            <button type="button" className="modal-close" onClick={() => setShowTxForm(false)}>×</button>
          </div>
          <form onSubmit={handleCreateTransaction}>
            <div className="grid-2">
              <div className="form-group">
                <label>Member *</label>
                <select value={txForm.memberId} onChange={e => updateTxField('memberId', e.target.value)} required>
                  <option value="">Select member...</option>
                  {activeMembers.map(member => (
                    <option key={member.memberId} value={member.memberId}>
                      {member.firstName} {member.lastName} ({member.membershipNo})
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Share Product *</label>
                <select value={txForm.productId} onChange={e => updateTxField('productId', e.target.value)} required>
                  <option value="">Select product...</option>
                  {products.map(product => (
                    <option key={product.productId} value={product.productId}>
                      {product.name} — {formatMoney(product.pricePerShare)}/share
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label>Quantity (shares) *</label>
                <input
                  type="number"
                  min="1"
                  value={txForm.quantity}
                  onChange={e => updateTxField('quantity', e.target.value)}
                  required
                />
                {selectedProduct && (
                  <p className="field-hint">Minimum for this product: {selectedProduct.minShares} shares</p>
                )}
              </div>
              <div className="form-group">
                <label>Amount (UGX)</label>
                <input value={computedAmount ? computedAmount.toLocaleString() : ''} readOnly placeholder="Auto-calculated" />
                <p className="field-hint">Quantity × price per share, calculated automatically.</p>
              </div>
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label>Transaction Type</label>
                <select value={txForm.transactionType} onChange={e => updateTxField('transactionType', e.target.value)}>
                  {Object.entries(TYPE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Reference (optional)</label>
                <input
                  value={txForm.reference}
                  onChange={e => updateTxField('reference', e.target.value)}
                  placeholder="Generated automatically if left blank"
                />
              </div>
            </div>

            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowTxForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Recording...' : 'Record Transaction'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="grid-2">
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Share Products</h3>
              <p className="card-subtitle">{products.length} active product{products.length === 1 ? '' : 's'}</p>
            </div>
          </div>
          {products.length === 0 ? (
            <p className="empty-text">No share products yet. Create one to start recording share purchases.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Min Shares</th>
                    <th>Price / Share</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map(product => (
                    <tr key={product.productId}>
                      <td>
                        <strong>{product.name}</strong>
                        {product.description && <span className="cell-sub">{product.description}</span>}
                      </td>
                      <td>{product.minShares}</td>
                      <td>{formatMoney(product.pricePerShare)}</td>
                      <td><span className="badge badge-success">{product.status}</span></td>
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
              <h3>Member Share Holdings</h3>
              <p className="card-subtitle">Live share count, value and membership status</p>
            </div>
          </div>
          <div className="form-group">
            <label>Select Member</label>
            <select value={summaryMemberId} onChange={e => handleSummaryMemberChange(e.target.value)}>
              <option value="">Select member...</option>
              {members.map(member => (
                <option key={member.memberId} value={member.memberId}>
                  {member.firstName} {member.lastName} ({member.membershipNo})
                </option>
              ))}
            </select>
          </div>

          {summaryLoading ? (
            <div className="loading">Loading member holdings...</div>
          ) : memberSummary ? (
            <>
              <div className="profile-stats">
                <div>
                  <span>Total Shares</span>
                  <strong>{Number(memberSummary.totalShares || 0).toLocaleString()}</strong>
                </div>
                <div>
                  <span>Share Value</span>
                  <strong>{formatMoney(memberSummary.totalValue)}</strong>
                </div>
                <div>
                  <span>Membership</span>
                  <strong>
                    <span className={`badge badge-${memberSummary.minimumSharesMet ? 'success' : 'warning'}`}>
                      {memberSummary.minimumSharesMet ? 'Full member' : 'Below minimum'}
                    </span>
                  </strong>
                </div>
              </div>

              {(memberSummary.holdings || []).length === 0 ? (
                <p className="empty-text">This member has no completed share holdings yet.</p>
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr><th>Product</th><th>Shares</th><th>Unit Price</th><th>Value</th></tr>
                    </thead>
                    <tbody>
                      {memberSummary.holdings.map(holding => (
                        <tr key={holding.productId}>
                          <td>{holding.productName}</td>
                          <td>{holding.quantity}</td>
                          <td>{formatMoney(holding.unitPrice)}</td>
                          <td>{formatMoney(holding.value)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          ) : (
            <p className="empty-text">Select a member to view their share holdings.</p>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Share Transaction Ledger</h3>
            <p className="card-subtitle">
              {filteredTransactions.length} transaction{filteredTransactions.length === 1 ? '' : 's'} found
            </p>
          </div>
        </div>

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search reference, member, product..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="filter-select" value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
            <option value="ALL">All transaction types</option>
            {Object.entries(TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <select className="filter-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="COMPLETED">Completed</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>

        {loading ? (
          <div className="loading">Loading share transactions...</div>
        ) : filteredTransactions.length === 0 ? (
          <p className="empty-text">
            {transactions.length === 0
              ? 'No share transactions yet. Record a purchase to build member share capital.'
              : 'No transactions match your search.'}
          </p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Member</th>
                  <th>Product</th>
                  <th>Qty</th>
                  <th>Amount</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Date</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filteredTransactions.map(tx => {
                  const product = productMap[tx.productId]
                  return (
                    <tr key={tx.transactionId}>
                      <td><strong>{tx.reference || `#${tx.transactionId}`}</strong></td>
                      <td>{memberMap[tx.memberId] || `Member #${tx.memberId}`}</td>
                      <td>{product?.name || `Product #${tx.productId}`}</td>
                      <td>{tx.quantity}</td>
                      <td>
                        {formatMoney(tx.amount)}
                        <span className="cell-sub">{formatMoney(tx.unitPrice)} / share</span>
                      </td>
                      <td>
                        <span className={`badge badge-${TYPE_BADGES[tx.transactionType] || 'default'}`}>
                          {TYPE_LABELS[tx.transactionType] || tx.transactionType}
                        </span>
                      </td>
                      <td>
                        <span className={`badge badge-${STATUS_BADGES[tx.status] || 'default'}`}>
                          {tx.status}
                        </span>
                      </td>
                      <td>{formatDate(tx.transactionDate || tx.createdAt)}</td>
                      <td>
                        {tx.status === 'PENDING' ? (
                          <div className="doc-actions">
                            <button type="button" className="link-btn" onClick={() => handleApprove(tx)}>Approve</button>
                            <button type="button" className="link-btn danger-link" onClick={() => handleReject(tx)}>Reject</button>
                          </div>
                        ) : (
                          <span className="cell-sub">
                            {tx.processedBy ? `By user #${tx.processedBy}` : '—'}
                          </span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showDividends && (
        <div className="modal-overlay" onClick={() => setShowDividends(false)}>
          <div className="modal receipt-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Dividend Calculator</h3>
              <button type="button" className="modal-close" onClick={() => setShowDividends(false)}>×</button>
            </div>
            <div className="modal-body">
              <p className="field-hint" style={{ marginBottom: 14 }}>
                Preview end-of-year dividends based on each member&apos;s completed share value.
              </p>
              <form onSubmit={handleCalculateDividends}>
                <div className="form-group">
                  <label>Dividend Rate (% of share value)</label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    value={dividendRate}
                    onChange={e => setDividendRate(e.target.value)}
                    required
                  />
                </div>
                <div className="form-actions" style={{ marginTop: 12, paddingTop: 0, borderTop: 'none' }}>
                  <button type="submit" className="btn btn-primary" disabled={dividendLoading}>
                    {dividendLoading ? 'Calculating...' : 'Calculate Dividends'}
                  </button>
                </div>
              </form>

              {dividends && (
                <>
                  <div className="dividend-total">
                    <span>Total Dividend Payable</span>
                    <strong>{formatMoney(dividends.totalDividend)}</strong>
                  </div>
                  <div className="info-grid">
                    <div className="info-row"><span>Rate</span><strong>{dividends.ratePercent}%</strong></div>
                    <div className="info-row"><span>Total Share Value</span><strong>{formatMoney(dividends.totalShareValue)}</strong></div>
                  </div>

                  {(dividends.members || []).length === 0 ? (
                    <p className="empty-text">No completed share holdings to distribute against.</p>
                  ) : (
                    <div className="table-wrap" style={{ maxHeight: 280, overflowY: 'auto' }}>
                      <table>
                        <thead>
                          <tr><th>Member</th><th>Shares</th><th>Share Value</th><th>Dividend</th></tr>
                        </thead>
                        <tbody>
                          {dividends.members.map(member => (
                            <tr key={member.memberId}>
                              <td>
                                <strong>{member.memberName}</strong>
                                <span className="cell-sub">{member.membershipNo}</span>
                              </td>
                              <td>{member.totalShares}</td>
                              <td>{formatMoney(member.shareValue)}</td>
                              <td><strong>{formatMoney(member.dividend)}</strong></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              )}

              <div className="form-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowDividends(false)}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Shares
