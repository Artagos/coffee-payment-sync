const http = require('http');
const url = require('url');

// Parse backends from env or use defaults
const BACKEND_CONFIG = process.env.BACKENDS
  ? process.env.BACKENDS.split(',').map((b, i) => {
      const [host, port] = b.split(':');
      return { host, port: parseInt(port), name: `Instance-${i + 1}` };
    })
  : [
      { host: 'localhost', port: 9091, name: 'Instance-1' },
      { host: 'localhost', port: 9092, name: 'Instance-2' },
      { host: 'localhost', port: 9093, name: 'Instance-3' },
    ];

const LB_PORT = parseInt(process.env.LB_PORT || '9090');
const HEALTH_INTERVAL = parseInt(process.env.HEALTH_INTERVAL || '5000');

class BackendState {
  constructor(config) {
    this.config = config;
    this.healthy = false;
    this.responseTimes = [];
    this.activeRequests = 0;
    this.totalRequests = 0;
    this.errorCount = 0;
    this.heapUsed = 0;
    this.heapMax = 1;
    this.cpuLoad = 0;
    this.instanceId = 'unknown';
  }

  get avgResponseTime() {
    if (this.responseTimes.length === 0) return 100;
    return this.responseTimes.reduce((a, b) => a + b, 0) / this.responseTimes.length;
  }

  get errorRate() {
    if (this.totalRequests === 0) return 0;
    return this.errorCount / this.totalRequests;
  }

  get score() {
    if (!this.healthy) return Infinity;

    const w1 = 0.30, w2 = 0.25, w3 = 0.20, w4 = 0.15, w5 = 0.10;
    const rt  = Math.min(this.avgResponseTime / 2000, 1);
    const act = Math.min(this.activeRequests / 20, 1);
    const err = this.errorRate;
    const cpu = Math.min(this.cpuLoad / 1.0, 1);
    const mem = Math.min(this.heapMax > 0 ? this.heapUsed / this.heapMax : 0, 1);

    return w1 * rt + w2 * act + w3 * err + w4 * cpu + w5 * mem;
  }

  recordResponseTime(ms) {
    this.responseTimes.push(ms);
    if (this.responseTimes.length > 50) this.responseTimes.shift();
  }

  recordError() {
    this.errorCount++;
  }
}

const backends = BACKEND_CONFIG.map(c => new BackendState(c));

function checkHealth(backend) {
  return new Promise(resolve => {
    const start = Date.now();
    const req = http.get(`http://${backend.config.host}:${backend.config.port}/api/health`, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        const elapsed = Date.now() - start;
        try {
          const data = JSON.parse(body);
          backend.instanceId = data.instanceId || backend.config.name;
        } catch (e) { /* ignore */ }
        backend.healthy = res.statusCode === 200;
        backend.recordResponseTime(elapsed);
        resolve();
      });
    });
    req.on('error', () => {
      backend.healthy = false;
      resolve();
    });
    req.setTimeout(3000, () => {
      req.destroy();
      backend.healthy = false;
      resolve();
    });
  });
}

function fetchStats(backend) {
  return new Promise(resolve => {
    const req = http.get(`http://${backend.config.host}:${backend.config.port}/api/stats`, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        if (res.statusCode === 200) {
          try {
            const data = JSON.parse(body);
            backend.heapUsed = data.heapUsed || 0;
            backend.heapMax = data.heapMax || 1;
            backend.cpuLoad = data.cpuLoad || 0;
            backend.instanceId = data.instanceId || backend.instanceId;
          } catch (e) { /* ignore */ }
        }
        resolve();
      });
    });
    req.on('error', () => resolve());
    req.setTimeout(3000, () => { req.destroy(); resolve(); });
  });
}

function selectBackend() {
  const healthy = backends.filter(b => b.healthy);
  if (healthy.length === 0) return null;

  const maxScore = Math.max(...healthy.map(b => b.score));
  const weights = healthy.map(b => Math.max(maxScore - b.score + 0.001, 0.001));
  const totalWeight = weights.reduce((a, b) => a + b, 0);

  let random = Math.random() * totalWeight;
  for (let i = 0; i < healthy.length; i++) {
    random -= weights[i];
    if (random <= 0) return healthy[i];
  }
  return healthy[healthy.length - 1];
}

function proxyRequest(clientReq, clientRes) {
  const backend = selectBackend();
  if (!backend) {
    clientRes.writeHead(503, { 'Content-Type': 'application/json' });
    clientRes.end(JSON.stringify({ error: 'No healthy backends available' }));
    return;
  }

  backend.activeRequests++;
  backend.totalRequests++;

  const parsed = url.parse(clientReq.url);
  const options = {
    hostname: backend.config.host,
    port: backend.config.port,
    path: parsed.path,
    method: clientReq.method,
    headers: { ...clientReq.headers },
    timeout: 30000,
  };

  const start = Date.now();
  const proxyReq = http.request(options, proxyRes => {
    const elapsed = Date.now() - start;
    backend.recordResponseTime(elapsed);

    if (proxyRes.statusCode >= 500) {
      backend.recordError();
    }

    clientRes.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(clientRes);
    backend.activeRequests--;
  });

  proxyReq.on('error', err => {
    backend.recordError();
    backend.healthy = false;
    backend.activeRequests--;
    if (!clientRes.headersSent) {
      clientRes.writeHead(502, { 'Content-Type': 'application/json' });
      clientRes.end(JSON.stringify({ error: 'Bad gateway', message: err.message }));
    }
  });

  proxyReq.on('timeout', () => {
    proxyReq.destroy();
    backend.recordError();
    backend.activeRequests--;
    if (!clientRes.headersSent) {
      clientRes.writeHead(504, { 'Content-Type': 'application/json' });
      clientRes.end(JSON.stringify({ error: 'Gateway timeout' }));
    }
  });

  clientReq.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
  const parsed = url.parse(req.url);

  // Load balancer's own admin endpoints
  if (parsed.pathname === '/lb/stats') {
    const data = backends.map(b => ({
      name: b.config.name,
      host: `${b.config.host}:${b.config.port}`,
      instanceId: b.instanceId,
      healthy: b.healthy,
      score: b.score,
      avgResponseTimeMs: Math.round(b.avgResponseTime),
      activeRequests: b.activeRequests,
      totalRequests: b.totalRequests,
      errorRate: Math.round(b.errorRate * 100) + '%',
      cpuLoad: Math.round(b.cpuLoad * 100) / 100,
      heapUsedMb: Math.round(b.heapUsed / 1024 / 1024),
      heapMaxMb: Math.round(b.heapMax / 1024 / 1024),
    }));
    res.writeHead(200, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    });
    res.end(JSON.stringify({ backends: data, selected: selectBackend()?.config.name || null }, null, 2));
    return;
  }

  if (parsed.pathname === '/lb/health') {
    const healthy = backends.filter(b => b.healthy).length;
    const total = backends.length;
    res.writeHead(healthy > 0 ? 200 : 503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: healthy > 0 ? 'UP' : 'DOWN',
      healthyBackends: healthy,
      totalBackends: total,
    }));
    return;
  }

  proxyRequest(req, res);
});

async function runHealthChecks() {
  for (const backend of backends) {
    await checkHealth(backend);
    if (backend.healthy) {
      await fetchStats(backend);
    }
  }
}

server.listen(LB_PORT, async () => {
  console.log(`[Load Balancer] Listening on port ${LB_PORT}`);
  console.log(`[Load Balancer] Backends: ${BACKEND_CONFIG.map(b => `${b.host}:${b.port}`).join(', ')}`);
  console.log(`[Load Balancer] Admin: http://localhost:${LB_PORT}/lb/stats`);

  await runHealthChecks();
  setInterval(runHealthChecks, HEALTH_INTERVAL);
});
