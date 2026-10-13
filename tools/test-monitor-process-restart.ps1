param(
    [Parameter(Mandatory = $true)]
    [int]$TransportId,

    [Parameter(Mandatory = $true)]
    [ValidateSet('Pixel_8_Pro', '23124RN87C')]
    [string]$ExpectedModel
)

$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$evidence = Join-Path $workspace 'build\verification'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$actualModel = (& adb -t $TransportId shell getprop ro.product.model).Trim().Replace(' ', '_')
if ($LASTEXITCODE -ne 0 -or $actualModel -ne $ExpectedModel) {
    throw "Expected $ExpectedModel on transport $TransportId; found $actualModel"
}

function Invoke-MonitorPhase([string]$Phase, [string]$Scenario) {
    $logPath = Join-Path $evidence "$ExpectedModel-process-$Scenario-$Phase.log"
    $output = & adb -t $TransportId shell am instrument -w `
        -e class cc.samlab.ticket.MonitorProcessRestartTest `
        -e monitorRestartPhase $Phase `
        cc.samlab.ticket.test/androidx.test.runner.AndroidJUnitRunner 2>&1 |
        Tee-Object -FilePath $logPath
    $output | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($output -join [Environment]::NewLine) -notmatch 'OK \(1 test\)') {
        throw "Monitor phase $Phase failed; see $logPath"
    }
}

foreach ($scenario in @('active', 'finished')) {
    try {
        Invoke-MonitorPhase "prepare-$scenario" $scenario
        & adb -t $TransportId shell am force-stop cc.samlab.ticket
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the test application' }
        & adb -t $TransportId shell am start -W -n cc.samlab.ticket/.MainActivity
        if ($LASTEXITCODE -ne 0) { throw 'Could not reopen the test application' }
        Invoke-MonitorPhase 'verify' $scenario
    } catch {
        $failure = $_
        # The private test backup survives force-stop; cleanup restores the original stores.
        Invoke-MonitorPhase 'cleanup' $scenario
        throw $failure
    }
}
