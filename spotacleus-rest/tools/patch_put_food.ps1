$ErrorActionPreference = 'Stop'
$p = Join-Path $PSScriptRoot '..\src\main\kotlin\com\faigenbloom\spartaculous\routing\FoodRoutes.kt'
$p = (Resolve-Path $p).Path
$raw = [System.IO.File]::ReadAllText($p)
$pattern = 'val\s+req\s*=\s*call\.receive<UpdateMealRequest>\(\)'
$replacement = @'
val ct = call.request.headers["Content-Type"]
val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
val req = try {
    Json { ignoreUnknownKeys = true; isLenient = true }
        .decodeFromString<UpdateMealRequest>(raw)
} catch (e: SerializationException) {
    call.application.environment.log.error("PUT /api/food/meals/{mealId} deserialization failed, CT=${ct}, raw='${raw}'", e)
    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
    return@put
}
'@
if ($raw -notmatch $pattern) {
  Write-Error "Target pattern not found: $pattern"
}
$bak = "$p.bak_$(Get-Date -Format 'yyyyMMddHHmmss')"
Copy-Item -LiteralPath $p -Destination $bak
$updated = [System.Text.RegularExpressions.Regex]::Replace($raw, $pattern, $replacement)
[System.IO.File]::WriteAllText($p, $updated, [System.Text.Encoding]::UTF8)
Write-Host "Patched. Backup: $bak"
