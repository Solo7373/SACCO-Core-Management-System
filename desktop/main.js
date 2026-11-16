const { app, BrowserWindow, dialog, shell } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');
const fs = require('fs');

let mainWindow = null;
let backendProcess = null;
let backendReady = false;

const BACKEND_PORT = 8081;
const HEALTH_URL = `http://127.0.0.1:${BACKEND_PORT}/api/auth/login`;

function findJava() {
  // 1. Check JAVA_HOME
  if (process.env.JAVA_HOME) {
    const javaExe = path.join(process.env.JAVA_HOME, 'bin', 'java.exe');
    if (fs.existsSync(javaExe)) return javaExe;
  }
  // 2. Check PATH
  const pathDirs = (process.env.PATH || '').split(path.delimiter);
  for (const dir of pathDirs) {
    const javaExe = path.join(dir, 'java.exe');
    if (fs.existsSync(javaExe)) return javaExe;
  }
  // 3. Common Windows install locations
  const commonPaths = [
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21*\\bin\\java.exe',
    'C:\\Program Files\\Java\\jdk-21*\\bin\\java.exe',
    'C:\\Program Files\\PostgreSQL\\16\\bin\\java.exe',
  ];
  // Simple glob-like search in Program Files
  const programFiles = ['C:\\Program Files', 'C:\\Program Files (x86)'];
  for (const pf of programFiles) {
    try {
      const entries = fs.readdirSync(pf);
      for (const entry of entries) {
        if (entry.toLowerCase().includes('jdk') || entry.toLowerCase().includes('jre')) {
          const javaExe = path.join(pf, entry, 'bin', 'java.exe');
          if (fs.existsSync(javaExe)) return javaExe;
        }
      }
    } catch (e) { /* ignore */ }
  }
  return null;
}

function getBackendJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'backend', 'sacco-core-management.jar');
  }
  // Development: use the built jar
  return path.join(__dirname, '..', 'target', 'sacco-core-management-1.0-SNAPSHOT.jar');
}

function startBackend() {
  const javaPath = findJava();
  if (!javaPath) {
    dialog.showErrorBox(
      'Java Not Found',
      'SCMS requires Java 21 or later. Please install a JDK from https://adoptium.net and restart the application.'
    );
    app.quit();
    return;
  }

  const jarPath = getBackendJarPath();
  if (!fs.existsSync(jarPath)) {
    dialog.showErrorBox(
      'Backend Not Found',
      `The backend JAR was not found at:\n${jarPath}\n\nPlease rebuild the application.`
    );
    app.quit();
    return;
  }

  console.log('Starting backend with:', javaPath, jarPath);

  backendProcess = spawn(javaPath, [
    '-Xmx512m',
    '-jar', jarPath
  ], {
    cwd: path.dirname(jarPath),
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  });

  backendProcess.stdout.on('data', (data) => {
    console.log('[backend]', data.toString().trim());
  });
  backendProcess.stderr.on('data', (data) => {
    console.error('[backend-err]', data.toString().trim());
  });
  backendProcess.on('exit', (code) => {
    console.log('Backend exited with code', code);
    backendProcess = null;
    if (mainWindow && !mainWindow.isDestroyed()) {
      dialog.showErrorBox('Backend Stopped', 'The backend server has stopped. The application will close.');
      app.quit();
    }
  });
}

function checkBackendReady(callback, attempts = 0) {
  if (attempts > 180) {
    dialog.showErrorBox(
      'Startup Timeout',
      'The backend server did not start within 3 minutes. Please check the logs and try again.'
    );
    app.quit();
    return;
  }

  const req = http.get(HEALTH_URL, (res) => {
    // Any response means the server is up (even 401/403/405)
    backendReady = true;
    callback();
  });
  req.on('error', () => {
    setTimeout(() => checkBackendReady(callback, attempts + 1), 1000);
  });
  req.setTimeout(2000, () => {
    req.destroy();
    setTimeout(() => checkBackendReady(callback, attempts + 1), 1000);
  });
}

function createWindow() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.focus();
    return;
  }

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    title: 'SCMS - SACCO Core Management System',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    },
    show: false
  });

  const appUrl = `http://127.0.0.1:${BACKEND_PORT}/`;

  mainWindow.loadURL(appUrl);

  // The backend can take up to a minute to start. If the first page load
  // happens too early, retry instead of leaving Chromium's error page open.
  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    if (validatedURL && validatedURL.startsWith(appUrl) && mainWindow && !mainWindow.isDestroyed()) {
      console.log(`Page load failed (${errorCode} ${errorDescription}); retrying...`);
      setTimeout(() => {
        if (mainWindow && !mainWindow.isDestroyed()) {
          mainWindow.loadURL(appUrl);
        }
      }, 1500);
    }
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  startBackend();
  checkBackendReady(() => {
    createWindow();
  });
});

app.on('window-all-closed', () => {
  if (backendProcess) {
    backendProcess.kill();
    backendProcess = null;
  }
  app.quit();
});

app.on('before-quit', () => {
  if (backendProcess) {
    backendProcess.kill();
    backendProcess = null;
  }
});

app.on('activate', () => {
  // Do not open a window before the backend is ready. On Windows this event
  // can fire while startup is still in progress and would show a connection
  // error page.
  if (backendReady && BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
