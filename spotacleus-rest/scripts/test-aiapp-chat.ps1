param(
  [Parameter(Mandatory=$true)][string]$Token,
  [Parameter(Mandatory=$true)][string]$UserId,
  [string]$Message = "Как дела?"
)

$ErrorActionPreference = 'Stop'

$headers = @{
  accept               = 'text/event-stream'
  'content-type'       = 'application/json'
  origin               = 'https://chat.chatbot.app'
  referer              = 'https://chat.chatbot.app/'
  x_function_use       = 'true'
  x_include_citations  = 'true'
  x_model              = '29'
  x_platform           = 'web'
  x_pr                 = 'false'
  x_stream             = 'true'
  x_token              = $Token
  x_user_id            = $UserId
  x_version            = '2'
  x_web_search_source  = '1'
  x_web_search_use     = 'true'
}

$bodyObj = @{ messages = @(@{ role = 'user'; content = $Message }) }
$body = $bodyObj | ConvertTo-Json -Depth 5 -Compress

try {
  $resp = Invoke-WebRequest -Uri 'https://api.aiapp.ai/api/chat' -Method POST -Headers $headers -Body $body -TimeoutSec 20
} catch {
  Write-Output ("HTTP_ERROR: " + $_.Exception.Message)
  exit 1
}

Write-Output ("STATUS: " + $resp.StatusCode)
Write-Output ("TYPE: " + ($resp.Headers['Content-Type']))

$content = $resp.Content
if ($null -ne $content) {
  $preview = if ($content.Length -gt 1000) { $content.Substring(0,1000) } else { $content }
  Write-Output $preview
} else {
  Write-Output 'NO CONTENT'
}
