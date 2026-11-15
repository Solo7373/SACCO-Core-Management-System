# Google Drive Integration

Google Drive is a **separate external integration** from the Meta WhatsApp Cloud
API. They share no credentials, billing, or failure handling:

| | Meta WhatsApp Cloud API | Google Drive API |
|---|---|---|
| Purpose | Send/receive WhatsApp messages | Store backups, documents, media archive |
| Credentials | `WA_ACCESS_TOKEN`, `WA_APP_SECRET` | Service account JSON or OAuth refresh token |
| Billing | Meta Business Portfolio | Google Drive storage quota |
| Config prefix | `sacco.whatsapp` / `WA_*` | `sacco.google-drive` / `GDRIVE_*` |
| Java package | `com.sacco.communication` | `com.sacco.storage.drive` |

---

## 1. What Drive will be used for

### A. Database backups (recommended first phase)

The existing backup engine already has three tiers:

- Tier 1: local `pg_dump` snapshot
- Tier 2: external USB/mounted drive
- Tier 3: durable PostgreSQL cloud queue

Google Drive becomes a new Tier 3 target:

```text
SACCO_BACKUP_CLOUD_MODE=GOOGLE_DRIVE
```

The existing queue, retries, backoff, audit events, and backup status API are
reused. Only the final upload step changes.

### B. Member documents and media

Member KYC documents, collateral documents, receipts, and similar files can be
copied to Drive. Two modes are possible:

- **ARCHIVE (recommended first):** the local `uploads/` copy remains the working
  file; Drive receives a copy for disaster recovery.
- **PRIMARY:** uploads, downloads, and deletes go through Drive. The local disk
  is only a cache. This is a larger change to `FileStorageService` and
  `FileController`.

Start with ARCHIVE so existing document flows keep working.

### C. WhatsApp media

**Do not use Google Drive links as the `mediaUrl` for WhatsApp messages.**

Meta fetches the media URL from its own servers. A normal Drive share link
returns an HTML page or a virus-scan confirmation, not the file, so Meta will
reject it or send the wrong content.

Correct flow:

1. Upload the file to Meta: `POST /{phone-number-id}/media`.
2. Send the message using the returned `media_id`.
3. Optionally archive the same file to Drive for retention.

Google Drive is an archive here, not the delivery link.

---

## 2. Authentication options

### Option A — Service account + Shared Drive (recommended for a server)

Best for backups and unattended server uploads.

1. Create a Google Cloud project.
2. Enable the **Google Drive API**.
3. Create a **service account**.
4. Create and download a **JSON key** for that service account.
5. Create a **Shared Drive** (Google Workspace) or a normal Drive folder.
6. Add the service account email as a member:
   - Shared Drive: **Content manager** or **Contributor**
   - Normal folder: **Editor**
7. Copy the folder ID from the URL:

   ```text
   https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz
                                              └──────── folder ID ────────┘
   ```

8. Store the JSON key outside the repository, for example:

   ```text
   C:\SCMS-Secrets\google-service-account.json
   ```

> Service accounts do not have a normal Gmail mailbox. On a personal Gmail
> account, uploads to a folder shared with a service account can hit storage
> quota limits. For production, use a Google Workspace Shared Drive, or use
> Option B.

### Option B — OAuth refresh token for the SACCO Google account

Best when the SACCO uses a personal Gmail account and wants files owned by that
account.

1. Create a Google Cloud project and enable the Drive API.
2. Configure the OAuth consent screen (Internal or External/Testing).
3. Create an OAuth client of type **Desktop app**.
4. Authorize the `https://www.googleapis.com/auth/drive.file` scope once and
   obtain a **refresh token**.
5. Store the client ID, client secret, and refresh token outside the repository.

The server exchanges the refresh token for short-lived access tokens. No browser
is needed after the initial consent.

---

## 3. Planned environment variables

```text
GDRIVE_ENABLED=true
GDRIVE_AUTH_MODE=SERVICE_ACCOUNT        # or OAUTH
GDRIVE_CREDENTIALS_FILE=C:\SCMS-Secrets\google-service-account.json
GDRIVE_FOLDER_ID=1AbCdEfGhIjKlMnOpQrStUvWxYz
GDRIVE_APPLICATION_NAME=SCMS
GDRIVE_TIMEOUT_MS=30000

# OAuth mode only
GDRIVE_CLIENT_ID=...
GDRIVE_CLIENT_SECRET=...
GDRIVE_REFRESH_TOKEN=...

# Backup integration
SACCO_BACKUP_CLOUD_MODE=GOOGLE_DRIVE
SACCO_BACKUP_CLOUD_GOOGLE_DRIVE_FOLDER_ID=1AbCdEfGhIjKlMnOpQrStUvWxYz
```

Never commit the JSON key or refresh token. Add the secrets directory to
`.gitignore`.

---

## 4. Drive folder layout

```text
SCMS/
├── Backups/
│   └── sacco_20261115_020000.sql
├── Member Documents/
│   └── {memberId}/
│       ├── national_id.pdf
│       └── payslip.pdf
└── WhatsApp Media/
    └── 2026/
        └── 11/
            └── receipt-{messageId}.pdf
```

Use separate folders for backups and documents so retention policies and access
can differ.

---

## 5. Implementation phases

### Phase 1 — Backup target (first)

- Add `GoogleDriveProperties` and `GoogleDriveClient`.
- Add `GOOGLE_DRIVE` to `BackupService`'s Tier 3 modes.
- Upload the existing `pg_dump` snapshot with a resumable Drive upload.
- Reuse the existing `backup_jobs` queue and retry logic.
- Expose Drive file ID/link in `BackupJob.targetPath`.
- Add a local mock Drive server for tests.

### Phase 2 — Document archive

- Add `GoogleDriveStorageService`.
- After a successful local `FileStorageService.store(...)`, copy the file to
  Drive when `GDRIVE_ARCHIVE_DOCUMENTS=true`.
- Record the Drive file ID alongside the existing local path.
- Add a restore command/API for disaster recovery.

### Phase 3 — WhatsApp media archive

- Add a Meta media-upload service (`POST /{phone-number-id}/media`).
- Send WhatsApp media by `media_id`.
- Optionally copy the same file to `WhatsApp Media/` in Drive for retention.

---

## 6. Security and cost notes

- The service account JSON is equivalent to a password. Restrict file
  permissions and never commit it.
- Grant the service account access only to the SCMS folders, not the whole
  Drive.
- `pg_dump` output is plain SQL. Consider encrypting backups before upload
  (for example GPG with a key held offline).
- Google Drive storage is billed by Google Workspace/Google One quota. API
  calls have per-project quotas; the backup queue's retry/backoff already
  handles transient 429/5xx responses.
- Test restores regularly. An upload that cannot be restored is not a backup.

---

## 7. Mock-first development

Until real credentials exist, the Drive client can point at a local mock server:

```text
GDRIVE_ENABLED=true
GDRIVE_AUTH_MODE=MOCK
GDRIVE_BASE_URL=http://localhost:9098
GDRIVE_FOLDER_ID=mock-folder
SACCO_BACKUP_CLOUD_MODE=GOOGLE_DRIVE
```

The mock implements the Drive token, metadata, and upload endpoints and stores
files in a local directory. This lets the backup queue, retries, audit events,
and UI status be tested end to end with zero Google cost.
