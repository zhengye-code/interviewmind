# 本地开发启动脚本：解决 Windows 控制台中文乱码
#
# 用法（在项目根目录或任意位置）：
#   .\scripts\run-dev.ps1
#
# 原理：Windows 控制台默认代码页 936（GBK），而 JDK 18+ 默认用 UTF-8 输出。
#      应用发出来的是 UTF-8 字节，控制台却按 GBK 解码 → 中文日志乱码。
#      Swagger 不受影响，因为它走 HTTP，浏览器全程 UTF-8。
#
# 本脚本做三件事：
#   1. 把控制台代码页切到 65001（UTF-8）
#   2. 把 PowerShell 的输出/输入编码也设为 UTF-8
#   3. 回到项目根目录启动 Spring Boot

chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "代码页: $((chcp) -replace '.*:\s*', '')  |  输出编码: $([Console]::OutputEncoding.WebName)" -ForegroundColor Cyan
Write-Host "启动 Spring Boot ..." -ForegroundColor Cyan
Write-Host ""

.\mvnw.cmd spring-boot:run
