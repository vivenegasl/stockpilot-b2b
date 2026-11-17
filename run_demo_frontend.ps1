# StockPilot — Lanzador Rápido de Frontend para Demo (PowerShell)
# Uso: .\run_demo_frontend.ps1

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  StockPilot — Iniciando Servidor Frontend de Demostración" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

$frontendPath = Join-Path $PSScriptRoot "frontend"

if (-not (Test-Path $frontendPath)) {
    Write-Error "No se encontro el directorio frontend en: $frontendPath"
    exit 1
}

Set-Location $frontendPath

Write-Host "Verificando dependencias en $frontendPath..." -ForegroundColor Green
if (-not (Test-Path (Join-Path $frontendPath "node_modules"))) {
    Write-Host "Instalando dependencias de node_modules..." -ForegroundColor Yellow
    npm install
}

Write-Host ""
Write-Host "Levantando servidor Vite en http://localhost:5173..." -ForegroundColor Cyan
Write-Host "Presiona Ctrl + C en esta ventana para detener el servidor cuando termine la demo." -ForegroundColor Gray
Write-Host ""

# Abrir el navegador en 3 segundos en segundo plano
Start-Job -ScriptBlock {
    Start-Sleep -Seconds 3
    Start-Process "http://localhost:5173"
} | Out-Null

npm run dev
