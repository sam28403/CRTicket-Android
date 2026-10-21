param(
    [string]$SourcePath,
    [string]$OutputPath = "$PSScriptRoot/../app/src/main/assets/vehicle-carousel-models.json"
)

$ErrorActionPreference = 'Stop'
$sourceUrl = 'https://www.china-emu.cn/Trains/ALL/'
$html = if ($SourcePath) {
    Get-Content -LiteralPath $SourcePath -Raw
} else {
    (Invoke-WebRequest $sourceUrl).Content
}
$pattern = 'series-list-item-icon"[^>]*background:url\(([^)]+)\)[\s\S]*?<a\b[^>]*class="series-list-item-word(?:\s[^"]*)?"[^>]*>([\s\S]*?)</a>'
$entries = [regex]::Matches($html, $pattern)
$iconCount = [regex]::Matches($html, 'class="series-list-item-icon"').Count
if ($entries.Count -ne $iconCount -or $entries.Count -eq 0) {
    throw "Catalog markup changed: $iconCount cards but $($entries.Count) parsed models"
}
$models = @($entries | ForEach-Object {
    $name = [System.Net.WebUtility]::HtmlDecode(
        ($_.Groups[2].Value -replace '<[^>]*>', '').Trim()
    )
    $path = [uri]::new([uri]$sourceUrl, $_.Groups[1].Value).AbsolutePath
    if ($path -notmatch '^/assets/imgs/Trains/Cute/(?!.*\.\.)[^?#]+\.png$') {
        throw "Unexpected image path: $path"
    }
    [ordered]@{
        name = $name
        image = if ($path -match '/none\.png$') { $null } else { $path }
    }
})
[ordered]@{
    source = $sourceUrl
    models = $models
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Output "Saved $($models.Count) models, including variants and models without artwork."
