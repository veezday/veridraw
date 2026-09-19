<#
.SYNOPSIS
  Unified manager for Docker Compose stacks.
#>
[CmdletBinding()]
param(
    [Parameter(Position=0)]
    [ValidateSet("up", "down", "rebuild", "status", "logs", "clean", "help")]
    [string]$Command = "help",

    [Parameter(Position=1)]
    [string]$Service = ""
)

$InfraFile = "docker-compose.infra.yml"
$ServicesFile = "docker-compose.services.yml"
$ComposeCmd = "docker compose"

function Write-Info { param([string]$Msg) Write-Host "[i] $Msg" -ForegroundColor Cyan }
function Write-Success { param([string]$Msg) Write-Host "[+] $Msg" -ForegroundColor Green }
function Write-ErrorCustom { param([string]$Msg) Write-Host "[-] $Msg" -ForegroundColor Red }

function Test-Docker {
    if (-not (Get-Command $ComposeCmd -ErrorAction SilentlyContinue)) {
        $script:ComposeCmd = "docker-compose"
        if (-not (Get-Command $ComposeCmd -ErrorAction SilentlyContinue)) {
            Write-ErrorCustom "Docker Compose is not installed or not in PATH."
            exit 1
        }
    }
    try {
        $null = docker info 2>&1
    }
    catch {
        Write-ErrorCustom "Docker Desktop is not running. Start it and retry."
        exit 1
    }
    Write-Info "Docker environment verified."
}

function Start-Stack {
    $targetMsg = if ($Service) { "service '$Service'" } else { "infrastructure and services" }
    Write-Info "Starting $targetMsg..."
    $composeArgs = @("-f", $InfraFile, "-f", $ServicesFile, "up", "-d")
    if ($Service) { $composeArgs += $Service }

    & $ComposeCmd @composeArgs
    if ($LASTEXITCODE -eq 0) { Write-Success "Started successfully." } else { Write-ErrorCustom "Start failed." }
}

function Stop-Stack {
    if ($Service) {
        Write-Info "Stopping specific service: $Service..."
        & $ComposeCmd -f $InfraFile -f $ServicesFile stop $Service
        if ($LASTEXITCODE -eq 0) { Write-Success "Service '$Service' stopped." } else { Write-ErrorCustom "Stop failed." }
    } else {
        Write-Info "Stopping all stacks..."
        & $ComposeCmd -f $InfraFile -f $ServicesFile down
        if ($LASTEXITCODE -eq 0) { Write-Success "Stacks stopped." } else { Write-ErrorCustom "Stop failed." }
    }
}

function Rebuild-Services {
    Write-Info "Fast rebuilding services (using cache)..."
    $env:DOCKER_BUILDKIT = 1

    $composeArgs = @("-f", $InfraFile, "-f", $ServicesFile, "up", "-d", "--build")
    if ($Service) { $composeArgs += $Service }

    & $ComposeCmd @composeArgs
    if ($LASTEXITCODE -eq 0) { Write-Success "Services rebuilt and restarted quickly." } else { Write-ErrorCustom "Build failed." }
}

function Show-Status {
    $displayTarget = if ($Service) { $Service } else { "all" }
    Write-Info "Containers and Port Mappings for: $displayTarget"

    if ($Service) {
        & $ComposeCmd -f $InfraFile -f $ServicesFile ps $Service
        Write-Host ""
        Write-Host "Listening Application Ports:" -ForegroundColor Yellow
        docker ps --format "table {{.Names}}`t{{.Ports}}" | Where-Object { $_ -match $Service -and $_ -match "0.0.0.0" }
    } else {
        & $ComposeCmd -f $InfraFile -f $ServicesFile ps
        Write-Host ""
        Write-Host "Listening Application Ports:" -ForegroundColor Yellow
        docker ps --format "table {{.Names}}`t{{.Ports}}" | Where-Object { $_ -match "0.0.0.0" }
    }
}

function Show-Logs {
    $displayTarget = if ($Service) { $Service } else { "all" }
    Write-Info "Tailing logs for: $displayTarget"

    if ($Service) {
        & $ComposeCmd -f $InfraFile -f $ServicesFile logs -f --tail=100 --no-color --no-log-prefix $Service
    } else {
        & $ComposeCmd -f $InfraFile -f $ServicesFile logs -f --tail=100 --no-color
    }
}

function Clean-Environment {
    Write-Info "Wiping host build directories..."
    $buildPaths = @(".\build", ".\gateway\build", ".\shared\build", ".\gateway\.gradle", ".\shared\.gradle")
    foreach ($path in $buildPaths) {
        if (Test-Path $path) {
            Remove-Item -Path $path -Recurse -Force -ErrorAction SilentlyContinue
            Write-Info "  Deleted: $path"
        }
    }

    if ($Service) {
        Write-Info "Nuking specific service: '$Service'..."
        & $ComposeCmd -f $InfraFile -f $ServicesFile rm -f -s -v $Service 2>$null | Out-Null

        Write-Info "Searching and destroying images for '$Service'..."
        $images = docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | Where-Object { $_ -match $Service }
        if ($images) {
            foreach ($line in $images) {
                $imageId = ($line -split '\s+')[-1]
                Write-Info "  Removing image ID: $imageId"
                docker rmi -f $imageId 2>$null | Out-Null
            }
        } else {
            Write-Info "  No specific images found to delete."
        }

        docker builder prune -f --filter "label=com.docker.compose.service=$Service" 2>$null | Out-Null
        Write-Success "Service '$Service' completely purged from host and Docker."
    } else {
        Write-Info "Nuking EVERYTHING (all project containers, volumes, images, and cache)..."

        & $ComposeCmd -f $InfraFile -f $ServicesFile down -v --remove-orphans 2>$null | Out-Null

        $projectName = "veridraw"
        $images = docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | Where-Object { $_ -match $projectName -or $_ -match "gateway" -or $_ -match "shared" }
        if ($images) {
            foreach ($line in $images) {
                $imageId = ($line -split '\s+')[-1]
                Write-Info "  Removing image ID: $imageId"
                docker rmi -f $imageId 2>$null | Out-Null
            }
        }

        Write-Info "Clearing global Docker build cache..."
        docker builder prune -a -f 2>$null | Out-Null

        Write-Success "TOTAL WIPE COMPLETE. The next build will be 100% fresh."
    }
}

function Invoke-Main {
    Test-Docker

    switch ($Command) {
        "up"      { Start-Stack }
        "down"    { Stop-Stack }
        "rebuild" { Rebuild-Services }
        "status"  { Show-Status }
        "logs"    { Show-Logs }
        "clean"   { Clean-Environment }
        "help"    {
            Write-Host "Docker Compose Manager" -ForegroundColor White
            Write-Host "Usage: .\manage.ps1 [command] [service]" -ForegroundColor White
            Write-Host ""
            Write-Host "Commands:" -ForegroundColor White
            Write-Host "  up       Start all stacks (or specific service)" -ForegroundColor White
            Write-Host "  down     Stop all containers (or specific service)" -ForegroundColor White
            Write-Host "  rebuild  FAST rebuild using cache (for daily dev)" -ForegroundColor White
            Write-Host "  status   Show containers and published ports" -ForegroundColor White
            Write-Host "  logs     Tail logs (e.g., .\manage.ps1 logs gateway)" -ForegroundColor White
            Write-Host "  clean    Nuke EVERYTHING: host build files, containers, images, volumes, cache" -ForegroundColor White
            Write-Host "  help     Show this message" -ForegroundColor White
            Write-Host ""
            Write-Host "Examples for 100% clean build:" -ForegroundColor Yellow
            Write-Host "  .\manage.ps1 clean gateway   # Wipes all traces" -ForegroundColor White
            Write-Host "  .\manage.ps1 rebuild gateway # Builds 100% fresh from scratch" -ForegroundColor White
        }
        default { Write-ErrorCustom "Unknown command. Use 'help'." }
    }
}

Invoke-Main