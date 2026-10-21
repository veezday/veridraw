#!/usr/bin/env bash

# =============================================================================
# CONFIGURATION
# =============================================================================
PROJECT_NAME="veridraw"
KEYCLOAK_URL="http://localhost:8080"
KEYCLOAK_REALM="veridraw"
KEYCLOAK_CLIENT_ID="veridraw-api"
KEYCLOAK_CLIENT_SECRET="veridraw-api-secret-key-2026"
KEYCLOAK_USERNAME="demo-admin"
KEYCLOAK_PASSWORD="admin123"
KEYCLOAK_GRANT_TYPE="password"
GRAFANA_URL="http://localhost:3000"

# Derived values
TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"
SCRIPTS_DIR="./infra/k6"

# =============================================================================
# COLORS
# =============================================================================
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
RED='\033[0;31m'
WHITE='\033[1;37m'
NC='\033[0m'

# =============================================================================
# MAIN
# =============================================================================
COMMAND=$(echo "$1" | tr '[:upper:]' '[:lower:]')
shift || true

SERVICES=("$@")

if [ ${#SERVICES[@]} -gt 0 ]; then
    IFS=', '
    TARGET_INFO="${SERVICES[*]}"
    unset IFS
else
    TARGET_INFO="all services"
fi

case "$COMMAND" in
    "up")
        echo -e "${GREEN}Starting: $TARGET_INFO${NC}"
        docker compose -p "$PROJECT_NAME" up -d "${SERVICES[@]}"
        ;;
    "down")
        echo -e "${YELLOW}Stopping and removing containers for: $TARGET_INFO${NC}"
        docker compose -p "$PROJECT_NAME" down "${SERVICES[@]}"
        ;;
    "log")
        echo -e "${CYAN}Showing logs for: $TARGET_INFO${NC}"
        docker compose -p "$PROJECT_NAME" logs -f "${SERVICES[@]}"
        ;;
    "status")
        echo -e "${CYAN}Status of: $TARGET_INFO${NC}"
        docker compose -p "$PROJECT_NAME" ps "${SERVICES[@]}"
        ;;
    "rebuild")
        echo -e "${MAGENTA}Rebuilding and restarting: $TARGET_INFO${NC}"
        docker compose -p "$PROJECT_NAME" build "${SERVICES[@]}"
        docker compose -p "$PROJECT_NAME" up -d --force-recreate "${SERVICES[@]}"
        ;;
    "rebuild-clean")
        echo -e "${MAGENTA}Clean rebuilding (no cache) and restarting: $TARGET_INFO${NC}"
        docker compose -p "$PROJECT_NAME" build --no-cache "${SERVICES[@]}"
        docker compose -p "$PROJECT_NAME" up -d --force-recreate "${SERVICES[@]}"
        ;;
    "clean")
        if [ ${#SERVICES[@]} -gt 0 ]; then
            echo -e "${RED}Completely removing: $TARGET_INFO (containers, volumes, local images)${NC}"
            docker compose -p "$PROJECT_NAME" down --volumes --rmi local "${SERVICES[@]}"
            echo -e "${GREEN}Cleanup complete for: $TARGET_INFO${NC}"
        else
            echo -e "${RED}WARNING: This will remove ALL containers, networks, images, and volumes for project '$PROJECT_NAME'.${NC}"
            read -r -p "Are you sure? (y/n): " confirmation
            if [[ "$confirmation" =~ ^[Yy]$ ]]; then
                echo -e "${YELLOW}Nuking project '$PROJECT_NAME'...${NC}"
                docker compose -p "$PROJECT_NAME" down --volumes --remove-orphans --rmi all
                docker volume prune --force --filter "name=^${PROJECT_NAME}_"
                echo -e "${GREEN}Global cleanup complete.${NC}"
            else
                echo -e "${YELLOW}Cleanup cancelled.${NC}"
            fi
        fi
        ;;
    "token")
        echo -e "${CYAN}[INFO] Fetching access token...${NC}"
        TOKEN=$(curl -s -X POST "$TOKEN_ENDPOINT" \
          -H "Content-Type: application/x-www-form-urlencoded" \
          -d "client_id=${KEYCLOAK_CLIENT_ID}&client_secret=${KEYCLOAK_CLIENT_SECRET}&username=${KEYCLOAK_USERNAME}&password=${KEYCLOAK_PASSWORD}&grant_type=${KEYCLOAK_GRANT_TYPE}" \
          | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

        if [ -z "$TOKEN" ]; then
            echo -e "${RED}[ERROR] Failed to retrieve access token. Is Keycloak running and credentials correct?${NC}"
            exit 1
        fi

        echo -e "${GREEN}Access Token:${NC}"
        echo "$TOKEN"
        ;;
    "load-test")
        echo -e "${CYAN}[INFO] Checking infrastructure readiness...${NC}"
        STATE=$(docker compose -p "$PROJECT_NAME" ps prometheus --format "{{.State}}" 2>/dev/null || echo "")
        if [[ "$STATE" != *"running"* ]]; then
            echo -e "${RED}[ERROR] Infrastructure is not running or not ready.${NC}"
            echo -e "${YELLOW}Please run first: ./manage.sh up${NC}"
            exit 1
        fi

        if [ ! -d "$SCRIPTS_DIR" ]; then
            echo -e "${RED}[ERROR] Directory $SCRIPTS_DIR not found.${NC}"
            exit 1
        fi

        SCRIPTS_TO_RUN=()
        if [ ${#SERVICES[@]} -gt 0 ]; then
            for script in "${SERVICES[@]}"; do
                if [ -f "$SCRIPTS_DIR/$script" ]; then
                    SCRIPTS_TO_RUN+=("$script")
                else
                    echo -e "${YELLOW}[WARN] Script not found: $script${NC}"
                fi
            done
        else
            for file in "$SCRIPTS_DIR"/*.js; do
                if [ -f "$file" ]; then
                    SCRIPTS_TO_RUN+=("$(basename "$file")")
                fi
            done
        fi

        if [ ${#SCRIPTS_TO_RUN[@]} -eq 0 ]; then
            echo -e "${RED}[ERROR] No scripts found to run.${NC}"
            exit 1
        fi

        echo -e "${GREEN}[INFO] Starting load tests (${#SCRIPTS_TO_RUN[@]} script(s))...${NC}"
        for script in "${SCRIPTS_TO_RUN[@]}"; do
            echo -e "${CYAN}>> Running: $script${NC}"
            docker compose -p "$PROJECT_NAME" run --rm k6 run "/scripts/$script"
            echo -e "${GREEN}>> Completed: $script${NC}"
            echo "----------------------------------------"
        done
        echo -e "${GREEN}[DONE] All tests finished. Open Grafana: $GRAFANA_URL${NC}"
        ;;
    "help")
        echo -e "${CYAN}Usage: ./manage.sh <command> [service1 service2 ...]${NC}"
        echo -e "${CYAN}Commands:${NC}"
        echo -e "${WHITE}  up            - Start services (default: all)${NC}"
        echo -e "${WHITE}  down          - Stop and remove containers (default: all)${NC}"
        echo -e "${WHITE}  log           - View logs (default: all)${NC}"
        echo -e "${WHITE}  status        - Show current status (default: all)${NC}"
        echo -e "${WHITE}  rebuild       - Rebuild (with cache) and restart (default: all)${NC}"
        echo -e "${WHITE}  rebuild-clean - Full rebuild (no cache) and restart (default: all)${NC}"
        echo -e "${WHITE}  clean         - Remove containers, volumes, and images. (No args = ALL project. With args = ONLY specified services)${NC}"
        echo -e "${WHITE}  token         - Fetch and display access token from Keycloak${NC}"
        echo -e "${WHITE}  load-test     - Run k6 tests (args are script names, e.g., 01.js)${NC}"
        echo -e "${WHITE}  help          - Show this help message${NC}"
        ;;
    *)
        echo -e "${RED}Unknown command: $COMMAND${NC}"
        echo -e "${YELLOW}Run './manage.sh help' for usage information.${NC}"
        ;;
esac