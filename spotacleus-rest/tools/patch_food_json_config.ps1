$ErrorActionPreference = 'Stop'
$p = Join-Path $PSScriptRoot '..\src\main\kotlin\com\faigenbloom\spartaculous\routing\FoodRoutes.kt'
$p = (Resolve-Path $p).Path
$raw = [System.IO.File]::ReadAllText($p)
$from = 'Json { ignoreUnknownKeys = true; isLenient = true }'
$to   = 'Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false; coerceInputValues = true }'
if ($raw -notlike "*${from}*") {
  Write-Host "Pattern not found exactly, trying regex..."
  $updated = [regex]::Replace($raw, 'Json\s*\{\s*ignoreUnknownKeys\s*=\s*true;\s*isLenient\s*=\s*true\s*\}', 'Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false; coerceInputValues = true }')
} else {
  $updated = $raw.Replace($from, $to)
}
$bak = "$p.bak_$(Get-Date -Format 'yyyyMMddHHmmss')"
Copy-Item -LiteralPath $p -Destination $bak
[System.IO.File]::WriteAllText($p, $updated, [System.Text.Encoding]::UTF8)
Write-Host "Patched Json config in FoodRoutes.kt. Backup: $bak"
