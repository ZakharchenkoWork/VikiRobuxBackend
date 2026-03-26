# Тестирование авторизации с тестовыми токенами

Write-Host "`n=== Testing Auth with Test Tokens ===" -ForegroundColor Cyan

$baseUrl = "http://192.168.68.67:8282"
$testTokens = @(
    "test-token",
    "mock-jwt-token",
    "test-12345",
    "mock-abcdef"
)

$endpoints = @(
    "/api/goals/overview",
    "/api/settings/profile",
    "/api/settings/preferences"
)

foreach ($token in $testTokens) {
    Write-Host "`n--- Testing token: $token ---" -ForegroundColor Yellow
    
    foreach ($endpoint in $endpoints) {
        try {
            $response = Invoke-WebRequest `
                -Uri "$baseUrl$endpoint" `
                -Headers @{"Authorization" = "Bearer $token"} `
                -Method GET `
                -UseBasicParsing `
                -TimeoutSec 5
            
            Write-Host "  ✓ $endpoint : $($response.StatusCode)" -ForegroundColor Green
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -eq 401) {
                Write-Host "  ✗ $endpoint : 401 (token not accepted)" -ForegroundColor Red
            } else {
                Write-Host "  ? $endpoint : $statusCode" -ForegroundColor Yellow
            }
        }
    }
}

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "Supported test tokens:" -ForegroundColor Green
Write-Host "  - test-token" -ForegroundColor White
Write-Host "  - mock-jwt-token" -ForegroundColor White
Write-Host "  - test-* (любой токен начинающийся с 'test-')" -ForegroundColor White
Write-Host "  - mock-* (любой токен начинающийся с 'mock-')" -ForegroundColor White
Write-Host "`nUsage in tests:" -ForegroundColor Cyan
Write-Host '  headers["Authorization"] = "Bearer test-token"' -ForegroundColor White
