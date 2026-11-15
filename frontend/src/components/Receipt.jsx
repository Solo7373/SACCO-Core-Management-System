import { useEffect } from 'react'

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

export default function Receipt({ receipt, accounts, onClose }) {
  if (!receipt) return null

  const data = receipt.data || {}
  const isTransfer = receipt.kind === 'TRANSFER'

  return (
    <div className="receipt-print-root">
      <div className="receipt-paper">
        <div className="receipt-header">
          <div className="receipt-org">SACCO CORE MANAGEMENT SYSTEM</div>
          <div className="receipt-sub">Official Transaction Receipt</div>
          <div className="receipt-divider" />
        </div>

        <div className="receipt-ref">{data.reference}</div>
        <div className="receipt-divider" />

        <div className="receipt-body">
          {isTransfer ? (
            <>
              <ReceiptRow label="Type" value="Transfer" />
              <ReceiptRow label="From Transaction" value={data.fromTransaction?.reference} />
              <ReceiptRow label="To Transaction" value={data.toTransaction?.reference} />
              <ReceiptRow label="Amount" value={formatMoney(data.fromTransaction?.amount)} />
              <ReceiptRow label="Channel" value={data.fromTransaction?.channel || 'CASH'} />
              <ReceiptRow label="Transfer Fee" value={formatMoney(data.fromTransaction?.feeAmount)} />
              <ReceiptRow label="Tax" value={formatMoney(data.fromTransaction?.taxAmount)} />
              <ReceiptRow label="Net Credited" value={formatMoney(data.toTransaction?.amount)} />
              <ReceiptRow label="Status" value="Completed" />
              <ReceiptRow label="Posted" value={formatDate(data.fromTransaction?.createdAt)} />
            </>
          ) : (
            <>
              <ReceiptRow label="Type" value={data.transactionType || 'Transaction'} />
              <ReceiptRow label="Account" value={accounts?.find(a => a.accountId === data.accountId)?.accountNumber || `#${data.accountId}`} />
              <ReceiptRow label="Gross Amount" value={formatMoney(data.amount)} />
              <ReceiptRow label="Channel" value={data.channel || 'CASH'} />
              <ReceiptRow label="Fee" value={formatMoney(data.feeAmount)} />
              <ReceiptRow label="Tax" value={formatMoney(data.taxAmount)} />
              <ReceiptRow label="Net Amount" value={formatMoney(data.netAmount ?? data.amount)} />
              <ReceiptRow label="Status" value={data.status} />
              <ReceiptRow label="Value Date" value={data.valueDate || '—'} />
              <ReceiptRow label="Posted" value={formatDate(data.createdAt)} />
              <ReceiptRow label="Authorized By" value={data.authorizedBy || '—'} />
              <ReceiptRow label="Narration" value={data.description || '—'} />
            </>
          )}
        </div>

        <div className="receipt-divider" />
        <div className="receipt-footer">
          <div>Generated: {new Date().toLocaleString()}</div>
          <div className="receipt-signature">
            <div className="receipt-sig-line" />
            <div>Authorized Signature</div>
          </div>
          <div className="receipt-note">
            This is a computer-generated receipt. Keep it for your records.
          </div>
        </div>
      </div>

      <div className="receipt-actions no-print">
        <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
          Print Receipt
        </button>
        <button type="button" className="btn btn-primary" onClick={onClose}>
          Done
        </button>
      </div>
    </div>
  )
}

function ReceiptRow({ label, value }) {
  return (
    <div className="receipt-row">
      <span className="receipt-label">{label}</span>
      <span className="receipt-value">{value ?? '—'}</span>
    </div>
  )
}
