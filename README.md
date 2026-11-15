# SACCO-Core-Management-System

This is a modern web-based application to handle all transactions in a SACCO with multiple ways of backing up data. The system is intended to solve a number of problems, including auditing-related issues. It ensures integrity in the institution by only soft-deleting data so records can be restored.

## Technology

- Java 21 and Spring Boot 3.2
- PostgreSQL 16
- React frontend
- Electron desktop wrapper

## Local setup

### Prerequisites

- JDK 21 or later
- Maven 3.9 or later
- PostgreSQL 16 or later
- Node.js 20 or later for the frontend and desktop build

The default database is `sacco_db` on `localhost:8080`. Change it with `SACCO_DB_URL` if your PostgreSQL instance uses a different port or database name.

### Environment variables

No database password or signing secret is committed to this repository. Configure these before starting the backend:

| Variable | Purpose |
| --- | --- |
| `SACCO_DB_URL` | JDBC URL. Default: `jdbc:postgresql://localhost:8080/sacco_db` |
| `SACCO_DB_USERNAME` | Database user. Default: `postgres` |
| `SACCO_DB_PASSWORD` | Database password. Default: `postgres` |
| `SACCO_JWT_SECRET` | High-entropy JWT signing key. If omitted, an ephemeral key is generated at startup and sessions end when the process restarts. |
| `SACCO_ADMIN_RECOVERY_KEY` | Break-glass administrator recovery key. Leave blank to disable administrator recovery. |

PowerShell example:

```powershell
$env:SACCO_DB_PASSWORD = "your-database-password"
$env:SACCO_JWT_SECRET = "a-long-random-value"
$env:SACCO_ADMIN_RECOVERY_KEY = "a-separate-long-random-value"
mvn spring-boot:run
```

To persist these for your Windows user, use `[Environment]::SetEnvironmentVariable("NAME", "value", "User")` and open a new terminal.

### Build and run

```powershell
mvn clean package
java -jar target/sacco-core-management-1.0-SNAPSHOT.jar
```

The backend listens on `http://localhost:8081`.

### Desktop build

```powershell
cd desktop
npm install
npm run build:dir
```

`target/`, `desktop/dist/`, `backups/`, and `uploads/` are local build or runtime data and are not committed.

## Integrations

- **Africa's Talking** for SMS, delivery reports, incoming SMS, and OTP verification (`AT_*` variables).
- **Meta WhatsApp Cloud API** for WhatsApp messaging (`WA_*` variables).
- **Google Drive** for backups, member documents, and WhatsApp media archiving. See [`docs/google-drive-integration.md`](docs/google-drive-integration.md) for setup and the phased plan.

Keep all API keys, tokens, app secrets, and database passwords in environment variables. Do not commit them.
