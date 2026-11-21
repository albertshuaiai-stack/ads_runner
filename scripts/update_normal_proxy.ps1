# 批量替换 Normal 任务的 Dynamic Proxy Info（走后台 API，等同于在前端逐条编辑保存）
# Bulk-replace dynamicProxyInfo of Normal tasks through the backend API.
#
# 注意 / Caveat: PUT /api/normal-ads/{id} 会把 adsOwner 改成当前登录用户，
# 所以默认只改属于 -OwnerPhone 的任务，其他人的任务跳过（让对应账号自己跑）。
#
# 服务器上直接双击同目录的 update_normal_proxy.bat 即可 / On the server just run update_normal_proxy.bat.
# 用法 / Usage (默认先 dry-run 再询问是否写入，加 -Apply 跳过确认直接写入):
#   .\scripts\update_normal_proxy.ps1 -LoginId 13800000000 -Proxy "host:port:user:pass"
#   .\scripts\update_normal_proxy.ps1 -LoginId 13800000000 -Proxy "host:port:user:pass" -Apply
param(
    [Parameter(Mandatory = $true)][string]$LoginId,
    [Parameter(Mandatory = $true)][string]$Proxy,
    # 登录用邮箱时必须传手机号（adsOwner 存的是手机号）/ required when logging in by email
    [string]$OwnerPhone = $LoginId,
    [string]$BaseUrl = "https://track.lunalvabeau.com",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

$secure = Read-Host "Password for $LoginId" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

$loginBody = @{ loginId = $LoginId; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
    -ContentType "application/json" -Body $loginBody
$headers = @{ AMtoken = $login.amToken }
Write-Host "Logged in as $($login.userName) ($($login.userRole))"

$tasks = @()
$page = 0
do {
    $resp = Invoke-RestMethod -Method Get -Headers $headers `
        -Uri "$BaseUrl/api/normal-ads?page=$page&size=200"
    $tasks += $resp.content
    $page++
} while ($page -lt $resp.totalPages)
Write-Host "Found $($tasks.Count) normal tasks"

function Invoke-ProxyUpdate([bool]$doApply) {
    $updated = 0; $same = 0; $skipped = 0; $failed = 0
    foreach ($t in $tasks) {
        $label = "#$($t.id) [$($t.campainCountry)] $($t.campainName) owner=$($t.adsOwner)"
        if ($t.adsOwner -ne $OwnerPhone) {
            Write-Host "SKIP other owner: $label" -ForegroundColor DarkYellow
            $skipped++; continue
        }
        if ($t.dynamicProxyInfo -eq $Proxy) {
            $same++; continue
        }
        if ($t.campainCountry -and $t.campainCountry -notmatch '^(US|USA)$') {
            Write-Host "WARN non-US task, US proxy will fail IP check: $label" -ForegroundColor Yellow
        }
        if (-not $doApply) {
            Write-Host "DRY-RUN would update: $label  old=$($t.dynamicProxyInfo)"
            $updated++; continue
        }
        try {
            $body = @{ dynamicProxyInfo = $Proxy } | ConvertTo-Json
            Invoke-RestMethod -Method Put -Headers $headers -ContentType "application/json" `
                -Uri "$BaseUrl/api/normal-ads/$($t.id)" -Body $body | Out-Null
            Write-Host "UPDATED: $label" -ForegroundColor Green
            $updated++
        } catch {
            Write-Host "FAILED: $label  $($_.Exception.Message)" -ForegroundColor Red
            $failed++
        }
    }
    $mode = if ($doApply) { "APPLY" } else { "DRY-RUN" }
    Write-Host "[$mode] updated=$updated alreadySame=$same skippedOtherOwner=$skipped failed=$failed"
    return $updated
}

if ($Apply) {
    Invoke-ProxyUpdate $true | Out-Null
} else {
    $pending = Invoke-ProxyUpdate $false
    if ($pending -gt 0) {
        $answer = Read-Host "Apply these $pending updates now? (y/N)"
        if ($answer -match '^(y|yes)$') {
            Invoke-ProxyUpdate $true | Out-Null
        } else {
            Write-Host "Cancelled, nothing changed."
        }
    }
}
