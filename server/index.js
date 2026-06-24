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
app.use(express.json())
app.use(express.static(path.join(__dirname, 'public')))

// ───── Mock data ─────
const DAY = 86400000
const now = Date.now()
const mockRecords = [
  // today
  ...[1,2,3,4,5,6,7,8,9,10].map(i => ({
    id: i, phone: `1380000${String(1000+i).slice(1)}`,
    callSession: crypto.randomUUID(),
    duration: [45,87,120,32,210,55,18,93,140,67][i-1],
    status: i <= 8 ? 'answered' : 'ended',
    recordingUrl: i <= 8 ? `/uploads/rec_${i}.mp3` : null,
    timestamp: now - (10-i) * 180000,
  })),
  // yesterday
  ...[11,12,13,14,15,16,17,18].map(i => ({
    id: i, phone: `1390000${String(1000+i).slice(1)}`,
    callSession: crypto.randomUUID(),
    duration: [30,150,60,95,0,45,200,80][i-11],
    status: [1,2,3,5,7].includes(i) ? 'answered' : 'ended',
    recordingUrl: i <= 15 ? `/uploads/rec_${i}.mp3` : null,
    timestamp: now - DAY - (18-i) * 240000,
  })),
]

function todayTs() {
  const d = new Date(); d.setHours(0,0,0,0); return d.getTime()
}

// ───── API ─────

// 今日/昨日统计
app.get('/api/stats', (req, res) => {
  const start = todayTs()
  const end = start + DAY

  function calc(records) {
    if (!records.length) return { dialout:0, callLong:0, avg:0, connect:0, connect40:0, connectRate:0 }
    const dialout = records.length
    const callLong = records.reduce((s,r) => s + r.duration, 0)
    const connect = records.filter(r => r.status === 'answered').length
    const connect40 = records.filter(r => r.duration >= 40).length
    return {
      dialout,
      callLong,
      avg: Math.round(callLong / dialout),
      connect,
      connect40,
      connectRate: dialout ? Math.round(connect / dialout * 100) : 0,
    }
  }

  res.json({
    today: calc(mockRecords.filter(r => r.timestamp >= start && r.timestamp < end)),
    yesterday: calc(mockRecords.filter(r => r.timestamp >= start - DAY && r.timestamp < start)),
  })
})

// 分页记录列表
app.get('/api/records', (req, res) => {
  let list = [...mockRecords].sort((a,b) => b.timestamp - a.timestamp)
  const page = Math.max(1, parseInt(req.query.page) || 1)
  const limit = Math.min(100, Math.max(1, parseInt(req.query.limit) || 20))
  const total = list.length
  const offset = (page - 1) * limit
  res.json({ total, page, limit, list: list.slice(offset, offset + limit) })
})

// App 上报通话记录
app.post('/api/records/sync', (req, res) => {
  const body = Array.isArray(req.body) ? req.body : [req.body]
  let added = 0
  body.forEach(rec => {
    if (rec.callSession && !mockRecords.find(r => r.callSession === rec.callSession)) {
      mockRecords.push({ id: mockRecords.length + 1, ...rec })
      added++
    }
  })
  log('API', `synced ${added} records`)
  res.json({ success: true, added })
})

// 拉取缺失记录 (since timestamp)
app.get('/api/records/pull', (req, res) => {
  const since = parseInt(req.query.since) || 0
  const list = mockRecords.filter(r => r.timestamp > since).sort((a,b) => b.timestamp - a.timestamp)
  res.json({ list })
})

// ───── Upload ─────

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
