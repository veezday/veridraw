# Makefile for Docker Compose Stack Management
# Usage: make <target> [SERVICE=name]

.PHONY: up down rebuild status logs clean help check-docker

# Configuration
COMPOSE_CMD := docker compose
INFRA_FILE  := docker-compose.infra.yml
SERVICES_FILE := docker-compose.services.yml
COMPOSE_FLAGS := -f $(INFRA_FILE) -f $(SERVICES_FILE)

# Capture optional service argument (e.g., make rebuild gateway)
SERVICE ?= $(filter-out $@,$(MAKECMDGOALS))

# ANSI Colors
CYAN    := \033[36m
GREEN   := \033[32m
RED     := \033[31m
YELLOW  := \033[33m
WHITE   := \033[37m
RESET   := \033[0m

# Output helpers
define info
	@echo -e "$(CYAN)[ℹ️] $(1)$(RESET)"
endef
define success
	@echo -e "$(GREEN)[✅] $(1)$(RESET)"
endef
define error
	@echo -e "$(RED)[❌] $(1)$(RESET)"
endef

# Pre-flight environment check
check-docker:
	@command -v $(COMPOSE_CMD) >/dev/null 2>&1 || { \
		command -v docker-compose >/dev/null 2>&1 && eval COMPOSE_CMD=docker-compose || { \
			$(call error,Docker Compose is not installed or not in PATH.); exit 1; }; \
	}
	@docker info >/dev/null 2>&1 || { \
		$(call error,Docker is not running. Start it and retry.); exit 1; \
	}
	@$(call info,Docker environment verified.)

# Targets
up: check-docker
	$(call info,Starting infrastructure & services...)
	@$(COMPOSE_CMD) $(COMPOSE_FLAGS) up -d
	@$(call success,Stack started.)

down: check-docker
	$(call info,Stopping stacks...)
	@$(COMPOSE_CMD) $(COMPOSE_FLAGS) down
	@$(call success,Stacks stopped.)

rebuild: check-docker
	$(call info,Rebuilding services (dev mode)...)
	@DOCKER_BUILDKIT=1 $(COMPOSE_CMD) $(COMPOSE_FLAGS) up -d --build --force-recreate $(SERVICE)
	@$(call success,Services rebuilt & restarted.)

status: check-docker
	$(call info,Containers & Port Mappings:)
	@$(COMPOSE_CMD) $(COMPOSE_FLAGS) ps
	@echo -e "\n$(YELLOW)🔍 Listening Application Ports:$(RESET)"
	@docker ps --format "table {{.Names}}\t{{.Ports}}" | grep "0.0.0.0" || true

logs: check-docker
	@$(call info,Tailing logs for: $(if $(SERVICE),$(SERVICE),all))
	@$(COMPOSE_CMD) $(COMPOSE_FLAGS) logs -f --tail=100 $(SERVICE)

clean: check-docker
	$(call info,Cleaning volumes, networks & local images...)
	@$(COMPOSE_CMD) $(COMPOSE_FLAGS) down -v --rmi local --remove-orphans
	@$(call success,Cleanup complete.)

help:
	@echo -e "$(WHITE)🐳 Docker Compose Manager$(RESET)"
	@echo -e "Usage: make [command] [SERVICE=name]\n"
	@echo -e "Commands:"
	@echo -e "  up       Start all stacks (infra + services)"
	@echo -e "  down     Stop all containers"
	@echo -e "  rebuild  Rebuild & restart services (use during dev)"
	@echo -e "  status   Show containers & published ports"
	@echo -e "  logs     Tail logs (optionally: make logs gateway)"
	@echo -e "  clean    Remove volumes, networks, and local images"
	@echo -e "  help     Show this message\n"
	@echo -e "Examples:"
	@echo -e "  make rebuild"
	@echo -e "  make logs gateway"
	@echo -e "  make status"

# Default target
.DEFAULT_GOAL := help