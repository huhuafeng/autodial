const WebSocket = require('ws')
const express = require('express')
const multer = require('multer')
const path = require('path')
const fs = require('fs')
const http = require('http')
const crypto = require('crypto')

const PORT = process.env.PORT || 8080
const UPLOAD_DIR = path.join(__dirname, 'uploads')

if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOAD_DIR),
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname)
    cb(null, `${Date.now()}_${crypto.randomBytes(4).toString('hex')}${ext}`)
  }
})
const upload = multer({ storage })

const app = express()
app.use(express.static(path.join(__dirname, 'public')))

app.post('/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'no file' })
  const url = `/uploads/${req.file.filename}`
  console.log(`[UPLOAD] saved: ${req.file.filename} (session: ${req.body.callSession || 'N/A'})`)
  res.json({ success: true, url })
})

app.use('/uploads', express.static(UPLOAD_DIR))

const server = http.createServer(app)
const wss = new WebSocket.Server({ server })

const clients = { web: null, phone: null }
const PING_INTERVAL = 30000
const PONG_TIMEOUT = 10000

function log(dir, msg) {
  const ts = new Date().toLocaleTimeString()
  console.log(`[${ts}] [${dir}] ${msg}`)
}

function send(ws, data) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data))
  }
}

function heartbeat(ws) {
  if (ws._pongTimer) clearTimeout(ws._pongTimer)
  ws.isAlive = false
  ws.ping()
  ws._pongTimer = setTimeout(() => {
    log('SYS', 'pong timeout, closing')
    ws.terminate()
  }, PONG_TIMEOUT)
}

wss.on('connection', (ws, req) => {
  const addr = req.socket.remoteAddress
  let role = null

  ws.isAlive = true
  ws.on('pong', () => {
    ws.isAlive = true
    if (ws._pongTimer) clearTimeout(ws._pongTimer)
  })

  log('SYS', `new connection from ${addr}`)

  ws.on('message', (raw) => {
    let msg
    try { msg = JSON.parse(raw) } catch { return }

    if (msg.type === 'register') {
      role = msg.role
      if (role === 'web') {
        clients.web = ws
        log('SYS', 'web client registered')
        send(ws, { type: 'registered', role: 'web' })
      } else if (role === 'phone') {
        clients.phone = ws
        log('SYS', 'phone client registered')
        send(ws, { type: 'registered', role: 'phone' })
      }
      return
    }

    if (msg.type === 'pong') return

    if (msg.type === 'dial') {
      log('WEB', `dial request: ${msg.phone}`)
      if (clients.phone) {
        send(clients.phone, msg)
      } else {
        send(ws, { type: 'error', message: 'phone not connected' })
      }
      return
    }

    if (msg.type === 'status') {
      log('PHONE', `status: ${msg.status} phone=${msg.phone}`)
      if (clients.web) send(clients.web, msg)
      return
    }

    if (msg.type === 'record_ready') {
      log('PHONE', `record ready: ${msg.callSession} ${msg.fileName}`)
      if (clients.web) send(clients.web, msg)
      return
    }
  })

  ws.on('close', () => {
    log('SYS', `${role || 'unknown'} disconnected`)
    if (role === 'web') clients.web = null
    else if (role === 'phone') clients.phone = null
  })

  ws.on('error', () => {})
})

const pingTimer = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (!ws.isAlive) return ws.terminate()
    heartbeat(ws)
  })
}, PING_INTERVAL)

wss.on('close', () => clearInterval(pingTimer))

server.listen(PORT, () => {
  console.log(`\n  AutoDial Server running`)
  console.log(`  WebSocket : ws://0.0.0.0:${PORT}`)
  console.log(`  Web Page  : http://0.0.0.0:${PORT}`)
  console.log(`  Upload    : POST http://0.0.0.0:${PORT}/upload\n`)
})
