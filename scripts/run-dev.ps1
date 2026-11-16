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

# ---- PostgreSQL 宿主机端口 ----
# 容器和应用读的是同一个变量 POSTGRES_PORT，不设就是 5432
# （compose.yaml 和 application.properties 的默认值都是 5432）。
#
# 两边必须一致。只改一边的典型症状就是：
#   Connection to localhost:15432 refused
#
# 只有在 5432 被 Windows(Hyper-V) 划进保留段时才需要换端口：
#   1) $env:POSTGRES_PORT='15432'
#   2) docker compose up -d            # 让容器换到 15432
#   3) .\scripts\run-dev.ps1            # 在同一个终端启动应用
#
# 查看被保留的区间: netsh interface ipv4 show excludedportrange protocol=tcp
$pgPort = if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PORT)) { '5432' } else { $env:POSTGRES_PORT }

# 启动前校验：容器实际映射的端口必须和应用要连的端口一致，
# 否则会在 20 秒后报一句看不懂的 Connection refused。
$containerPort = (docker port interviewmind-postgres 5432/tcp 2>$null | Select-Object -First 1)
if ($containerPort) {
    $containerPort = ($containerPort -split ':')[-1].Trim()
    if ($containerPort -ne $pgPort) {
        Write-Host "错误: PostgreSQL 端口不一致" -ForegroundColor Red
        Write-Host "      容器映射在: $containerPort" -ForegroundColor Yellow
        Write-Host "      应用要连:   $pgPort" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  修法 A（不动容器）: `$env:POSTGRES_PORT='$containerPort'  然后重新运行本脚本" -ForegroundColor Yellow
        Write-Host "  修法 B（让容器换）: `$env:POSTGRES_PORT='$pgPort'; docker compose up -d" -ForegroundColor Yellow
        exit 1
    }
}
Write-Host "PostgreSQL 宿主机端口: $pgPort（与容器一致）" -ForegroundColor Cyan

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

# ---- 确保 EMBEDDING_API_KEY 在当前进程可见（M7 RAG 需要） ----
if ([string]::IsNullOrWhiteSpace($env:EMBEDDING_API_KEY)) {
    $fromSystem = [Environment]::GetEnvironmentVariable('EMBEDDING_API_KEY', 'Machine')
    if ([string]::IsNullOrWhiteSpace($fromSystem)) {
        $fromSystem = [Environment]::GetEnvironmentVariable('EMBEDDING_API_KEY', 'User')
    }
    if (-not [string]::IsNullOrWhiteSpace($fromSystem)) {
        $env:EMBEDDING_API_KEY = $fromSystem
        Write-Host "已从系统环境变量载入 EMBEDDING_API_KEY（末4位: $($fromSystem.Substring($fromSystem.Length - 4))）" -ForegroundColor Cyan
    }
}

if ([string]::IsNullOrWhiteSpace($env:EMBEDDING_API_KEY)) {
    Write-Host "错误: 未找到 EMBEDDING_API_KEY。" -ForegroundColor Red
    Write-Host "  M7 的向量检索需要它（硅基流动 https://cloud.siliconflow.cn 可免费注册）。" -ForegroundColor Yellow
    Write-Host "  本窗口:  `$env:EMBEDDING_API_KEY = `"sk-你的key`"" -ForegroundColor Yellow
    Write-Host "  用户变量: [Environment]::SetEnvironmentVariable('EMBEDDING_API_KEY','sk-你的key','User')" -ForegroundColor Yellow
    Write-Host "  （application.properties 里引用了它，缺了会启动失败，所以这里先拦住）" -ForegroundColor Yellow
    exit 1
}
Write-Host "代码页: $((chcp) -replace '.*:\s*', '')  |  输出编码: $([Console]::OutputEncoding.WebName)" -ForegroundColor Cyan
Write-Host "启动 Spring Boot ..." -ForegroundColor Cyan
Write-Host ""

.\mvnw.cmd spring-boot:run
