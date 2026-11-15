import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

const REFRESH_MS = 15000

const TIER_LABELS = {
  LOCAL: 'Tier 1 · Local snapshot',
  EXTERNAL: 'Tier 2 · External mirror',
  CLOUD: 'Tier 3 · Cloud queue'
}

const STATUS_CLASS = {
  COMPLETED: 'success',
  RUNNING: 'warning',
  QUEUED: 'warning',
  RETRYING: 'warning',
  PENDING: 'default',
  FAILED: 'danger'
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function formatBytes(value) {
  const bytes = Number(value || 0)
  if (!bytes) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

function shortChecksum(value) {
  if (!value) return '—'
  return `${value.slice(0, 12)}…`
}

function tierCardState(job) {
  if (!job) return { label: 'No run yet', className: 'backup-state-idle' }
  if (job.status === 'COMPLETED') return { label: 'Healthy', className: 'backup-state-ok' }
  if (job.status === 'FAILED') return { label: 'Failed', className: 'backup-state-bad' }
  return { label: job.status || 'Unknown', className: 'backup-state-warn' }
}

function Backups() {
  const role = localStorage.getItem('role') || ''
  const canManage = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER'].includes(role)

  const [status, setStatus] = useState(null)
  const [jobs, setJobs] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [action, setAction] = useState(null)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  async function loadData(silent = false) {
    if (!silent) setRefreshing(true)
    try {
      const [statusData, jobData] = await Promise.all([
        api.getBackupStatus(),
        api.getBackupJobs(30)
      ])
      setStatus(statusData)
      setJobs(jobData || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    loadData()
    const timer = setInterval(() => loadData(true), REFRESH_MS)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!success) return
    const timer = setTimeout(() => setSuccess(null), 6000)
    return () => clearTimeout(timer)
  }, [success])

  async function handleRunBackup() {
    if (!window.confirm('Run a full backup now? This creates a Tier 1 snapshot, mirrors it to the external drive and queues a Tier 3 cloud upload.')) {
      return
    }
    setAction('run')
    setError(null)
    try {
      const next = await api.runBackup()
      setStatus(next)
      setSuccess('Backup run requested. Tier 1/2/3 status has been refreshed.')
      await loadData(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setAction(null)
    }
  }

  async function handleRetryQueue() {
    setAction('retry')
    setError(null)
    try {
      const processed = await api.retryBackupQueue()
      setSuccess(`Cloud queue processed: ${processed} upload attempt(s).`)
      await loadData(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setAction(null)
    }
  }

  const localState = tierCardState(status?.lastLocalBackup)
  const externalState = tierCardState(status?.lastExternalBackup)
  const cloudState = tierCardState(status?.lastCloudUpload)

  const summary = useMemo(() => ({
    total: status?.totalJobs ?? jobs.length,
    queued: status?.queuedCloudJobs ?? 0,
    failed: status?.failedJobs ?? 0
  }), [status, jobs])

  if (loading) {
    return (
      <div>
        <div className="page-header">
          <div>
            <h2>Backup & Resilience</h2>
            <p className="page-subtitle">Three-tier hybrid backup engine</p>
          </div>
        </div>
        <div className="loading">Loading backup engine status…</div>
      </div>
    )
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Backup & Resilience</h2>
          <p className="page-subtitle">
            Continuous local snapshots, external-drive mirroring and an outage-proof cloud queue.
            Status refreshes automatically every 15 seconds.
          </p>
        </div>
        <div className="header-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => loadData()}
            disabled={refreshing}
          >
            {refreshing ? 'Refreshing…' : 'Refresh'}
          </button>
          {canManage && (
            <>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleRetryQueue}
                disabled={action !== null}
              >
                {action === 'retry' ? 'Resuming…' : 'Resume cloud queue'}
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleRunBackup}
                disabled={action !== null}
              >
                {action === 'run' ? 'Running backup…' : 'Run backup now'}
              </button>
            </>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      {!canManage && (
        <div className="info-banner">
          Read-only backup visibility. Only ADMIN, RECOVERY_ADMIN and MANAGER roles can trigger
          backups or resume the cloud queue.
        </div>
      )}

      <div className="backup-summary-row">
        <div className="backup-summary-chip">
          <span>Total jobs</span>
          <strong>{summary.total}</strong>
        </div>
        <div className="backup-summary-chip">
          <span>Queued cloud uploads</span>
          <strong>{summary.queued}</strong>
        </div>
        <div className="backup-summary-chip">
          <span>Failed jobs</span>
          <strong className={summary.failed > 0 ? 'text-danger' : ''}>{summary.failed}</strong>
        </div>
        <div className={`backup-summary-chip ${status?.pgDumpAvailable ? 'chip-ok' : 'chip-bad'}`}>
          <span>pg_dump</span>
          <strong>{status?.pgDumpAvailable ? 'Detected' : 'Not found'}</strong>
        </div>
      </div>

      <div className="backup-tier-grid">
        <div className="card backup-tier-card">
          <div className="backup-tier-head">
            <div>
              <span className="backup-tier-kicker">Tier 1</span>
              <h3>Continuous local snapshots</h3>
            </div>
            <span className={`backup-state ${localState.className}`}>{localState.label}</span>
          </div>
          <p className="backup-tier-copy">
            Scheduled <code>pg_dump</code> snapshots stored on the host disk. These are the primary
            recovery points and are checksummed with SHA-256.
          </p>
          <dl className="backup-detail-list">
            <div><dt>Directory</dt><dd className="mono-cell">{status?.localDirectory || '—'}</dd></div>
            <div><dt>pg_dump path</dt><dd className="mono-cell">{status?.pgDumpPath || '—'}</dd></div>
            <div><dt>Last snapshot</dt><dd>{formatDateTime(status?.lastLocalBackup?.createdAt)}</dd></div>
            <div><dt>Size</dt><dd>{formatBytes(status?.lastLocalBackup?.sizeBytes)}</dd></div>
            <div><dt>Checksum</dt><dd className="mono-cell">{shortChecksum(status?.lastLocalBackup?.checksum)}</dd></div>
          </dl>
        </div>

        <div className="card backup-tier-card">
          <div className="backup-tier-head">
            <div>
              <span className="backup-tier-kicker">Tier 2</span>
              <h3>External drive mirror</h3>
            </div>
            <span className={`backup-state ${externalState.className}`}>{externalState.label}</span>
          </div>
          <p className="backup-tier-copy">
            Every local snapshot is copied to a connected USB or mounted drive so a disk failure
            cannot destroy both copies.
          </p>
          <dl className="backup-detail-list">
            <div><dt>Directory</dt><dd className="mono-cell">{status?.externalDirectory || 'Not configured'}</dd></div>
            <div><dt>Configured</dt><dd>{status?.externalConfigured ? 'Yes' : 'No — set SACCO_BACKUP_EXTERNAL_DIR'}</dd></div>
            <div><dt>Last mirror</dt><dd>{formatDateTime(status?.lastExternalBackup?.createdAt)}</dd></div>
            <div><dt>Size</dt><dd>{formatBytes(status?.lastExternalBackup?.sizeBytes)}</dd></div>
            <div><dt>Checksum</dt><dd className="mono-cell">{shortChecksum(status?.lastExternalBackup?.checksum)}</dd></div>
          </dl>
        </div>

        <div className="card backup-tier-card">
          <div className="backup-tier-head">
            <div>
              <span className="backup-tier-kicker">Tier 3</span>
              <h3>Outage-proof cloud queue</h3>
            </div>
            <span className={`backup-state ${cloudState.className}`}>{cloudState.label}</span>
          </div>
          <p className="backup-tier-copy">
            Upload jobs live in PostgreSQL, not memory. They survive power and Wi-Fi outages and
            retry automatically with backoff until the cloud target accepts them.
          </p>
          <dl className="backup-detail-list">
            <div><dt>Mode</dt><dd>{status?.cloudMode || 'QUEUE_ONLY'}</dd></div>
            <div><dt>Target</dt><dd className="mono-cell">{status?.cloudTarget || '—'}</dd></div>
            <div><dt>Configured</dt><dd>{status?.cloudConfigured ? 'Yes' : 'Persistent queue only'}</dd></div>
            <div><dt>Queued / retrying</dt><dd>{status?.queuedCloudJobs ?? 0}</dd></div>
            <div><dt>Last upload</dt><dd>{formatDateTime(status?.lastCloudUpload?.createdAt)}</dd></div>
          </dl>
        </div>
      </div>

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Recent backup jobs</h3>
            <p className="card-subtitle">
              Tier 1 snapshots, Tier 2 mirrors and Tier 3 uploads, newest first.
            </p>
          </div>
          <span className="count-chip">{jobs.length} shown</span>
        </div>

        {jobs.length === 0 ? (
          <p className="empty-text">No backup jobs yet. Run a backup to seed the engine.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table backup-jobs-table">
              <thead>
                <tr>
                  <th>Tier</th>
                  <th>File</th>
                  <th>Status</th>
                  <th>Attempts</th>
                  <th>Size</th>
                  <th>Checksum</th>
                  <th>Created</th>
                  <th>Detail</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map(job => (
                  <tr key={job.jobId}>
                    <td>
                      <span className={`tier-pill tier-${String(job.tier || '').toLowerCase()}`}>
                        {job.tier}
                      </span>
                    </td>
                    <td className="mono-cell backup-file-cell">{job.fileName}</td>
                    <td>
                      <span className={`badge badge-${STATUS_CLASS[job.status] || 'default'}`}>
                        {job.status}
                      </span>
                    </td>
                    <td>{job.attempts ?? 0}</td>
                    <td>{formatBytes(job.sizeBytes)}</td>
                    <td className="mono-cell" title={job.checksum || ''}>{shortChecksum(job.checksum)}</td>
                    <td>{formatDateTime(job.createdAt)}</td>
                    <td className="backup-detail-cell">
                      {job.errorMessage
                        ? <span className="backup-error-text" title={job.errorMessage}>{job.errorMessage}</span>
                        : job.targetPath || TIER_LABELS[job.tier] || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

export default Backups
