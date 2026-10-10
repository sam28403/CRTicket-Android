param([string]$Source = 'C:\Users\SAM-LAB\WebstormProjects\CRTicket')
$ErrorActionPreference = 'Stop'
$records = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'web-source-hashes.json') -Raw | ConvertFrom-Json
$changed = @($records | Where-Object {
    $file = Join-Path $Source $_.path
    -not (Test-Path -LiteralPath $file) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $_.hash
})
if ($changed.Count -gt 0) { $changed | ForEach-Object path; throw "$($changed.Count) source files changed" }
Write-Output "Verified $($records.Count) original web project files: unchanged."
