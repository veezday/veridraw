# infra.ps1 - Infrastructure management script for VeriDraw (Windows)
# Usage: .\infra.ps1 <command>
# Commands: up, down, logs, restart, status, token, clean, help

param(
    [Parameter(Position=0)]
    [ValidateSet("up", "down", "logs", "restart", "status", "token", "clean", "help")]
    [string]$Command = "help"
)

$ComposeFile = "docker-compose.infra.yml"
$Green = "Green"
$Yellow = "Yellow"
$Red = "Red"
$Cyan = "Cyan"

function Write-Colored {
    param([string]$Text, [string]$Color)
    Write-Host $Text -ForegroundColor $Color
}

function Start-Infra {
    Write-Colored "Starting VeriDraw infrastructure..." $Yellow
    docker compose -f $ComposeFile up -d

    Write-Colored "Waiting for services to be ready..." $Yellow
    Start-Sleep -Seconds 10

    Write-Colored "Infrastructure started!" $Green
    Write-Host ""
    Write-Host "Services:" -ForegroundColor Cyan
    Write-Host "  • PostgreSQL:   localhost:5432 (veridraw/veridraw_password)"
    Write-Host "  • Redis:        localhost:6379"
    Write-Host "  • Kafka:        localhost:9092"
    Write-Host "  • Keycloak:     http://localhost:8080 (admin/admin)"
    Write-Host "  • Grafana:      http://localhost:3000 (admin/admin)"
    Write-Host "  • Prometheus:   http://localhost:9090"
    Write-Host "  • Jaeger:       http://localhost:16686"
    Write-Host "  • MailHog:      http://localhost:8025"
    Write-Host ""
    Write-Host "Check status: .\infra.ps1 status" $Yellow
}

function Stop-Infra {
    Write-Colored "Stopping VeriDraw infrastructure..." $Yellow
    docker compose -f $ComposeFile down
}

function Show-Logs {
    docker compose -f $ComposeFile logs -f
}

function Restart-Infra {
    Stop-Infra
    Start-Infra
}

function Show-Status {
    Write-Colored "VeriDraw Infrastructure Status:" $Cyan
    docker compose -f $ComposeFile ps
}

function Get-DemoToken {
    Write-Host "Requesting JWT token for demo-user..." -ForegroundColor Yellow

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/realms/veridraw/protocol/openid-connect/token" `
            -Method POST `
            -Body @{
                client_id = "veridraw-api"
                client_secret = "veridraw-api-secret-key-2026"
                username = "demo-user"
                password = "password123"
                grant_type = "password"
            } `
            -ContentType "application/x-www-form-urlencoded"

        Write-Output $response.access_token
    }
    catch {
        Write-Host "Failed to get token: $_" -ForegroundColor Red
        return $null
    }
}

function Clean-Infra {
    Write-Colored "WARNING: This will remove ALL containers and volumes!" $Red
    $confirm = Read-Host "Are you sure? Type 'yes' to continue"

    if ($confirm -eq "yes") {
        docker compose -f $ComposeFile down -v
        Write-Colored "Infrastructure cleaned. Run '.\infra.ps1 up' to start fresh." $Green
    }
    else {
        Write-Colored "Cancelled." $Yellow
    }
}

function Show-Help {
    Write-Host ""
    Write-Colored "VeriDraw — Infrastructure Management (Windows)" $Green
    Write-Host ""
    Write-Host "Usage: .\infra.ps1 <command>" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Yellow
    Write-Host "  up       Start all infrastructure services"
    Write-Host "  down     Stop all infrastructure services"
    Write-Host "  logs     Show logs from all services"
    Write-Host "  restart  Restart all services"
    Write-Host "  status   Show status of all services"
    Write-Host "  token    Get JWT token for demo-user"
    Write-Host "  clean    Remove all containers and volumes"
    Write-Host "  help     Show this help message"
    Write-Host ""
    Write-Host "Quick Start:" -ForegroundColor Yellow
    Write-Host "  1. .\infra.ps1 up"
    Write-Host "  2. .\infra.ps1 token"
    Write-Host "  3. Use token in API requests"
    Write-Host ""
    Write-Host "For Linux/Mac users: use 'make <command>' instead" -ForegroundColor Gray
}

# Main command dispatcher
switch ($Command) {
    "up" { Start-Infra }
    "down" { Stop-Infra }
    "logs" { Show-Logs }
    "restart" { Restart-Infra }
    "status" { Show-Status }
    "token" { Get-DemoToken }
    "clean" { Clean-Infra }
    "help" { Show-Help }
    default { Show-Help }
}