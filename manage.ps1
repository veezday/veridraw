param (
    [Parameter(Mandatory=$true, Position=0)]
    [string]$Command,

    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$Services
)

# =============================================================================
# CONFIGURATION
# =============================================================================
$ProjectName        = "veridraw"
$KeycloakUrl        = "http://localhost:8080"
$KeycloakRealm      = "veridraw"
$KeycloakClientId   = "veridraw-api"
$KeycloakClientSecret = "veridraw-api-secret-key-2026"
$KeycloakUsername   = "demo-admin"
$KeycloakPassword   = "admin123"
$KeycloakGrantType  = "password"
$GrafanaUrl         = "http://localhost:3000"

# Derived values
$TokenEndpoint = "$KeycloakUrl/realms/$KeycloakRealm/protocol/openid-connect/token"
$ScriptsDir    = ".\infra\k6"

# =============================================================================
# MAIN
# =============================================================================
$TargetInfo = if ($Services.Count -gt 0) { $Services -join ", " } else { "all services" }

switch ($Command.ToLower()) {
    "up" {
        Write-Host "Starting: $TargetInfo" -ForegroundColor Green
        & docker compose -p $ProjectName up -d @Services
    }
    "down" {
        Write-Host "Stopping and removing containers for: $TargetInfo" -ForegroundColor Yellow
        & docker compose -p $ProjectName down @Services
    }
    "log" {
        Write-Host "Showing logs for: $TargetInfo" -ForegroundColor Cyan
        & docker compose -p $ProjectName logs -f @Services
    }
    "status" {
        Write-Host "Status of: $TargetInfo" -ForegroundColor Cyan
        & docker compose -p $ProjectName ps @Services
    }
    "rebuild" {
        Write-Host "Rebuilding and restarting: $TargetInfo" -ForegroundColor Magenta
        & docker compose -p $ProjectName build @Services
        & docker compose -p $ProjectName up -d --force-recreate @Services
    }
    "rebuild-clean" {
        Write-Host "Clean rebuilding (no cache) and restarting: $TargetInfo" -ForegroundColor Magenta
        & docker compose -p $ProjectName build --no-cache @Services
        & docker compose -p $ProjectName up -d --force-recreate @Services
    }
    "clean" {
        if ($Services.Count -gt 0) {
            Write-Host "Completely removing: $TargetInfo (containers, volumes, local images)" -ForegroundColor Red
            & docker compose -p $ProjectName down --volumes --rmi local @Services
            Write-Host "Cleanup complete for: $TargetInfo" -ForegroundColor Green
        } else {
            Write-Host "WARNING: This will remove ALL containers, networks, images, and volumes for project '$ProjectName'." -ForegroundColor Red
            $confirmation = Read-Host "Are you sure? (y/n)"
            if ($confirmation -eq 'y' -or $confirmation -eq 'Y') {
                Write-Host "Nuking project '$ProjectName'..." -ForegroundColor Yellow
                & docker compose -p $ProjectName down --volumes --remove-orphans --rmi all
                & docker volume prune --force --filter "name=^$ProjectName_"
                Write-Host "Global cleanup complete." -ForegroundColor Green
            } else {
                Write-Host "Cleanup cancelled." -ForegroundColor Yellow
            }
        }
    }
    "token" {
        Write-Host "[INFO] Fetching access token..." -ForegroundColor Cyan

        $Body = @{
            client_id     = $KeycloakClientId
            client_secret = $KeycloakClientSecret
            username      = $KeycloakUsername
            password      = $KeycloakPassword
            grant_type    = $KeycloakGrantType
        }

        try {
            $response = Invoke-WebRequest -Uri $TokenEndpoint `
                -Method POST `
                -ContentType "application/x-www-form-urlencoded" `
                -Body $Body `
                -UseBasicParsing

            $tokenData = $response.Content | ConvertFrom-Json
            Write-Host "Access Token:" -ForegroundColor Green
            Write-Host $tokenData.access_token -ForegroundColor White
        }
        catch {
            Write-Host "[ERROR] Failed to retrieve access token. Is Keycloak running and credentials correct?" -ForegroundColor Red
            Write-Host "Details: $($_.Exception.Message)" -ForegroundColor Yellow
            exit 1
        }
    }
    "load-test" {
        Write-Host "[INFO] Checking infrastructure readiness..." -ForegroundColor Cyan
        $isRunning = docker compose -p $ProjectName ps prometheus --format "{{.State}}" 2>$null | Select-String -Pattern "running"
        if (-not $isRunning) {
            Write-Host "[ERROR] Infrastructure is not running or not ready." -ForegroundColor Red
            Write-Host "Please run first: .\manage.ps1 up" -ForegroundColor Yellow
            exit 1
        }

        if (-not (Test-Path $ScriptsDir)) {
            Write-Host "[ERROR] Directory $ScriptsDir not found." -ForegroundColor Red
            exit 1
        }

        $scriptsToRun = @()
        if ($Services.Count -gt 0) {
            foreach ($script in $Services) {
                $fullPath = Join-Path $ScriptsDir $script
                if (Test-Path $fullPath) { $scriptsToRun += $script }
                else { Write-Host "[WARN] Script not found: $script" -ForegroundColor Yellow }
            }
        } else {
            $scriptsToRun = (Get-ChildItem -Path $ScriptsDir -Filter "*.js").Name
        }

        if ($scriptsToRun.Count -eq 0) {
            Write-Host "[ERROR] No scripts found to run." -ForegroundColor Red
            exit 1
        }

        Write-Host "[INFO] Starting load tests ($($scriptsToRun.Count) script(s))..." -ForegroundColor Green
        foreach ($script in $scriptsToRun) {
            Write-Host ">> Running: $script" -ForegroundColor Cyan
            & docker compose -p $ProjectName run --rm k6 run "/scripts/$script"
            Write-Host ">> Completed: $script" -ForegroundColor Green
            Write-Host "----------------------------------------"
        }
        Write-Host "[DONE] All tests finished. Open Grafana: $GrafanaUrl" -ForegroundColor Green
    }
    "help" {
        Write-Host "Usage: .\manage.ps1 <command> [service1 service2 ...]" -ForegroundColor Cyan
        Write-Host "Commands:" -ForegroundColor Cyan
        Write-Host "  up            - Start services (default: all)" -ForegroundColor White
        Write-Host "  down          - Stop and remove containers (default: all)" -ForegroundColor White
        Write-Host "  log           - View logs (default: all)" -ForegroundColor White
        Write-Host "  status        - Show current status (default: all)" -ForegroundColor White
        Write-Host "  rebuild       - Rebuild (with cache) and restart (default: all)" -ForegroundColor White
        Write-Host "  rebuild-clean - Full rebuild (no cache) and restart (default: all)" -ForegroundColor White
        Write-Host "  clean         - Remove containers, volumes, and images. (No args = ALL project. With args = ONLY specified services)" -ForegroundColor White
        Write-Host "  token         - Fetch and display access token from Keycloak" -ForegroundColor White
        Write-Host "  load-test     - Run k6 tests (args are script names, e.g., 01.js)" -ForegroundColor White
        Write-Host "  help          - Show this help message" -ForegroundColor White
    }
    default {
        Write-Host "Unknown command: $Command" -ForegroundColor Red
        Write-Host "Run '.\manage.ps1 help' for usage information." -ForegroundColor Yellow
    }
}