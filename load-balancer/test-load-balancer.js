const http = require('http');

const LB_HOST = process.env.LB_HOST || 'localhost';
const LB_PORT = parseInt(process.env.LB_PORT || '9090');

const PAYMENT_TYPES = ['LATTE', 'ESPRESSO', 'CAPPUCCINO', 'AMERICANO', 'MOCHA'];
const CURRENCIES = ['EUR', 'USD', 'GBP'];

function randomPayment() {
  return {
    coffeeType: PAYMENT_TYPES[Math.floor(Math.random() * PAYMENT_TYPES.length)],
    price: (Math.random() * 5 + 1.5).toFixed(2),
    currency: CURRENCIES[Math.floor(Math.random() * CURRENCIES.length)],
    idempotencyKey: `test-${Date.now()}-${Math.random().toString(36).substr(2, 8)}`,
  };
}

function makeRequest(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const options = {
      hostname: LB_HOST,
      port: LB_PORT,
      path,
      method,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': data ? Buffer.byteLength(data) : 0,
      },
      timeout: 15000,
    };

    const start = Date.now();
    const req = http.request(options, res => {
      let responseBody = '';
      res.on('data', chunk => responseBody += chunk);
      res.on('end', () => {
        const elapsed = Date.now() - start;
        resolve({ status: res.statusCode, body: responseBody, time: elapsed });
      });
    });

    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('Timeout')); });
    if (data) req.write(data);
    req.end();
  });
}

async function getLBStats() {
  const res = await makeRequest('GET', '/lb/stats');
  return JSON.parse(res.body);
}

async function runLoadTest(count) {
  console.log(`\n=== Sending ${count} payment sync requests to load balancer ===\n`);

  let success = 0, failed = 0;
  let totalTime = 0;
  const timings = [];

  for (let i = 0; i < count; i++) {
    const payment = randomPayment();
    try {
      const res = await makeRequest('POST', '/api/payments/sync', { payments: [payment] });
      totalTime += res.time;
      timings.push(res.time);
      if (res.status < 500) {
        success++;
        process.stdout.write('✓');
      } else {
        failed++;
        process.stdout.write('✗');
      }
    } catch (err) {
      failed++;
      process.stdout.write('✗');
    }
    if ((i + 1) % 50 === 0) console.log(` (${i + 1}/${count})`);
  }
  console.log(`\n`);

  const avgTime = timings.length > 0 ? (totalTime / timings.length).toFixed(0) : 'N/A';
  console.log('=== Load Test Results ===');
  console.log(`  Total requests: ${count}`);
  console.log(`  Successful:     ${success}`);
  console.log(`  Failed:         ${failed}`);
  console.log(`  Avg response:   ${avgTime}ms`);
  console.log(`  Min response:   ${timings.length > 0 ? Math.min(...timings) : 'N/A'}ms`);
  console.log(`  Max response:   ${timings.length > 0 ? Math.max(...timings) : 'N/A'}ms`);
}

async function main() {
  console.log('========================================');
  console.log('  Coffee Payment Sync - Load Balancer Test');
  console.log('========================================');

  console.log(`\nLoad balancer: http://${LB_HOST}:${LB_PORT}`);

  // Step 1: Check load balancer health
  console.log('\n--- Step 1: Checking load balancer health ---');
  try {
    const lbHealth = await makeRequest('GET', '/lb/health');
    console.log(`  /lb/health => ${lbHealth.status}: ${lbHealth.body}`);
  } catch (err) {
    console.error('  ERROR: Load balancer not reachable:', err.message);
    console.error('  Make sure docker-compose is running (docker-compose up --build)');
    process.exit(1);
  }

  // Step 2: Check backend stats
  console.log('\n--- Step 2: Checking backend instances ---');
  const stats = await getLBStats();
  console.log(`  Total backends: ${stats.backends.length}`);
  stats.backends.forEach(b => {
    console.log(`  ${b.name} (${b.instanceId}):`);
    console.log(`    Healthy:      ${b.healthy}`);
    console.log(`    Score:        ${typeof b.score === 'number' ? b.score.toFixed(4) : b.score}`);
    console.log(`    Avg Resp:     ${b.avgResponseTimeMs}ms`);
    console.log(`    Active Reqs:  ${b.activeRequests}`);
    console.log(`    Error Rate:   ${b.errorRate}`);
    console.log(`    CPU Load:     ${b.cpuLoad}`);
    console.log(`    Heap:         ${b.heapUsedMb}MB / ${b.heapMaxMb}MB`);
  });

  // Step 3: Run load test
  await runLoadTest(100);

  // Step 4: Show final distribution
  console.log('\n--- Step 4: Request distribution after load test ---');
  const finalStats = await getLBStats();
  const totalReqs = finalStats.backends.reduce((sum, b) => sum + b.totalRequests, 0);
  finalStats.backends.forEach(b => {
    const pct = totalReqs > 0 ? ((b.totalRequests / totalReqs) * 100).toFixed(1) : '0.0';
    console.log(`  ${b.name}: ${b.totalRequests} requests (${pct}%) | score: ${b.score.toFixed(4)} | avg: ${b.avgResponseTimeMs}ms | active: ${b.activeRequests}`);
  });

  console.log('\n✅ Load balancer test completed!');
}

main().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
