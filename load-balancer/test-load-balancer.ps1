param(
    [string]$LbHost = "localhost",
    [int]$LbPort = 9090,
    [int]$RequestCount = 100
)

$baseUrl = "http://${LbHost}:${LbPort}"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Coffee Payment Sync - Load Balancer Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Load balancer: $baseUrl"

# Step 1: Check load balancer health
Write-Host "`n--- Step 1: Checking load balancer health ---" -ForegroundColor Yellow
try {
    $lbHealth = Invoke-RestMethod -Uri "$baseUrl/lb/health" -TimeoutSec 5
    Write-Host "  /lb/health => $($lbHealth.status)" -ForegroundColor Green
    Write-Host "  Healthy backends: $($lbHealth.healthyBackends) / $($lbHealth.totalBackends)"
}
catch {
    Write-Host "  ERROR: Load balancer not reachable at $baseUrl" -ForegroundColor Red
    Write-Host "  Make sure docker-compose is running (docker-compose up --build)"
    exit 1
}

# Step 2: Check backend stats
Write-Host "`n--- Step 2: Checking backend instances ---" -ForegroundColor Yellow
$stats = Invoke-RestMethod -Uri "$baseUrl/lb/stats" -TimeoutSec 5
Write-Host "  Total backends: $($stats.backends.Count)"
foreach ($b in $stats.backends) {
    Write-Host "  $($b.name) ($($b.instanceId)):"
    Write-Host "    Healthy:      $($b.healthy)"
    Write-Host "    Score:        $([math]::Round($b.score, 4))"
    Write-Host "    Avg Resp:     $($b.avgResponseTimeMs)ms"
    Write-Host "    Active Reqs:  $($b.activeRequests)"
    Write-Host "    Error Rate:   $($b.errorRate)"
    Write-Host "    CPU Load:     $($b.cpuLoad)"
    Write-Host "    Heap:         $($b.heapUsedMb)MB / $($b.heapMaxMb)MB"
}

# Step 3: Run load test
Write-Host "`n--- Step 3: Sending $RequestCount payment sync requests ---" -ForegroundColor Yellow

$coffeeTypes = @('LATTE', 'ESPRESSO', 'CAPPUCCINO', 'AMERICANO', 'MOCHA')
$currencies = @('EUR', 'USD', 'GBP')

$success = 0
$failed = 0
$timings = @()

for ($i = 0; $i -lt $RequestCount; $i++) {
    $payment = @{
        coffeeType = $coffeeTypes[(Get-Random -Maximum $coffeeTypes.Length)]
        price = "{0:N2}" -f (Get-Random -Minimum 1.5 -Maximum 6.5)
        currency = $currencies[(Get-Random -Maximum $currencies.Length)]
        idempotencyKey = "test-$([DateTime]::Now.Ticks)-$(Get-Random)"
    }

    $body = @{ payments = @($payment) } | ConvertTo-Json

    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $res = Invoke-RestMethod -Uri "$baseUrl/api/payments/sync" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 15
        $sw.Stop()
        $timings += $sw.ElapsedMilliseconds
        $success++
        Write-Host "✓" -NoNewline -ForegroundColor Green
    }
    catch {
        $failed++
        Write-Host "✗" -NoNewline -ForegroundColor Red
    }

    if (($i + 1) % 50 -eq 0) { Write-Host " ($($i+1)/$RequestCount)" }
}
Write-Host "`n"

$avgTime = if ($timings.Count -gt 0) { [math]::Round(($timings | Measure-Object -Average).Average, 0) } else { "N/A" }
$minTime = if ($timings.Count -gt 0) { ($timings | Measure-Object -Minimum).Minimum } else { "N/A" }
$maxTime = if ($timings.Count -gt 0) { ($timings | Measure-Object -Maximum).Maximum } else { "N/A" }

Write-Host "=== Load Test Results ===" -ForegroundColor Cyan
Write-Host "  Total requests: $RequestCount"
Write-Host "  Successful:     $success"
Write-Host "  Failed:         $failed"
Write-Host "  Avg response:   ${avgTime}ms"
Write-Host "  Min response:   ${minTime}ms"
Write-Host "  Max response:   ${maxTime}ms"

# Step 4: Show final distribution
Write-Host "`n--- Step 4: Request distribution after load test ---" -ForegroundColor Yellow
$finalStats = Invoke-RestMethod -Uri "$baseUrl/lb/stats" -TimeoutSec 5
$totalReqs = ($finalStats.backends | Measure-Object -Property totalRequests -Sum).Sum

foreach ($b in $finalStats.backends) {
    $pct = if ($totalReqs -gt 0) { [math]::Round(($b.totalRequests / $totalReqs) * 100, 1) } else { 0.0 }
    Write-Host "  $($b.name): $($b.totalRequests) requests ($($pct)%) | score: $([math]::Round($b.score, 4)) | avg: $($b.avgResponseTimeMs)ms | active: $($b.activeRequests)"
}

Write-Host "`nLoad balancer test completed!" -ForegroundColor Green
