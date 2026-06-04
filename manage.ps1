<#
.SYNOPSIS
  Unified manager for Docker Compose stacks (Infra + Services).
.DESCRIPTION
  Provides dev-friendly commands to start, rebuild, monitor logs and check ports.
  Requires Docker Desktop + Compose V2.
#>
[CmdletBinding()]
param(
    [Parameter(Position=0)]
    [ValidateSet("up", "down", "rebuild", "status", "logs", "clean", "help")]
    [string]$Command = "help",

    [Parameter(Position=1)]
    [string]$Service = ""
)

# --- Configuration ---
$InfraFile = "docker-compose.infra.yml"
$ServicesFile = "docker-compose.services.yml"
$ComposeCmd = "docker compose"

# --- Helpers ---
function Write-Info    { param([string]$Msg) Write-Host "[ℹ️] $Msg" -ForegroundColor Cyan }
function Write-Success { param([string]$Msg) Write-Host "[✅] $Msg" -ForegroundColor Green }
function Write-Error   { param([string]$Msg) Write-Host "[❌] $Msg" -ForegroundColor Red }

function Test-Docker {
    if (-not (Get-Command $ComposeCmd -ErrorAction SilentlyContinue)) {
        # Fallback for older Docker versions
        $script:ComposeCmd = "docker-compose"
        if (-not (Get-Command $ComposeCmd -ErrorAction SilentlyContinue)) {
            Write-Error "Docker Compose is not installed or not in PATH."
            exit 1
        }
    }
    try { $null = docker info 2>&1 }
    catch {
        Write-Error "Docker Desktop is not running. Start it and retry."
        exit 1
    }
    Write-Info "Docker environment verified."
}

# --- Actions ---
function Start-Stack {
    Write-Info "Starting infrastructure & services..."
    & $ComposeCmd -f $InfraFile -f $ServicesFile up -d
    if ($LASTEXITCODE -eq 0) { Write-Success "Stack started." } else { Write-Error "Start failed." }
}

function Stop-Stack {
    Write-Info "Stopping stacks..."
    & $ComposeCmd -f $InfraFile -f $ServicesFile down
    if ($LASTEXITCODE -eq 0) { Write-Success "Stacks stopped." } else { Write-Error "Stop failed." }
}

function Rebuild-Services {
    Write-Info "Rebuilding services (dev mode)..."
    $env:DOCKER_BUILDKIT = 1
    $composeArgs = @("-f", $InfraFile, "-f", $ServicesFile, "up", "-d", "--build", "--force-recreate")
    if ($Service) {
        $composeArgs += $Service
    }
    & $ComposeCmd @composeArgs
    if ($LASTEXITCODE -eq 0) { Write-Success "Services rebuilt & restarted." } else { Write-Error "Build failed." }
}

function Show-Status {
    Write-Info "Containers & Port Mappings:"
    & $ComposeCmd -f $InfraFile -f $ServicesFile ps
    Write-Host "`n🔍 Listening Application Ports:" -ForegroundColor Yellow
    docker ps --format "table {{.Names}}\t{{.Ports}}" | Where-Object { $_ -match "0.0.0.0" }
}

function Show-Logs {
    $target = if ($Service) { $Service } else { "" }

    $displayTarget = if ($target -eq "") { "all" } else { $target }

    Write-Info "Tailing logs for: $displayTarget"
    & $ComposeCmd -f $InfraFile -f $ServicesFile logs -f --tail=100 --no-color --no-log-prefix $target
}

function Clean-Environment {
    Write-Info "Cleaning volumes, networks & local images..."
    & $ComposeCmd -f $InfraFile -f $ServicesFile down -v --rmi local --remove-orphans
    if ($LASTEXITCODE -eq 0) { Write-Success "Cleanup complete." } else { Write-Error "Cleanup failed." }
}

# --- Main ---
Test-Docker
switch ($Command) {
    "up"      { Start-Stack }
    "down"    { Stop-Stack }
    "rebuild" { Rebuild-Services }
    "status"  { Show-Status }
    "logs"    { Show-Logs }
    "clean"   { Clean-Environment }
    "help"    {
        Write-Host @"
🐳 Docker Compose Manager
Usage: .\manage.ps1 [command] [service]

Commands:
  up       Start all stacks (infra + services)
  down     Stop all containers
  rebuild  Rebuild & restart services (use during dev)
  status   Show containers & published ports
  logs     Tail logs (optionally: .\manage.ps1 logs gateway)
  clean    Remove volumes, networks, and local images
  help     Show this message

Examples:
  .\manage.ps1 rebuild
  .\manage.ps1 logs gateway
  .\manage.ps1 status
"@ -ForegroundColor White
    }
    default { Write-Error "Unknown command. Use 'help'." }
}
