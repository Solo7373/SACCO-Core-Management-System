const BASE_URL = '/api'

function actorId() {
  return localStorage.getItem('userId') || '1'
}

function withActor(url) {
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}actorId=${encodeURIComponent(actorId())}`
}

function queryString(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, value)
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function request(url, options = {}) {
  const token = localStorage.getItem('token')
  const headers = { 'Content-Type': 'application/json' }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const response = await fetch(`${BASE_URL}${withActor(url)}`, {
    headers,
    ...options
  })
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
      localStorage.removeItem('memberId')
      localStorage.removeItem('passwordResetRequired')
      window.location.href = '/'
      throw new Error('Session expired. Please sign in again.')
    }
    const error = await response.json().catch(() => ({ message: `HTTP ${response.status}` }))
    throw new Error(error.message || `Request failed with status ${response.status}`)
  }
  if (response.status === 204) {
    return null
  }
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

async function upload(url, file, fields = {}) {
  const token = localStorage.getItem('token')
  const headers = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const formData = new FormData()
  formData.append('file', file)
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      formData.append(key, value)
    }
  })

  const response = await fetch(`${BASE_URL}${withActor(url)}`, {
    method: 'POST',
    headers,
    body: formData
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: `HTTP ${response.status}` }))
    throw new Error(error.message || `Upload failed with status ${response.status}`)
  }
  return response.json()
}

async function download(url, filename) {
  const token = localStorage.getItem('token')
  const headers = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const response = await fetch(`${BASE_URL}${withActor(url)}`, { headers })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: `HTTP ${response.status}` }))
    throw new Error(error.message || `Download failed with status ${response.status}`)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
}

export const api = {
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  changePassword: (userId, data) => request(`/auth/change-password?userId=${encodeURIComponent(userId)}`, { method: 'POST', body: JSON.stringify(data) }),
  recoverAdmin: (data) => request('/auth/recover-admin', { method: 'POST', body: JSON.stringify(data) }),
  getSetupStatus: () => request('/setup/status'),
  saveSetupOrganization: (data) => request('/setup/organization', { method: 'POST', body: JSON.stringify(data) }),
  saveSetupAdmin: (data) => request('/setup/admin', { method: 'POST', body: JSON.stringify(data) }),
  saveSetupStorage: (data) => request('/setup/storage', { method: 'POST', body: JSON.stringify(data) }),
  uploadSetupCredentials: (file) => upload('/setup/storage/credentials', file),
  testSetupStorage: () => request('/setup/storage/test', { method: 'POST' }),
  testSetupEmail: () => request('/setup/email/test', { method: 'POST' }),
  sendSetupTestEmail: (to) => request('/setup/email/send-test', { method: 'POST', body: JSON.stringify({ to: to || '' }) }),
  saveSetupMessaging: (data) => request('/setup/messaging', { method: 'POST', body: JSON.stringify(data) }),
  saveSetupBilling: (data) => request('/setup/billing', { method: 'POST', body: JSON.stringify(data) }),
  completeSetup: () => request('/setup/complete', { method: 'POST' }),
  runSetupBackup: () => request('/setup/backup/run', { method: 'POST' }),
  getEmailStatus: () => request('/communications/email/status'),
  sendEmail: (data) => request('/communications/email/send', { method: 'POST', body: JSON.stringify(data) }),
  registerMember: (data) => request('/members', { method: 'POST', body: JSON.stringify(data) }),
  registerMemberWithDocuments: (data) => request('/members/with-documents', { method: 'POST', body: JSON.stringify(data) }),
  getMembers: () => request('/members'),
  getArchivedMembers: () => request('/members/archived'),
  getMember: (id) => request(`/members/${id}`),
  updateMember: (id, data) => request(`/members/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  archiveMember: (id, reason) => request(
    `/members/${id}/archive${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`,
    { method: 'POST' }
  ),
  restoreMember: (id) => request(`/members/${id}/restore`, { method: 'POST' }),
  getMemberUser: (id) => request(`/members/${id}/user`),
  saveMemberUser: (id, data) => request(`/members/${id}/user`, { method: 'POST', body: JSON.stringify(data) }),
  getMemberProfile: (id) => request(`/members/${id}/profile`),
  getMemberMonthlySavings: (id, params) => request(`/members/${id}/savings/monthly${queryString(params || {})}`),
  getMemberDocuments: (id) => request(`/members/${id}/documents`),
  uploadMemberPhoto: (memberId, file) => upload(`/members/${memberId}/photo`, file),
  uploadDocumentFile: (memberId, documentId, file) => upload(`/members/${memberId}/documents/${documentId}/file`, file),
  fileUrl: (path) => path ? `/api/files?path=${encodeURIComponent(path)}` : null,
  addIdentityDocument: (memberId, data) => request(`/members/${memberId}/documents`, { method: 'POST', body: JSON.stringify(data) }),
  verifyDocument: (data) => request('/members/verify-document', { method: 'POST', body: JSON.stringify(data) }),
  checkDuplicates: (firstName, lastName, phone) => request(`/members/check-duplicates?firstName=${firstName}&lastName=${lastName}&phone=${phone || ''}`),
  openAccount: (data, primaryMemberId) => request(`/accounts?primaryMemberId=${primaryMemberId}`, { method: 'POST', body: JSON.stringify(data) }),
  getAccounts: () => request('/accounts'),
  getAccount: (id) => request(`/accounts/${id}`),
  getAccountHolders: (id) => request(`/accounts/${id}/holders`),
  getAccountsByMember: (memberId) => request(`/accounts/member/${memberId}`),
  activateAccount: (id) => request(`/accounts/${id}/activate`, { method: 'POST' }),
  freezeAccount: (id, reason) => request(`/accounts/${id}/freeze?reason=${encodeURIComponent(reason)}`, { method: 'POST' }),
  unfreezeAccount: (id) => request(`/accounts/${id}/unfreeze`, { method: 'POST' }),
  closeAccount: (id, reason) => request(`/accounts/${id}/close?reason=${encodeURIComponent(reason)}`, { method: 'POST' }),
  markAccountDormant: (id) => request(`/accounts/${id}/dormant`, { method: 'POST' }),
  runDormancyCheck: () => request('/accounts/run-dormancy-check', { method: 'POST' }),
  postTransaction: (data) => request('/savings/transactions', { method: 'POST', body: JSON.stringify(data) }),
  postTransfer: (data) => request('/savings/transfers', { method: 'POST', body: JSON.stringify(data) }),
  getBalance: (accountId) => request(`/savings/accounts/${accountId}/balance`),
  getAccountTransactions: (accountId) => request(`/savings/accounts/${accountId}/transactions`),
  getSavingsTransaction: (id) => request(`/savings/transactions/${id}`),
  getAllTransactions: () => request('/savings/transactions'),
  getWithdrawalThreshold: () => request('/savings/withdrawal-threshold'),
  reverseTransaction: (id, reason) => request(`/savings/transactions/${id}/reverse?reason=${encodeURIComponent(reason)}`, { method: 'POST' }),
  createShareProduct: (data) => request('/shares/products', { method: 'POST', body: JSON.stringify(data) }),
  getActiveShareProducts: () => request('/shares/products/active'),
  recordShareTransaction: (data) => request('/shares/transactions', { method: 'POST', body: JSON.stringify(data) }),
  getAllShareTransactions: () => request('/shares/transactions'),
  getMemberShareTransactions: (memberId) => request(`/shares/transactions/member/${memberId}`),
  approveShareTransaction: (id) => request(`/shares/transactions/${id}/approve`, { method: 'POST' }),
  rejectShareTransaction: (id) => request(`/shares/transactions/${id}/reject`, { method: 'POST' }),
  getShareSummary: (memberId) => request(`/shares/members/${memberId}/summary`),
  getSharePortfolioSummary: () => request('/shares/summary'),
  calculateDividends: (rate) => request(`/shares/dividends?rate=${rate}`),
  createLoanProduct: (data) => request('/loans/products', { method: 'POST', body: JSON.stringify(data) }),
  getLoanProducts: () => request('/loans/products'),
  getActiveLoanProducts: () => request('/loans/products/active'),
  applyLoan: (data) => request('/loans/applications', { method: 'POST', body: JSON.stringify(data) }),
  getLoans: () => request('/loans'),
  getLoan: (id) => request(`/loans/${id}`),
  getLoansByMember: (memberId) => request(`/loans/member/${memberId}`),
  getLoanArrears: () => request('/loans/arrears'),
  checkLoanEligibility: (memberId, productId, amount) => {
    const amountParam = amount ? `&amount=${encodeURIComponent(amount)}` : ''
    return request(`/loans/eligibility?memberId=${memberId}&productId=${productId}${amountParam}`)
  },
  reviewLoan: (id, data) => request(`/loans/${id}/review`, { method: 'POST', body: JSON.stringify(data || {}) }),
  approveLoan: (id) => request(`/loans/${id}/approve`, { method: 'POST' }),
  rejectLoan: (id, data) => request(`/loans/${id}/reject`, { method: 'POST', body: JSON.stringify(data) }),
  disburseLoan: (id, data) => request(`/loans/${id}/disburse`, { method: 'POST', body: JSON.stringify(data || {}) }),
  getLoanSchedule: (id) => request(`/loans/${id}/schedule`),
  getLoanRepayments: (id) => request(`/loans/${id}/repayments`),
  repayLoan: (id, data) => request(`/loans/${id}/repayments`, { method: 'POST', body: JSON.stringify(data) }),
  getLoanDemands: (id) => request(`/loans/${id}/demands`),
  downloadLoanDemandPdf: (loanId, demandId, demandNumber) => download(
    `/loans/${loanId}/demands/${demandId}/pdf`,
    `${demandNumber || `demand-${demandId}`}.pdf`
  ),

  // Loan collections: aging, demand notices and contact trail
  getCollectionSummary: () => request('/collections/summary'),
  getCollectionLoans: (params) => request(`/collections/loans${queryString(params || {})}`),
  getCollectionDetail: (loanId) => request(`/collections/loans/${loanId}`),
  getAllLoanDemands: () => request('/collections/demands'),
  issueLoanDemand: (loanId, data) => request(`/collections/loans/${loanId}/demands`, {
    method: 'POST',
    body: JSON.stringify(data)
  }),
  updateLoanDemandStatus: (demandId, data) => request(`/collections/demands/${demandId}/status`, {
    method: 'POST',
    body: JSON.stringify(data)
  }),
  downloadDemandPdf: (loanId, demandId, demandNumber) => download(
    `/collections/loans/${loanId}/demands/${demandId}/pdf`,
    `${demandNumber || `demand-${demandId}`}.pdf`
  ),
  getCollectionActions: (loanId) => request(`/collections/loans/${loanId}/actions`),
  recordCollectionAction: (loanId, data) => request(`/collections/loans/${loanId}/actions`, {
    method: 'POST',
    body: JSON.stringify(data)
  }),

  postJournalEntry: (data) => request('/ledger/entries', { method: 'POST', body: JSON.stringify(data) }),
  getJournalEntries: (sourceType, sourceId) => request(`/ledger/entries?sourceType=${sourceType}&sourceId=${sourceId}`),
  reverseJournalEntry: (id, reason) => request(`/ledger/entries/${id}/reverse?reason=${reason}`, { method: 'POST' }),
  getAccountBalance: (accountCode) => request(`/ledger/accounts/${accountCode}/balance`),
  getTrialBalance: () => request('/ledger/trial-balance'),
  getChartOfAccounts: () => request('/ledger/chart-of-accounts'),
  getTrialBalanceReport: (asOfDate) => request(`/reports/trial-balance${asOfDate ? `?asOfDate=${asOfDate}` : ''}`),
  getBalanceSheet: (asOfDate) => request(`/reports/balance-sheet${asOfDate ? `?asOfDate=${asOfDate}` : ''}`),
  getIncomeExpenditure: (fromDate, toDate) => {
    const params = []
    if (fromDate) params.push(`fromDate=${fromDate}`)
    if (toDate) params.push(`toDate=${toDate}`)
    return request(`/reports/income-expenditure${params.length ? `?${params.join('&')}` : ''}`)
  },
  downloadMemberStatement: (memberId, format = 'pdf') => download(
    `/reports/member/${memberId}/statement?format=${format}`,
    `member-${memberId}-statement.${format}`
  ),
  getUsers: () => request('/users'),
  createUser: (data) => request('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUserRole: (id, role) => request(`/users/${id}/role?role=${encodeURIComponent(role)}`, { method: 'PUT' }),
  activateUser: (id) => request(`/users/${id}/activate`, { method: 'POST' }),
  deactivateUser: (id) => request(`/users/${id}/deactivate`, { method: 'POST' }),
  resetUserPassword: (id, password) => request(`/users/${id}/reset-password?password=${encodeURIComponent(password)}`, { method: 'POST' }),
  getRoles: () => request('/users/roles'),
  getDashboardSummary: (params) => request(`/dashboard/summary${queryString(params || {})}`),
  getAuditEvents: (limit = 20) => request(`/audit/events?limit=${limit}`),
  getCollateral: () => request('/collateral'),
  getCollateralById: (id) => request(`/collateral/${id}`),
  getCollateralByMember: (memberId) => request(`/collateral/member/${memberId}`),
  createCollateral: (data) => request('/collateral', { method: 'POST', body: JSON.stringify(data) }),
  updateCollateral: (id, data) => request(`/collateral/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  releaseCollateral: (id, data) => request(`/collateral/${id}/release`, { method: 'POST', body: JSON.stringify(data) }),
  markCollateralDefault: (id, data) => request(`/collateral/${id}/default`, { method: 'POST', body: JSON.stringify(data) }),
  uploadCollateralDocument: (id, file, fields) => upload(`/collateral/${id}/documents`, file, fields),
  deleteCollateralDocument: (id, documentId) => request(`/collateral/${id}/documents/${documentId}`, { method: 'DELETE' }),

  // Finance configuration: tax rates and transaction fees
  getTaxRates: () => request('/finance/tax-rates'),
  createTaxRate: (data) => request('/finance/tax-rates', { method: 'POST', body: JSON.stringify(data) }),
  updateTaxRate: (id, data) => request(`/finance/tax-rates/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  toggleTaxRate: (id) => request(`/finance/tax-rates/${id}/toggle`, { method: 'POST' }),
  getFeeConfigs: () => request('/finance/fee-configs'),
  createFeeConfig: (data) => request('/finance/fee-configs', { method: 'POST', body: JSON.stringify(data) }),
  updateFeeConfig: (id, data) => request(`/finance/fee-configs/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  toggleFeeConfig: (id) => request(`/finance/fee-configs/${id}/toggle`, { method: 'POST' }),

  // Auditor compliance workspace
  getComplianceTransactions: (params) => request(`/compliance/transactions${queryString(params)}`),
  exportComplianceTransactions: (params, format = 'csv') => download(
    `/compliance/transactions/export${queryString({ ...params, format })}`,
    `transaction-sheet-${new Date().toISOString().slice(0, 10)}.${format}`
  ),
  getTaxBreakdown: (params) => request(`/compliance/tax-breakdown${queryString(params)}`),
  exportTaxBreakdown: (params, format = 'csv') => download(
    `/compliance/tax-breakdown/export${queryString({ ...params, format })}`,
    `tax-breakdown-${new Date().toISOString().slice(0, 10)}.${format}`
  ),
  getComplianceAuditLog: (params) => request(`/compliance/audit-log${queryString(params)}`),
  exportComplianceAuditLog: (params, format = 'csv') => download(
    `/compliance/audit-log/export${queryString({ ...params, format })}`,
    `audit-log-${new Date().toISOString().slice(0, 10)}.${format}`
  ),

  // Three-tier hybrid resilience backup engine
  getBackupStatus: () => request('/backups/status'),
  getBackupJobs: (limit = 25) => request(`/backups/jobs?limit=${limit}`),
  runBackup: () => request('/backups/run', { method: 'POST' }),
  retryBackupQueue: () => request('/backups/cloud/retry', { method: 'POST' }),

  // Product catalogue, stock and import procurement
  getProducts: () => request('/inventory/products'),
  createProduct: (data) => request('/inventory/products', { method: 'POST', body: JSON.stringify(data) }),
  updateProduct: (id, data) => request(`/inventory/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  toggleProduct: (id) => request(`/inventory/products/${id}/toggle`, { method: 'POST' }),
  adjustStock: (id, delta, reason) => request(
    `/inventory/products/${id}/stock?delta=${delta}${reason ? `&reason=${encodeURIComponent(reason)}` : ''}`,
    { method: 'POST' }
  ),
  getShippingMethods: () => request('/inventory/shipping-methods'),
  getImports: () => request('/inventory/imports'),
  previewImport: (data) => request('/inventory/imports/preview', { method: 'POST', body: JSON.stringify(data) }),
  createImport: (data) => request('/inventory/imports', { method: 'POST', body: JSON.stringify(data) }),
  markImportInTransit: (id) => request(`/inventory/imports/${id}/in-transit`, { method: 'POST' }),
  receiveImport: (id) => request(`/inventory/imports/${id}/receive`, { method: 'POST' }),
  cancelImport: (id) => request(`/inventory/imports/${id}/cancel`, { method: 'POST' }),
}
