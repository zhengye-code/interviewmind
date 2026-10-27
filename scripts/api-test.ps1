<#
.SYNOPSIS
    调用 HTTP 接口，并按 UTF-8 正确显示中文响应。

.DESCRIPTION
    为什么需要这个脚本：
      Windows PowerShell 5.1 的 Invoke-WebRequest 在响应头没有 charset 时，
      会按 ISO-8859-1 解码正文。Spring Boot 返回的 application/json 按规范
      就是 UTF-8 且通常不带 charset，于是中文会显示成 "ç³»ç»Ÿç¹å¿" 这种乱码。
      这纯粹是客户端解码问题，服务端和浏览器（Swagger）都是正常的。

    本脚本改为直接读取原始字节，再用 UTF-8 解码，因此不会乱码。

.EXAMPLE
    .\scripts\api-test.ps1 -Url "http://localhost:8080/api/health"

.EXAMPLE
    .\scripts\api-test.ps1 -Url "http://localhost:8080/api/ai/ping?message=你好"

.EXAMPLE
    .\scripts\api-test.ps1 -Url "http://localhost:8080/api/ai/ping" -Method POST
#>
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [string]$Method = 'GET',
    [int]$TimeoutSec = 120
)

$ErrorActionPreference = 'Stop'

function Write-Utf8Body {
    param($Stream, [switch]$Error)
    $Stream.Position = 0
    $bytes = New-Object byte[] $Stream.Length
    $null = $Stream.Read($bytes, 0, $bytes.Length)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ($Error) { Write-Host $text -ForegroundColor Yellow } else { Write-Host $text }
}

try {
    $resp = Invoke-WebRequest -Uri $Url -Method $Method -UseBasicParsing -TimeoutSec $TimeoutSec
    Write-Host "[$Method] $Url -> HTTP $($resp.StatusCode)" -ForegroundColor Green
    Write-Utf8Body -Stream $resp.RawContentStream
}
catch {
    $ex = $_.Exception
    if ($ex.Response) {
        $code = [int]$ex.Response.StatusCode
        Write-Host "[$Method] $Url -> HTTP $code" -ForegroundColor Yellow
        try { Write-Utf8Body -Stream $ex.Response.GetResponseStream() -Error } catch { }
    }
    else {
        Write-Host "[$Method] $Url -> 请求失败: $($ex.Message)" -ForegroundColor Red
        exit 1
    }
}
