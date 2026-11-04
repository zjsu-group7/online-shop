param(
    [string]$BaseUrl = "http://localhost:18084",
    [int]$Requests = 100,
    [int]$Warmup = 5,
    [string]$OutputCsv = (Join-Path $PSScriptRoot "iteration3-performance-results.csv")
)

$ErrorActionPreference = "Stop"

if ($Requests -lt 1 -or $Warmup -lt 0) {
    throw "Requests must be at least 1 and Warmup cannot be negative."
}

$testCases = @(
    [pscustomobject]@{ Name = "product-list"; Path = "/api/products?page=1&size=12" },
    [pscustomobject]@{ Name = "search-test"; Path = "/api/products?q=test&page=1&size=12" },
    [pscustomobject]@{ Name = "search-phone"; Path = "/api/products?q=%E6%89%8B%E6%9C%BA&page=1&size=12" }
)

function Invoke-MeasuredRequest {
    param([string]$Uri)

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Get -UseBasicParsing
        $watch.Stop()
        return [pscustomobject]@{ Milliseconds = $watch.Elapsed.TotalMilliseconds; Success = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) }
    }
    catch {
        $watch.Stop()
        return [pscustomobject]@{ Milliseconds = $watch.Elapsed.TotalMilliseconds; Success = $false }
    }
}

$summary = foreach ($case in $testCases) {
    $uri = $BaseUrl.TrimEnd("/") + $case.Path

    for ($i = 0; $i -lt $Warmup; $i++) {
        $null = Invoke-MeasuredRequest -Uri $uri
    }

    $measurements = @()
    $totalWatch = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $Requests; $i++) {
        $measurements += Invoke-MeasuredRequest -Uri $uri
    }
    $totalWatch.Stop()

    $times = @($measurements | ForEach-Object { $_.Milliseconds } | Sort-Object)
    $errors = @($measurements | Where-Object { -not $_.Success }).Count
    $p95Index = [Math]::Max(0, [Math]::Ceiling($times.Count * 0.95) - 1)

    [pscustomobject]@{
        Endpoint = $case.Name
        Requests = $Requests
        AverageMs = [Math]::Round(($times | Measure-Object -Average).Average, 2)
        P95Ms = [Math]::Round($times[$p95Index], 2)
        MinMs = [Math]::Round($times[0], 2)
        MaxMs = [Math]::Round($times[-1], 2)
        ThroughputRps = [Math]::Round($Requests / $totalWatch.Elapsed.TotalSeconds, 2)
        Errors = $errors
        ErrorRatePercent = [Math]::Round(($errors * 100.0 / $Requests), 2)
        AverageUnder2Seconds = (($times | Measure-Object -Average).Average -lt 2000)
    }
}

$outputDirectory = Split-Path -Parent $OutputCsv
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$summary | Export-Csv -LiteralPath $OutputCsv -NoTypeInformation -Encoding UTF8
$summary | Format-Table -AutoSize
Write-Host "Results saved to: $OutputCsv"

if (@($summary | Where-Object { $_.Errors -gt 0 -or -not $_.AverageUnder2Seconds }).Count -gt 0) {
    exit 1
}
