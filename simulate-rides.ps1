# simulate-rides.ps1
# Usage: .\simulate-rides.ps1 -Count 50 -DelayMs 200
# Usage: .\simulate-rides.ps1 -Count 100 -Parallel -Throttle 20

param(
    [string]$BaseUrl     = "http://localhost:8081/api/rides/request",
    [int]   $Count       = 50,
    [int]   $DelayMs     = 200,
    [string]$IdPrefix    = "RIDER",
    [double]$CenterLat   = 18.5204,
    [double]$CenterLon   = 73.8567,
    [double]$Spread      = 0.03,
    [switch]$Parallel,
    [int]   $Throttle    = 10
)

$RideTypes   = @("ECONOMY", "PREMIUM", "XL")
$PickupNames = @("Pune Station", "Shivajinagar", "Kothrud", "Baner", "Hinjewadi", "Wakad", "Viman Nagar")
$DropNames   = @("Airport", "FC Road", "Magarpatta", "Hadapsar", "Camp", "Koregaon Park", "Deccan")

$Results = [System.Collections.Concurrent.ConcurrentBag[object]]::new()
$Start   = Get-Date

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     🚕 Ride-Hailing Dispatch - Load Tester      ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host "  Endpoint : $BaseUrl"
Write-Host "  Count    : $Count"
Write-Host "  Delay    : ${DelayMs}ms"
Write-Host "  Parallel : $Parallel"
Write-Host ""

function Send-RideRequest {
    param([int]$Index)

    $RiderId   = "$IdPrefix-$(Get-Random -Minimum 1000 -Maximum 9999)"
    $RiderName = "TestRider_$Index"
    $RideType  = $RideTypes[(Get-Random -Minimum 0 -Maximum $RideTypes.Length)]
    $Pickup    = $PickupNames[(Get-Random -Minimum 0 -Maximum $PickupNames.Length)]
    $Drop      = $DropNames[(Get-Random -Minimum 0 -Maximum $DropNames.Length)]

    $pLat = [math]::Round($CenterLat + (Get-Random -Minimum -100 -Maximum 100) * $Spread / 100, 6)
    $pLon = [math]::Round($CenterLon + (Get-Random -Minimum -100 -Maximum 100) * $Spread / 100, 6)
    $dLat = [math]::Round($CenterLat + (Get-Random -Minimum -100 -Maximum 100) * $Spread / 100, 6)
    $dLon = [math]::Round($CenterLon + (Get-Random -Minimum -100 -Maximum 100) * $Spread / 100, 6)

    $Body = @{
        riderId       = $RiderId
        riderName     = $RiderName
        pickupLat     = $pLat
        pickupLon     = $pLon
        dropLat       = $dLat
        dropLon       = $dLon
        pickupAddress = $Pickup
        dropAddress   = $Drop
        rideType      = $RideType
    } | ConvertTo-Json

    $t0 = Get-Date
    try {
        $Response = Invoke-RestMethod -Uri $BaseUrl -Method POST `
            -ContentType "application/json" -Body $Body -TimeoutSec 10
        $ms = [int]((Get-Date) - $t0).TotalMilliseconds
        Write-Host "  [$Index] ✅ $($Response.rideId) | $RideType | ${ms}ms" -ForegroundColor Green
        return @{ Index=$Index; Success=$true; RideId=$Response.rideId; Type=$RideType; LatencyMs=$ms }
    } catch {
        $ms = [int]((Get-Date) - $t0).TotalMilliseconds
        Write-Host "  [$Index] ❌ FAILED | $RideType | ${ms}ms | $($_.Exception.Message)" -ForegroundColor Red
        return @{ Index=$Index; Success=$false; RideId="N/A"; Type=$RideType; LatencyMs=$ms }
    }
}

# ─── Run Tests ────────────────────────────────────────────────────────────────

if ($Parallel) {
    Write-Host "Running in PARALLEL mode (throttle: $Throttle)..." -ForegroundColor Yellow
    $Jobs = 1..$Count | ForEach-Object -ThrottleLimit $Throttle -Parallel {
        $fn   = $using:Function:Send-RideRequest
        $idx  = $_
        $url  = $using:BaseUrl
        $pre  = $using:IdPrefix
        $clat = $using:CenterLat
        $clon = $using:CenterLon
        $sprd = $using:Spread
        # Re-invoke function in parallel scope
        & $fn -Index $idx
    }
    $Jobs | ForEach-Object { $Results.Add($_) }
} else {
    Write-Host "Running in SEQUENTIAL mode..." -ForegroundColor Yellow
    for ($i = 1; $i -le $Count; $i++) {
        $r = Send-RideRequest -Index $i
        $Results.Add($r)
        if ($DelayMs -gt 0) { Start-Sleep -Milliseconds $DelayMs }
    }
}

# ─── Summary ──────────────────────────────────────────────────────────────────

$Elapsed   = [int]((Get-Date) - $Start).TotalSeconds
$Successes = ($Results | Where-Object { $_.Success }).Count
$Failures  = ($Results | Where-Object { -not $_.Success }).Count
$Latencies = ($Results | Where-Object { $_.Success } | Select-Object -ExpandProperty LatencyMs)
$AvgLat    = if ($Latencies) { [int]($Latencies | Measure-Object -Average).Average } else { 0 }
$MaxLat    = if ($Latencies) { ($Latencies | Measure-Object -Maximum).Maximum } else { 0 }

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                   📊 RESULTS                    ║" -ForegroundColor Cyan
Write-Host "╠══════════════════════════════════════════════════╣" -ForegroundColor Cyan
Write-Host "║  Total    : $Count" -ForegroundColor White
Write-Host "║  Success  : $Successes ✅" -ForegroundColor Green
Write-Host "║  Failed   : $Failures ❌" -ForegroundColor Red
Write-Host "║  Avg Lat  : ${AvgLat}ms" -ForegroundColor Yellow
Write-Host "║  Max Lat  : ${MaxLat}ms" -ForegroundColor Yellow
Write-Host "║  Duration : ${Elapsed}s" -ForegroundColor White
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan

# ─── CSV Export ───────────────────────────────────────────────────────────────

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$CsvPath   = "simulate-rides-result-$Timestamp.csv"
$Results | ForEach-Object {
    [PSCustomObject]@{
        Index     = $_.Index
        Success   = $_.Success
        RideId    = $_.RideId
        RideType  = $_.Type
        LatencyMs = $_.LatencyMs
    }
} | Export-Csv -Path $CsvPath -NoTypeInformation
Write-Host ""
Write-Host "  📁 Results saved to: $CsvPath" -ForegroundColor Magenta
