import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

const CATEGORIES = [
  ['PASSBOOK', 'Passbook'],
  ['STATEMENT_BOOK', 'Statement Book'],
  ['MERCHANDISE', 'Merchandise'],
  ['EQUIPMENT', 'Equipment'],
  ['STATIONERY', 'Stationery'],
  ['OTHER', 'Other']
]

const CUSTOMS_OPTIONS = [
  ['0', '0% - Exempt'],
  ['10', '10% - Preferential'],
  ['25', '25% - Standard customs duty'],
  ['35', '35% - High duty']
]

const VAT_OPTIONS = [
  ['0', '0% - VAT exempt'],
  ['18', '18% - Standard import VAT']
]

const STATUS_BADGES = {
  ORDERED: 'badge-warning',
  IN_TRANSIT: 'badge-info',
  RECEIVED: 'badge-success',
  CANCELLED: 'badge-default'
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function categoryLabel(value) {
  return CATEGORIES.find(([key]) => key === value)?.[1] || value
}

function emptyProductForm() {
  return {
    code: '',
    name: '',
    category: 'PASSBOOK',
    unitPrice: '',
    costPrice: '',
    stockQuantity: '0',
    reorderLevel: '5',
    isActive: true,
    description: ''
  }
}

function emptyImportForm(shippingMethods) {
  return {
    productId: '',
    supplierName: '',
    supplierCountry: '',
    quantity: '',
    unitCost: '',
    shippingMethod: shippingMethods[0]?.code || '',
    customsDutyRate: '25',
    vatRate: '18',
    otherTaxes: '0',
    orderDate: new Date().toISOString().slice(0, 10),
    expectedDate: '',
    notes: ''
  }
}

function Inventory() {
  const role = localStorage.getItem('role') || ''
  const canManage = ['ADMIN', 'MANAGER'].includes(role)

  const [tab, setTab] = useState('products')
  const [products, setProducts] = useState([])
  const [imports, setImports] = useState([])
  const [shippingMethods, setShippingMethods] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [saving, setSaving] = useState(false)

  const [showProductForm, setShowProductForm] = useState(false)
  const [editingProductId, setEditingProductId] = useState(null)
  const [productForm, setProductForm] = useState(emptyProductForm)

  const [stockTarget, setStockTarget] = useState(null)
  const [stockForm, setStockForm] = useState({ delta: '', reason: '' })

  const [showImportForm, setShowImportForm] = useState(false)
  const [importForm, setImportForm] = useState(() => emptyImportForm([]))
  const [importActionId, setImportActionId] = useState(null)

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const [productData, importData, shippingData] = await Promise.all([
        api.getProducts().catch(() => []),
        api.getImports().catch(() => []),
        api.getShippingMethods().catch(() => [])
      ])
      setProducts(productData || [])
      setImports(importData || [])
      setShippingMethods(shippingData || [])
      setImportForm(prev => (
        prev.shippingMethod ? prev : { ...prev, shippingMethod: shippingData?.[0]?.code || '' }
      ))
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

  function openProductForm(product) {
    setError(null)
    if (product) {
      setEditingProductId(product.productId)
      setProductForm({
        code: product.code || '',
        name: product.name || '',
        category: product.category || 'PASSBOOK',
        unitPrice: product.unitPrice ?? '',
        costPrice: product.costPrice ?? '',
        stockQuantity: product.stockQuantity ?? '0',
        reorderLevel: product.reorderLevel ?? '5',
        isActive: product.isActive !== false,
        description: product.description || ''
      })
    } else {
      setEditingProductId(null)
      setProductForm(emptyProductForm())
    }
    setShowProductForm(true)
  }

  async function submitProduct(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        ...productForm,
        unitPrice: Number(productForm.unitPrice),
        costPrice: productForm.costPrice === '' ? null : Number(productForm.costPrice),
        stockQuantity: Number(productForm.stockQuantity || 0),
        reorderLevel: Number(productForm.reorderLevel || 0)
      }
      if (editingProductId) {
        await api.updateProduct(editingProductId, payload)
        flashSuccess('Product updated.')
      } else {
        await api.createProduct(payload)
        flashSuccess('Product added to the catalogue.')
      }
      setShowProductForm(false)
      setEditingProductId(null)
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function toggleProduct(product) {
    setError(null)
    try {
      await api.toggleProduct(product.productId)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  async function submitStock(event) {
    event.preventDefault()
    if (!stockTarget) return
    const delta = Number(stockForm.delta)
    if (!delta) {
      setError('Enter a non-zero stock adjustment.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await api.adjustStock(stockTarget.productId, delta, stockForm.reason || undefined)
      flashSuccess(`Stock adjusted for ${stockTarget.name}.`)
      setStockTarget(null)
      setStockForm({ delta: '', reason: '' })
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  function openImportForm() {
    setError(null)
    setImportForm(emptyImportForm(shippingMethods))
    setShowImportForm(true)
  }

  const selectedShipping = shippingMethods.find(method => method.code === importForm.shippingMethod)

  const importPreview = useMemo(() => {
    const quantity = Number(importForm.quantity || 0)
    const unitCost = Number(importForm.unitCost || 0)
    if (quantity <= 0 || unitCost < 0 || !importForm.unitCost) {
      return null
    }
    const baseCost = quantity * unitCost
    const shippingFee = Number(selectedShipping?.baseFee || 0)
    const customsDutyAmount = baseCost * Number(importForm.customsDutyRate || 0) / 100
    const vatAmount = (baseCost + shippingFee + customsDutyAmount) * Number(importForm.vatRate || 0) / 100
    const otherTaxes = Number(importForm.otherTaxes || 0)
    const totalLandedCost = baseCost + shippingFee + customsDutyAmount + vatAmount + otherTaxes
    return {
      baseCost,
      shippingFee,
      customsDutyAmount,
      vatAmount,
      otherTaxes,
      totalLandedCost,
      unitLandedCost: totalLandedCost / quantity
    }
  }, [importForm, selectedShipping])

  async function submitImport(event) {
    event.preventDefault()
    if (!importForm.productId) {
      setError('Select the product being imported.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await api.createImport({
        productId: Number(importForm.productId),
        supplierName: importForm.supplierName.trim(),
        supplierCountry: importForm.supplierCountry || null,
        quantity: Number(importForm.quantity),
        unitCost: Number(importForm.unitCost),
        shippingMethod: importForm.shippingMethod,
        shippingFee: importPreview ? importPreview.shippingFee : null,
        customsDutyRate: Number(importForm.customsDutyRate || 0),
        vatRate: Number(importForm.vatRate || 0),
        otherTaxes: Number(importForm.otherTaxes || 0),
        orderDate: importForm.orderDate || null,
        expectedDate: importForm.expectedDate || null,
        notes: importForm.notes || null
      })
      flashSuccess('Import order created with landed-cost breakdown.')
      setShowImportForm(false)
      setImportForm(emptyImportForm(shippingMethods))
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function updateImportStatus(item, action) {
    if (action === 'cancel' && !window.confirm(`Cancel import order IMP-${item.importId}?`)) {
      return
    }
    setImportActionId(item.importId)
    setError(null)
    try {
      if (action === 'transit') {
        await api.markImportInTransit(item.importId)
        flashSuccess('Import marked as in transit.')
      } else if (action === 'receive') {
        await api.receiveImport(item.importId)
        flashSuccess('Import received. Stock levels and product cost updated.')
      } else {
        await api.cancelImport(item.importId)
        flashSuccess('Import order cancelled.')
      }
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setImportActionId(null)
    }
  }

  const productMap = useMemo(() => {
    const map = {}
    products.forEach(product => {
      map[product.productId] = product
    })
    return map
  }, [products])

  const lowStock = products.filter(product => Number(product.stockQuantity) <= Number(product.reorderLevel))
  const stockValue = products.reduce(
    (total, product) => total + Number(product.costPrice || 0) * Number(product.stockQuantity || 0),
    0
  )
  const activeImports = imports.filter(item => ['ORDERED', 'IN_TRANSIT'].includes(item.status))

  if (loading) {
    return <div className="loading">Loading inventory...</div>
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Products & Inventory</h2>
          <p className="page-subtitle">
            Passbooks, statement books, merchandise and equipment, with import taxes and landed-cost tracking
          </p>
        </div>
        <div className="header-actions">
          <span className="count-chip">{products.length} products</span>
          <span className="count-chip">{activeImports.length} active imports</span>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      {!canManage && (
        <div className="info-banner">
          Your role has read-only access to the product catalogue and procurement records.
        </div>
      )}

      <div className="mini-stat-row">
        <div className="mini-stat">
          <span>Catalogue Items</span>
          <strong>{products.length}</strong>
        </div>
        <div className="mini-stat">
          <span>Stock Value at Cost</span>
          <strong>{formatMoney(stockValue)}</strong>
        </div>
        <div className="mini-stat">
          <span>Low Stock Alerts</span>
          <strong>{lowStock.length}</strong>
        </div>
        <div className="mini-stat">
          <span>Imports Awaiting Receipt</span>
          <strong>{activeImports.length}</strong>
        </div>
      </div>

      <div className="tabs">
        <button className={`tab ${tab === 'products' ? 'active' : ''}`} onClick={() => setTab('products')}>
          Product Catalogue
        </button>
        <button className={`tab ${tab === 'imports' ? 'active' : ''}`} onClick={() => setTab('imports')}>
          Import Procurement
        </button>
      </div>

      {tab === 'products' && (
        <div className="tab-panel">
          <div className="card">
            <div className="card-heading">
              <div>
                <h3>Product Catalogue & Stock Levels</h3>
                <p className="card-subtitle">
                  Products below their reorder level are flagged automatically.
                </p>
              </div>
              {canManage && (
                <button className="btn btn-primary btn-sm" onClick={() => openProductForm(null)}>
                  + New Product
                </button>
              )}
            </div>

            {showProductForm && canManage && (
              <form className="config-form" onSubmit={submitProduct}>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Product Code *</label>
                    <input
                      value={productForm.code}
                      onChange={e => setProductForm({ ...productForm, code: e.target.value })}
                      placeholder="PB-002"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Product Name *</label>
                    <input
                      value={productForm.name}
                      onChange={e => setProductForm({ ...productForm, name: e.target.value })}
                      placeholder="Member Passbook"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Category *</label>
                    <select
                      value={productForm.category}
                      onChange={e => setProductForm({ ...productForm, category: e.target.value })}
                    >
                      {CATEGORIES.map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Selling Price (UGX) *</label>
                    <input
                      type="number"
                      min="0"
                      value={productForm.unitPrice}
                      onChange={e => setProductForm({ ...productForm, unitPrice: e.target.value })}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Cost Price (UGX)</label>
                    <input
                      type="number"
                      min="0"
                      value={productForm.costPrice}
                      onChange={e => setProductForm({ ...productForm, costPrice: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Stock Quantity</label>
                    <input
                      type="number"
                      min="0"
                      value={productForm.stockQuantity}
                      onChange={e => setProductForm({ ...productForm, stockQuantity: e.target.value })}
                    />
                  </div>
                </div>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Reorder Level</label>
                    <input
                      type="number"
                      min="0"
                      value={productForm.reorderLevel}
                      onChange={e => setProductForm({ ...productForm, reorderLevel: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Status</label>
                    <select
                      value={productForm.isActive ? 'active' : 'inactive'}
                      onChange={e => setProductForm({ ...productForm, isActive: e.target.value === 'active' })}
                    >
                      <option value="active">Active</option>
                      <option value="inactive">Inactive</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Description</label>
                    <input
                      value={productForm.description}
                      onChange={e => setProductForm({ ...productForm, description: e.target.value })}
                    />
                  </div>
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setShowProductForm(false)}>
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : editingProductId ? 'Update Product' : 'Create Product'}
                  </button>
                </div>
              </form>
            )}

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Category</th>
                    <th>Selling Price</th>
                    <th>Cost Price</th>
                    <th>Stock</th>
                    <th>Status</th>
                    {canManage && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {products.length === 0 ? (
                    <tr>
                      <td colSpan={canManage ? 7 : 6} className="empty-text">No products in the catalogue.</td>
                    </tr>
                  ) : products.map(product => {
                    const low = Number(product.stockQuantity) <= Number(product.reorderLevel)
                    return (
                      <tr key={product.productId}>
                        <td>
                          <strong>{product.name}</strong>
                          <div className="cell-subtext">{product.code}</div>
                        </td>
                        <td>{categoryLabel(product.category)}</td>
                        <td className="numeric-cell">{formatMoney(product.unitPrice)}</td>
                        <td className="numeric-cell">{formatMoney(product.costPrice)}</td>
                        <td>
                          <span className={`badge ${low ? 'badge-danger' : 'badge-success'}`}>
                            {product.stockQuantity} units
                          </span>
                          <div className="cell-subtext">Reorder at {product.reorderLevel}</div>
                        </td>
                        <td>
                          <span className={`badge ${product.isActive ? 'badge-success' : 'badge-default'}`}>
                            {product.isActive ? 'ACTIVE' : 'INACTIVE'}
                          </span>
                        </td>
                        {canManage && (
                          <td>
                            <div className="row-actions">
                              <button className="btn btn-secondary btn-sm" onClick={() => openProductForm(product)}>
                                Edit
                              </button>
                              <button
                                className="btn btn-ghost btn-sm"
                                onClick={() => {
                                  setStockTarget(product)
                                  setStockForm({ delta: '', reason: '' })
                                }}
                              >
                                Adjust Stock
                              </button>
                              <button className="btn btn-ghost btn-sm" onClick={() => toggleProduct(product)}>
                                {product.isActive ? 'Disable' : 'Enable'}
                              </button>
                            </div>
                          </td>
                        )}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'imports' && (
        <div className="tab-panel">
          <div className="card">
            <div className="card-heading">
              <div>
                <h3>Import Orders</h3>
                <p className="card-subtitle">
                  Select a shipping method, customs duty and import VAT. Landed cost is calculated automatically
                  before the order is saved.
                </p>
              </div>
              {canManage && (
                <button className="btn btn-primary btn-sm" onClick={openImportForm}>
                  + New Import Order
                </button>
              )}
            </div>

            {showImportForm && canManage && (
              <form className="config-form" onSubmit={submitImport}>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Product *</label>
                    <select
                      value={importForm.productId}
                      onChange={e => setImportForm({ ...importForm, productId: e.target.value })}
                      required
                    >
                      <option value="">Select a product...</option>
                      {products.filter(product => product.isActive).map(product => (
                        <option key={product.productId} value={product.productId}>
                          {product.code} - {product.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Supplier Name *</label>
                    <input
                      value={importForm.supplierName}
                      onChange={e => setImportForm({ ...importForm, supplierName: e.target.value })}
                      placeholder="Supplier or manufacturer"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Supplier Country</label>
                    <input
                      value={importForm.supplierCountry}
                      onChange={e => setImportForm({ ...importForm, supplierCountry: e.target.value })}
                      placeholder="China, Kenya, UAE..."
                    />
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label>Quantity *</label>
                    <input
                      type="number"
                      min="1"
                      value={importForm.quantity}
                      onChange={e => setImportForm({ ...importForm, quantity: e.target.value })}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Unit Cost (UGX) *</label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={importForm.unitCost}
                      onChange={e => setImportForm({ ...importForm, unitCost: e.target.value })}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Shipping Method *</label>
                    <select
                      value={importForm.shippingMethod}
                      onChange={e => setImportForm({ ...importForm, shippingMethod: e.target.value })}
                      required
                    >
                      {shippingMethods.map(method => (
                        <option key={method.code} value={method.code}>
                          {method.name} - {formatMoney(method.baseFee)}
                          {method.estimatedDays ? ` (~${method.estimatedDays} days)` : ''}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label>Customs Duty</label>
                    <select
                      value={importForm.customsDutyRate}
                      onChange={e => setImportForm({ ...importForm, customsDutyRate: e.target.value })}
                    >
                      {CUSTOMS_OPTIONS.map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Import VAT</label>
                    <select
                      value={importForm.vatRate}
                      onChange={e => setImportForm({ ...importForm, vatRate: e.target.value })}
                    >
                      {VAT_OPTIONS.map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Other Taxes / Levies (UGX)</label>
                    <input
                      type="number"
                      min="0"
                      value={importForm.otherTaxes}
                      onChange={e => setImportForm({ ...importForm, otherTaxes: e.target.value })}
                    />
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label>Order Date</label>
                    <input
                      type="date"
                      value={importForm.orderDate}
                      onChange={e => setImportForm({ ...importForm, orderDate: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Expected Delivery</label>
                    <input
                      type="date"
                      value={importForm.expectedDate}
                      onChange={e => setImportForm({ ...importForm, expectedDate: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Notes</label>
                    <input
                      value={importForm.notes}
                      onChange={e => setImportForm({ ...importForm, notes: e.target.value })}
                    />
                  </div>
                </div>

                <div className="landed-cost-panel">
                  <div className="landed-cost-heading">
                    <h4>Automatic Landed Cost</h4>
                    <span className="card-subtitle">
                      {selectedShipping
                        ? `${selectedShipping.name}${selectedShipping.estimatedDays ? ` - approx. ${selectedShipping.estimatedDays} days` : ''}`
                        : 'Select a shipping method'}
                    </span>
                  </div>
                  {importPreview ? (
                    <>
                      <div className="landed-cost-grid">
                        <div><span>Base Cost</span><strong>{formatMoney(importPreview.baseCost)}</strong></div>
                        <div><span>Shipping Fee</span><strong>{formatMoney(importPreview.shippingFee)}</strong></div>
                        <div><span>Customs Duty</span><strong>{formatMoney(importPreview.customsDutyAmount)}</strong></div>
                        <div><span>Import VAT</span><strong>{formatMoney(importPreview.vatAmount)}</strong></div>
                        <div><span>Other Taxes</span><strong>{formatMoney(importPreview.otherTaxes)}</strong></div>
                        <div className="landed-total"><span>Total Landed Cost</span><strong>{formatMoney(importPreview.totalLandedCost)}</strong></div>
                        <div><span>Unit Landed Cost</span><strong>{formatMoney(importPreview.unitLandedCost)}</strong></div>
                      </div>
                      <p className="field-hint">
                        On receipt, stock is increased and the product cost price is set to the unit landed cost.
                      </p>
                    </>
                  ) : (
                    <p className="empty-text">Enter a quantity and unit cost to preview the landed cost.</p>
                  )}
                </div>

                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setShowImportForm(false)}>
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : 'Create Import Order'}
                  </button>
                </div>
              </form>
            )}

            <div className="table-wrap">
              <table className="sheet-table">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Supplier</th>
                    <th>Qty / Unit Cost</th>
                    <th>Shipping</th>
                    <th>Customs</th>
                    <th>Import VAT</th>
                    <th>Landed Cost</th>
                    <th>Status</th>
                    {canManage && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {imports.length === 0 ? (
                    <tr>
                      <td colSpan={canManage ? 9 : 8} className="empty-text">No import orders recorded.</td>
                    </tr>
                  ) : imports.map(item => (
                    <tr key={item.importId}>
                      <td>
                        <strong>IMP-{item.importId}</strong>
                        <div className="cell-subtext">
                          {productMap[item.productId]?.name || item.productName || `Product #${item.productId}`}
                        </div>
                        <div className="cell-subtext">{item.orderDate || '-'}</div>
                      </td>
                      <td>
                        {item.supplierName}
                        {item.supplierCountry ? <div className="cell-subtext">{item.supplierCountry}</div> : null}
                      </td>
                      <td>
                        {item.quantity} units
                        <div className="cell-subtext">{formatMoney(item.unitCost)} / unit</div>
                      </td>
                      <td>
                        {item.shippingMethod}
                        <div className="cell-subtext">{formatMoney(item.shippingFee)}</div>
                      </td>
                      <td>
                        {Number(item.customsDutyRate || 0)}%
                        <div className="cell-subtext">{formatMoney(item.customsDutyAmount)}</div>
                      </td>
                      <td>
                        {Number(item.vatRate || 0)}%
                        <div className="cell-subtext">{formatMoney(item.vatAmount)}</div>
                      </td>
                      <td className="numeric-cell">
                        <strong>{formatMoney(item.totalLandedCost)}</strong>
                      </td>
                      <td>
                        <span className={`badge ${STATUS_BADGES[item.status] || 'badge-default'}`}>
                          {item.status}
                        </span>
                      </td>
                      {canManage && (
                        <td>
                          <div className="row-actions">
                            {item.status === 'ORDERED' && (
                              <button
                                className="btn btn-secondary btn-sm"
                                disabled={importActionId === item.importId}
                                onClick={() => updateImportStatus(item, 'transit')}
                              >
                                In Transit
                              </button>
                            )}
                            {['ORDERED', 'IN_TRANSIT'].includes(item.status) && (
                              <>
                                <button
                                  className="btn btn-success btn-sm"
                                  disabled={importActionId === item.importId}
                                  onClick={() => updateImportStatus(item, 'receive')}
                                >
                                  Receive
                                </button>
                                <button
                                  className="btn btn-ghost btn-sm"
                                  disabled={importActionId === item.importId}
                                  onClick={() => updateImportStatus(item, 'cancel')}
                                >
                                  Cancel
                                </button>
                              </>
                            )}
                            {item.status === 'RECEIVED' && (
                              <span className="cell-subtext">Received {item.receivedDate || ''}</span>
                            )}
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {stockTarget && (
        <div className="modal-overlay" onClick={() => setStockTarget(null)}>
          <div className="modal stock-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Adjust Stock - {stockTarget.name}</h3>
              <button className="modal-close" onClick={() => setStockTarget(null)}>x</button>
            </div>
            <div className="modal-body">
              <p className="field-hint">
                Current stock: <strong>{stockTarget.stockQuantity} units</strong>. Use a positive number to add
                stock and a negative number to remove damaged or lost items.
              </p>
              <form onSubmit={submitStock}>
                <div className="form-group">
                  <label>Adjustment Quantity *</label>
                  <input
                    type="number"
                    value={stockForm.delta}
                    onChange={e => setStockForm({ ...stockForm, delta: e.target.value })}
                    placeholder="e.g. 25 or -3"
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Reason</label>
                  <input
                    value={stockForm.reason}
                    onChange={e => setStockForm({ ...stockForm, reason: e.target.value })}
                    placeholder="Stock count, damage, donation..."
                  />
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setStockTarget(null)}>
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : 'Apply Adjustment'}
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

export default Inventory
