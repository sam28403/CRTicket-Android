param(
    [string]$CatalogPath = "$PSScriptRoot/../build/vehicle-audit-catalog.json",
    [string]$DetailsPath = "$PSScriptRoot/../build/vehicle-audit",
    [string]$OutputRoot = "$PSScriptRoot/.."
)

$ErrorActionPreference = 'Stop'
$catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json
$rules = [System.Collections.Generic.List[object]]::new()

function Add-ArtworkRule {
    param([string]$Name, [string[]]$Prefixes, [string]$Numbers, [string]$Note = '')
    $entry = @($catalog | Where-Object name -EQ $Name)
    if ($entry.Count -ne 1) { throw "Ambiguous catalog entry: $Name" }
    $entry = $entry[0]
    $path = [uri]::new([uri]'https://www.china-emu.cn/', $entry.image).AbsolutePath
    $ranges = @($Numbers.Split(',') | ForEach-Object {
        $pair = $_.Split('-')
        ,@([int]$pair[0], [int]$pair[-1])
    })
    $rules.Add([ordered]@{
        model = $Name
        prefixes = $Prefixes
        numbers = $Numbers
        ranges = $ranges
        image = $path
        source = 'https://www.china-emu.cn' + $entry.href
        note = $Note
    })
}

Add-ArtworkRule 'CR200J3-B(跨国版)' @('CR200J', 'CR200J3B') '4004-4005'
Add-ArtworkRule 'CRH380A(港铁)' @('CRH380A') '0251-0259'
Add-ArtworkRule 'CRH1B(1E头型)' @('CRH1B') '1076-1080'
Add-ArtworkRule 'CRH1E(NG)' @('CRH1E') '1229-1233'
Add-ArtworkRule 'CRH2B(2A头型)' @('CRH2B') '2466-2472,4096-4105'
Add-ArtworkRule 'CRH2A(NG)' @('CRH2A') '2460'
Add-ArtworkRule 'CRH2E(NG高寒)' @('CRH2E') '2461'
Add-ArtworkRule 'CRH2E(NG)' @('CRH2E') '2462'
Add-ArtworkRule 'CRH2E(纵向)' @('CRH2E') '2463-2465'
Add-ArtworkRule 'CRH5G(技术提升型)' @('CRH5G') '5218-5229'
Add-ArtworkRule 'CRH6A(后期)' @('CRH6A') '0624-0643'
Add-ArtworkRule 'CRH6A(舒适版)' @('CRH6A') '4132-4137,4502-4509'
Add-ArtworkRule 'CRH6A(智能版)' @('CRH6A') '0216-0218'
Add-ArtworkRule 'CRH6A-A(智能版)' @('CRH6AA') '0213-0215,0644-0648,0652-0666' `
    '0212 涉及由 0210 改号，0219 后续改造，均保留默认图。'
Add-ArtworkRule 'CRH6F-A(智能版)' @('CRH6FA') '0667-0673'
Add-ArtworkRule 'CRH6A-A(2.0)' @('CRH6AA') '0220-0229,0233-0234' `
    '只使用明确的新造闭合号段；0219、0674~0685、0901~0912 涉及改造或改号，保留默认图。'
Add-ArtworkRule 'CRH6F(2.0)' @('CRH6F') '0230-0232'
Add-ArtworkRule 'CR200J1-B' @('CR200J', 'CR200J1B') '2048-2084'
Add-ArtworkRule 'CR200J2-B' @('CR200J', 'CR200J2B') '3005-3007,4006-4008'
Add-ArtworkRule 'CR200J3-B' @('CR200J', 'CR200J3B') '1049-1086,1093-1110'
Add-ArtworkRule 'CR400BF-A(纵向卧铺)' @('CR400BFA') '0306'
Add-ArtworkRule 'CJ6(样车)' @('CJ6') '0701-0702'

$audit = @($catalog | ForEach-Object {
    $entry = $_
    $source = if ($entry.href) { 'https://www.china-emu.cn' + $entry.href } else { '' }
    $file = if ($entry.href) {
        Join-Path $DetailsPath ([IO.Path]::GetFileName($entry.href))
    } else { '' }
    $ranges = if ($file -and (Test-Path -LiteralPath $file)) {
        $html = Get-Content -LiteralPath $file -Raw
        @([regex]::Matches($html, '<div class="model-box-word" title="([^"]*)"') |
            ForEach-Object { [System.Net.WebUtility]::HtmlDecode($_.Groups[1].Value) }) -join '; '
    } else { '' }
    [pscustomobject]@{
        model = $entry.name
        image = $entry.image
        source = $source
        numberEvidence = $ranges
        decision = if ($entry.name -in $rules.model) { '新增按号段匹配' }
            elseif (-not $entry.href) { '无详情链接，保持默认' }
            elseif (-not $ranges) { '未列明确号段，保持默认' }
            else { '已核对，保持默认或既有规则' }
    }
})
$missing = @($catalog | Where-Object {
    $_.href -and -not (Test-Path -LiteralPath (Join-Path $DetailsPath ([IO.Path]::GetFileName($_.href))))
})
if ($missing.Count) { throw "Still missing $($missing.Count) linked details" }
[ordered]@{ rules = @($rules) } | ConvertTo-Json -Depth 6 |
    Set-Content -LiteralPath "$OutputRoot/app/src/main/assets/vehicle-artwork-overrides.json" -Encoding utf8
$audit | Export-Csv -LiteralPath "$OutputRoot/docs/vehicle-artwork-audit.csv" -NoTypeInformation -Encoding utf8
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# 车辆车头图片核对清单（2026-10-21）')
$lines.Add('')
$lines.Add('核对目录 307 个条目：292 个带详情链接，对应 288 个不同详情页，均已读取；15 个没有详情链接。')
$lines.Add('只新增车号明确、无需推断改号或改造关系的匹配。其他使用默认版本。胶囊颜色规则保持不变。')
$lines.Add('完整逐项记录见 [vehicle-artwork-audit.csv](vehicle-artwork-audit.csv)。')
$lines.Add('')
$lines.Add('## 本次新增的 22 组规则')
$lines.Add('')
$lines.Add('| 车号前缀 | 车号／闭合号段 | 使用的版本与依据 | 备注 |')
$lines.Add('|---|---|---|---|')
foreach ($rule in $rules) {
    $lines.Add("| $($rule.prefixes -join ' / ') | $($rule.numbers) | [$($rule.model)]($($rule.source)) | $($rule.note) |")
}
$lines.Add('')
$lines.Add('## 保留默认版本的重点条目')
$lines.Add('')
$lines.Add('- CR400AF 0207~0208、CR400BF 0305/0503/0507：样车后续涂装已改为量产版本。')
$lines.Add('- CR300AF 0001/0003/0004、CR300BF 0002/0005/0006：详情说明已改造或改为量产涂装。')
$lines.Add('- CR200J 1001/2001/3001/4001 及长编组样车：动力车交换、历史编号和重复编号关系复杂。')
$lines.Add('- CR400BF 0031、CR400BF-G 0051 及原公务车：涉及改造或改型，不推断其历史外观。')
$lines.Add('- CRH3A 城际版 0302/0502：涉及 CJ1 与 CRH3A 更名／改号，保持原默认图。')
$lines.Add('- CRH3A-A 0510：样车后续更改涂装，保持默认图。')
$lines.Add('- CRH6A-A 0212（原 0210）、0219、0674~0685、0901~0912：涉及改号或改造。')
$lines.Add('- CRH6A 中期、CRH6F-A 2.0（仅列 0913~）、CRH2H、双层 CR400BF-Z 等：缺少完整闭合号段。')
$lines.Add('- CR220J 样车：更换动力车、返厂修改涂装，保持默认图。')
$lines.Add('- NYJ1 4007/4010：SFD06/SFD08/SFD15 多个版本重复使用，保持默认图。')
$lines.Add('- 同一车型各阶段使用相同图标的条目（如 CRH380AL 各阶段），不建立冗余号段规则。')
$lines.Add('')
$lines.Add('## 原有规则保留')
$lines.Add('')
$lines.Add('- CR200J 默认 CR200J1-A；CR200JC 默认 CR200J1-C。')
$lines.Add('- CR400BFZ0524 仍使用亚运版，CR400BFC5162 仍使用冬奥版。')
$lines.Add('- 补齐 CRH1A 的默认图入口；未命中特例时回退到对应默认版本。')
$lines | Set-Content -LiteralPath "$OutputRoot/docs/vehicle-artwork-audit.md" -Encoding utf8
Write-Output "Checked $($audit.Count) entries; wrote $($rules.Count) explicit rules and the full audit."
