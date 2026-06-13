const http = require('http');

const LB_HOST = process.env.LB_HOST || 'localhost';
const LB_PORT = parseInt(process.env.LB_PORT || '9090');

function makeRequest(path) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const req = http.get(`http://${LB_HOST}:${LB_PORT}${path}`, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body, time: Date.now() - start, instance: res.headers['x-instance'] || 'unknown' }));
    });
    req.on('error', reject);
    req.setTimeout(5000, () => { req.destroy(); reject(new Error('Timeout')); });
  });
}

async function getStats() {
  try {
    const res = await makeRequest('/lb/stats');
    return JSON.parse(res.body);
  } catch (e) {
    return null;
  }
}

async function main() {
  console.log('=== Smart Load Balancer Demo ===\n');

  // Check initial stats
  console.log('--- Initial Backend States ---');
  let stats = await getStats();
  stats.backends.forEach(b => console.log(`  ${b.name} (${b.instanceId}): score=${b.score.toFixed(4)}, avg=${b.avgResponseTimeMs}ms, heap=${b.heapUsedMb}MB/${b.heapMaxMb}MB`));
  console.log(`  Best backend: ${stats.selected}\n`);

  // Send 60 health check requests to demonstrate distribution
  const count = 60;
  console.log(`Sending ${count} health check requests through load balancer...`);
  const startTime = Date.now();
  const results = [];

  for (let i = 0; i < count; i++) {
    try {
      const res = await makeRequest('/api/health');
      const body = JSON.parse(res.body);
      results.push({ time: res.time, instanceId: body.instanceId });
      process.stdout.write('.');
    } catch (e) {
      results.push({ time: -1, instanceId: 'error' });
      process.stdout.write('E');
    }
    if ((i + 1) % 20 === 0) console.log(` (${i + 1}/${count})`);
  }
  console.log(`\nDone in ${Date.now() - startTime}ms\n`);

  // Analyze distribution
  const dist = {};
  results.forEach(r => {
    if (!dist[r.instanceId]) dist[r.instanceId] = { count: 0, times: [] };
    dist[r.instanceId].count++;
    dist[r.instanceId].times.push(r.time);
  });

  console.log('--- Request Distribution ---');
  Object.entries(dist).forEach(([id, data]) => {
    const avgTime = (data.times.reduce((a, b) => a + b, 0) / data.times.length).toFixed(0);
    const pct = ((data.count / count) * 100).toFixed(1);
    console.log(`  ${id}: ${data.count} requests (${pct}%) | avg ${avgTime}ms`);
  });

  // Show final LB stats
  console.log('\n--- Load Balancer Stats After Test ---');
  stats = await getStats();
  const totalReqs = stats.backends.reduce((s, b) => s + b.totalRequests, 0);
  stats.backends.forEach(b => {
    const pct = totalReqs > 0 ? ((b.totalRequests / totalReqs) * 100).toFixed(1) : '0.0';
    console.log(`  ${b.name} (${b.instanceId}):`);
    console.log(`    Requests:     ${b.totalRequests} (${pct}%)`);
    console.log(`    Score:        ${b.score.toFixed(4)}`);
    console.log(`    Avg Resp:     ${b.avgResponseTimeMs}ms`);
    console.log(`    Active Reqs:  ${b.activeRequests}`);
    console.log(`    Error Rate:   ${b.errorRate}`);
    console.log(`    Heap:         ${b.heapUsedMb}MB / ${b.heapMaxMb}MB`);
    console.log(`    CPU:          ${b.cpuLoad}`);
  });
  console.log(`\n✅ Load balancer smart routing demo complete!`);
}

main().catch(console.error);
