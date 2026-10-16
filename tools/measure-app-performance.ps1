param(
    [int]$TransportId = 16,
    [string]$Label = 'run',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = 'Stop'
$package = 'cc.samlab.ticket'
$output = Join-Path $PSScriptRoot '../build/verification/performance'
New-Item -ItemType Directory -Path $output -Force | Out-Null

function Open-Page([string]$Route) {
    & $Adb -t $TransportId shell am start -W -f 0x20000000 -n "$package/.MainActivity" `
        --es openTrainTracking $Route 2>&1 | Out-Null
}

function Reset-Frames {
    & $Adb -t $TransportId shell dumpsys gfxinfo $package reset | Out-Null
}

function Read-Frames {
    $header = $null
    $lines = & $Adb -t $TransportId shell dumpsys gfxinfo $package framestats
    foreach ($line in $lines) {
        if ($line.StartsWith('Flags,')) { $header = $line.Split(','); continue }
        if (!$header -or $line -notmatch '^\d+,') { continue }
        $fields = $line.Split(',')
        $start = [array]::IndexOf($header, 'IntendedVsync')
        $end = [array]::IndexOf($header, 'FrameCompleted')
        if ($start -lt 0 -or $end -lt 0 -or $fields.Length -le $end) { continue }
        $duration = ([double]$fields[$end] - [double]$fields[$start]) / 1e6
        if ($duration -gt 0 -and $duration -lt 10000) { $duration }
    }
}

function Summarize([double[]]$Frames) {
    $sorted = @($Frames | Sort-Object)
    if ($sorted.Count -eq 0) {
        return [ordered]@{ frames = 0; note = 'No redraw in this interval' }
    }
    [ordered]@{
        frames = $sorted.Count
        over8ms = @($sorted | Where-Object { $_ -gt 8.34 }).Count
        over16ms = @($sorted | Where-Object { $_ -gt 16.67 }).Count
        over32ms = @($sorted | Where-Object { $_ -gt 33.33 }).Count
        p50ms = $sorted[[math]::Floor(($sorted.Count - 1) * 0.5)]
        p95ms = $sorted[[math]::Floor(($sorted.Count - 1) * 0.95)]
        maxMs = $sorted[-1]
    }
}

$size = ((& $Adb -t $TransportId shell wm size) | Select-Object -Last 1)
if ($size -notmatch '(\d+)x(\d+)') { throw 'Cannot determine display dimensions' }
$width = [int]$Matches[1]
$height = [int]$Matches[2]
$pages = [ordered]@{
    home = '/home'
    settings = '/settings'
    list = '/list'
    station = '/station?station=北京南'
    'web-ticket' = '/'
}
$report = [ordered]@{ label = $Label; model = (& $Adb -t $TransportId shell getprop ro.product.model).Trim() }
& $Adb -t $TransportId shell input keyevent KEYCODE_WAKEUP
$policy = (& $Adb -t $TransportId shell dumpsys window policy) -join "`n"
if ($policy -match 'showing=true|isStatusBarKeyguard=true') {
    throw 'Unlock the device before sampling; locked windows cannot provide comparable frame timings.'
}
Open-Page '/home'
Start-Sleep -Milliseconds 1500
foreach ($pass in 0..1) {
    foreach ($name in $pages.Keys) {
        Reset-Frames
        Open-Page $pages[$name]
        Start-Sleep -Milliseconds 900
        $report["navigate-$pass-$name"] = Summarize @(Read-Frames)
    }
}
foreach ($name in $pages.Keys) {
    Open-Page $pages[$name]
    Start-Sleep -Milliseconds 2000
    $frames = @()
    foreach ($swipe in 1..4) {
        Reset-Frames
        & $Adb -t $TransportId shell input swipe ([int]($width * 0.5)) ([int]($height * 0.78)) `
            ([int]($width * 0.5)) ([int]($height * 0.28)) 220
        Start-Sleep -Milliseconds 600
        $frames += @(Read-Frames)
    }
    $report["scroll-$name"] = Summarize $frames
}
if ($report['scroll-home'].frames -eq 0 -or $report['scroll-list'].frames -eq 0) {
    throw 'No frames from the scrolling controls; the app may be hidden or the device locked.'
}
$path = Join-Path $output "$Label.json"
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $path
Write-Output $path
