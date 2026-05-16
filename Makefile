# Makefile for VeriDraw (Linux/Mac)
# Windows users: use .\infra.ps1 instead

.PHONY: help infra-up infra-down infra-logs infra-restart infra-status demo-token clean

# Цвета для вывода
GREEN := \033[0;32m
YELLOW := \033[0;33m
CYAN := \033[0;36m
NC := \033[0m

help:
	@echo ""
	@echo -e "$(GREEN)VeriDraw — Infrastructure Commands (Linux/Mac)$(NC)"
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@echo "Targets:"
	@echo "  infra-up       Start all infrastructure services"
	@echo "  infra-down     Stop all infrastructure services"
	@echo "  infra-logs     Show logs from all services"
	@echo "  infra-restart  Restart all services"
	@echo "  infra-status   Show status of all services"
	@echo "  demo-token     Get JWT token for demo-user via Keycloak"
	@echo "  clean          Remove all volumes and reset infrastructure state"
	@echo ""
	@echo "Quick Start:"
	@echo "  1. make infra-up"
	@echo "  2. make demo-token"
	@echo "  3. Use token in Authorization header: Bearer <token>"
	@echo ""
	@echo "Windows users: use '.\infra.ps1 <command>' instead"

infra-up:
	@echo -e "$(YELLOW)Starting VeriDraw infrastructure...$(NC)"
	docker compose -f docker-compose.infra.yml up -d
	@echo ""
	@echo -e "$(GREEN)Infrastructure started!$(NC)"
	@echo ""
	@echo -e "$(CYAN)Services:$(NC)"
	@echo "  • PostgreSQL:   localhost:5432 (veridraw/veridraw_password)"
	@echo "  • Redis:        localhost:6379"
	@echo "  • Kafka:        localhost:9092"
	@echo "  • Keycloak:     http://localhost:8080 (admin/admin)"
	@echo "  • Grafana:      http://localhost:3000 (admin/admin)"
	@echo "  • Prometheus:   http://localhost:9090"
	@echo "  • Jaeger:       http://localhost:16686"
	@echo "  • MailHog:      http://localhost:8025"
	@echo ""
	@echo -e "$(YELLOW)Check status: make infra-status$(NC)"

infra-down:
	@echo -e "$(YELLOW)Stopping VeriDraw infrastructure...$(NC)"
	docker compose -f docker-compose.infra.yml down

infra-logs:
	docker compose -f docker-compose.infra.yml logs -f

infra-restart: infra-down infra-up

infra-status:
	@echo -e "$(CYAN)VeriDraw Infrastructure Status:$(NC)"
	docker compose -f docker-compose.infra.yml ps

demo-token:
	@echo -e "$(YELLOW)Requesting JWT token for demo-user...$(NC)"
	@curl -s -X POST http://localhost:8080/realms/veridraw/protocol/openid-connect/token \
		-d "client_id=veridraw-api" \
		-d "client_secret=veridraw-api-secret-key-2026" \
		-d "username=demo-user" \
		-d "password=password123" \
		-d "grant_type=password" | jq -r '.access_token' 2>/dev/null || \
		(echo "jq not installed or failed. Install jq or use manual curl command." && \
		curl -s -X POST http://localhost:8080/realms/veridraw/protocol/openid-connect/token \
			-d "client_id=veridraw-api" \
			-d "client_secret=veridraw-api-secret-key-2026" \
			-d "username=demo-user" \
			-d "password=password123" \
			-d "grant_type=password")
	@echo ""
	@echo -e "$(GREEN)Usage: Authorization: Bearer <token>$(NC)"

clean:
	@echo -e "$(YELLOW)WARNING: This will remove ALL containers and volumes!$(NC)"
	@read -p "Are you sure? Type 'yes' to continue: " confirm && \
	if [ "$$confirm" = "yes" ]; then \
		docker compose -f docker-compose.infra.yml down -v; \
		echo -e "$(GREEN)Infrastructure cleaned. Run 'make infra-up' to start fresh.$(NC)"; \
	else \
		echo "Cancelled."; \
	fi