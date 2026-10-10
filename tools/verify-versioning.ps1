param(
    [string]$Java = 'java'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path $projectRoot 'build/verification/versioning'
$wrapper = Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar'

# Leave the app's real Git history untouched: use a fresh isolated repository.
$fixtureRoot = Join-Path $fixtureRoot ([guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
Set-Content -LiteralPath (Join-Path $fixtureRoot 'settings.gradle.kts') `
    -Value 'rootProject.name = "versioning-fixture"'
Set-Content -LiteralPath (Join-Path $fixtureRoot 'build.gradle.kts') `
    -Value 'apply(from = file("../../../../gradle/versioning.gradle.kts"))'
Set-Content -LiteralPath (Join-Path $fixtureRoot 'fixture.txt') -Value 'first'

& git -C $fixtureRoot init --initial-branch=main --quiet
if ($LASTEXITCODE -ne 0) { throw 'Unable to create versioning fixture' }

function Assert-Version([int]$Count, [int]$Patch, [int]$Code) {
    $hash = 'uncommitted'
    if ($Count -gt 0) {
        $hash = (& git -C $fixtureRoot rev-parse --short=7 HEAD).Trim()
    }
    $output = & $Java -jar $wrapper --project-dir $fixtureRoot `
        printAndroidVersion --configuration-cache --console=plain 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    $text = $output -join "`n"
    foreach ($expected in @(
        "versionName=0.3.$Patch-$hash-260930",
        "versionCode=$Code",
        "commitCount=$Count"
    )) {
        if (-not $text.Contains($expected)) { throw "Missing $expected`n$text" }
    }
    Write-Output "Verified commits=$Count, version=0.3.$Patch-$hash-260930, code=$Code"
    return $text
}

Assert-Version 0 3 7 | Out-Null
& git -C $fixtureRoot add -- fixture.txt
& git -C $fixtureRoot -c user.name='Version Test' `
    -c user.email='version-test@example.invalid' commit --quiet -m 'First fixture commit'
if ($LASTEXITCODE -ne 0) { throw 'First fixture commit failed' }
Assert-Version 1 3 7 | Out-Null

Set-Content -LiteralPath (Join-Path $fixtureRoot 'fixture.txt') -Value 'second'
& git -C $fixtureRoot add -- fixture.txt
& git -C $fixtureRoot -c user.name='Version Test' `
    -c user.email='version-test@example.invalid' commit --quiet -m 'Second fixture commit'
if ($LASTEXITCODE -ne 0) { throw 'Second fixture commit failed' }
Assert-Version 2 4 8 | Out-Null
$repeat = Assert-Version 2 4 8
if (-not ($repeat -join "`n").Contains('Reusing configuration cache')) {
    throw 'Repeated build did not reuse its configuration cache'
}
Write-Output 'Version increment, hash refresh, fixed date and configuration cache checks passed.'
