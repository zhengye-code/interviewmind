# 本地开发启动脚本
#
# 用法（在项目根目录或任意位置）：
#   .\scripts\run-dev.ps1
#
# 解决两件事：
#   1. 中文乱码
#      Windows 控制台默认代码页 936（GBK），而 JDK 18+ 默认用 UTF-8 输出，
#      控制台按 GBK 解码 → 中文日志乱码。Swagger 不受影响（走 HTTP，全程 UTF-8）。
#   2. 环境变量没生效
#      Windows 的「系统/用户环境变量」只对新启动的进程生效。
#      在系统设置里改完之后，已经开着的终端、IDE、DSH 进程都拿不到新值，
#      于是 Spring 解析不了 ${DEEPSEEK_API_KEY}，会把占位符原文当成 key 发出去，
#      接口表现为 401 → 我们这边笼统地返回 7003。
#      本脚本会在启动前主动从「用户/系统」作用域重新读取一次并注入当前进程。

chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

Set-Location (Split-Path $PSScriptRoot -Parent)

# ---- 确保 DEEPSEEK_API_KEY 在当前进程可见 ----
if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    $fromSystem = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine')
    if ([string]::IsNullOrWhiteSpace($fromSystem)) {
        $fromSystem = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')
    }
    if (-not [string]::IsNullOrWhiteSpace($fromSystem)) {
        $env:DEEPSEEK_API_KEY = $fromSystem
        Write-Host "已从系统环境变量载入 DEEPSEEK_API_KEY（末4位: $($fromSystem.Substring($fromSystem.Length - 4))）" -ForegroundColor Cyan
    }
}

if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    Write-Host "错误: 未找到 DEEPSEEK_API_KEY。" -ForegroundColor Red
    Write-Host "  请先在本窗口设置:  `$env:DEEPSEEK_API_KEY = `"sk-你的key`"" -ForegroundColor Yellow
    Write-Host "  或设置到用户环境变量（设置后需重开终端）:" -ForegroundColor Yellow
    Write-Host "  [Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY','sk-你的key','User')" -ForegroundColor Yellow
    exit 1
}

# 占位符没被替换时的典型特征：值以 ${ 开头、以 } 结尾
if ($env:DEEPSEEK_API_KEY.StartsWith('${') -or $env:DEEPSEEK_API_KEY -eq '${DEEPSEEK_API_KEY}') {
    Write-Host "错误: DEEPSEEK_API_KEY 的值看起来是未解析的占位符，而不是真实 key。" -ForegroundColor Red
    exit 1
}

Write-Host "代码页: $((chcp) -replace '.*:\s*', '')  |  输出编码: $([Console]::OutputEncoding.WebName)" -ForegroundColor Cyan
Write-Host "启动 Spring Boot ..." -ForegroundColor Cyan
Write-Host ""

.\mvnw.cmd spring-boot:run
