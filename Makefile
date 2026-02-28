ENV_FILE ?= .env
COMPOSE := docker compose --env-file $(ENV_FILE) -f docker/compose.yaml

.PHONY: env-init test run compose-up compose-down compose-logs compose-ps rebuild restart \
	fail ok psql signal config incident-open incident-history alert-list alerts-open state-list \
	demo-critical demo-recover demo-cycle fmt fmt-check

env-init:
	cp -n .env.example $(ENV_FILE) || true

test:
	./gradlew test --quiet --no-daemon

fmt:
	./gradlew spotlessApply --no-daemon

fmt-check:
	./gradlew spotlessCheck --no-daemon

run:
	DOZOR_CONFIG=config/dozor.yaml ./gradlew run --no-daemon

compose-up:
	$(COMPOSE) up --build

compose-down:
	$(COMPOSE) down -v

compose-logs:
	$(COMPOSE) logs -f dozor postgres redis mock-health

compose-ps:
	$(COMPOSE) ps

rebuild:
	$(COMPOSE) build --no-cache

restart:
	$(COMPOSE) restart dozor

fail:
	curl "http://localhost:$${MOCK_HEALTH_PORT:-18080}/toggle?healthy=false"

ok:
	curl "http://localhost:$${MOCK_HEALTH_PORT:-18080}/toggle?healthy=true"

psql:
	docker exec -it $$(docker ps -qf name=postgres) psql -U $${POSTGRES_USERNAME:-dozor} -d $${POSTGRES_DB:-dozor}

incident-open:
	docker exec -i $$(docker ps -qf name=postgres) psql -U $${POSTGRES_USERNAME:-dozor} -d $${POSTGRES_DB:-dozor} \
	  -c "SELECT id, root_component_id, started_at, status FROM incidents WHERE status = 0 ORDER BY started_at DESC;"

incident-history:
	docker exec -i $$(docker ps -qf name=postgres) psql -U $${POSTGRES_USERNAME:-dozor} -d $${POSTGRES_DB:-dozor} \
	  -c "SELECT id, root_component_id, started_at, resolved_at, status FROM incidents ORDER BY started_at DESC LIMIT 20;"

alert-list:
	docker exec -i $$(docker ps -qf name=postgres) psql -U $${POSTGRES_USERNAME:-dozor} -d $${POSTGRES_DB:-dozor} \
	  -c "SELECT incident_id, type, sent_at, channel FROM alerts ORDER BY sent_at DESC LIMIT 20;"

alerts-open:
	docker exec -i $$(docker ps -qf name=postgres) psql -U $${POSTGRES_USERNAME:-dozor} -d $${POSTGRES_DB:-dozor} \
	  -c "SELECT incident_id, type, sent_at, channel FROM alerts WHERE type = 0 ORDER BY sent_at DESC LIMIT 20;"

state-list:
	docker exec -i $$(docker ps -qf name=postgres) psql -U $${POSTGRES_USERNAME:-dozor} -d $${POSTGRES_DB:-dozor} \
	  -c "SELECT component_id, state, last_evaluated_at, version FROM component_state ORDER BY last_evaluated_at DESC;"

demo-critical:
	@echo "==> trigger failure"
	@$(MAKE) fail
	@echo "==> wait for critical transition ($${DEMO_CRITICAL_WAIT:-16}s)"
	@sleep $${DEMO_CRITICAL_WAIT:-16}
	@echo "==> component states"
	@$(MAKE) state-list
	@echo "==> open incidents"
	@$(MAKE) incident-open
	@echo "==> recent alerts"
	@$(MAKE) alert-list

demo-recover:
	@echo "==> trigger recovery"
	@$(MAKE) ok
	@echo "==> wait for recovery propagation ($${DEMO_RECOVERY_WAIT:-16}s)"
	@sleep $${DEMO_RECOVERY_WAIT:-16}
	@echo "==> component states"
	@$(MAKE) state-list
	@echo "==> incident history"
	@$(MAKE) incident-history
	@echo "==> recent alerts"
	@$(MAKE) alert-list

demo-cycle:
	@$(MAKE) demo-critical
	@$(MAKE) demo-recover

signal:
	curl -X POST http://localhost:$${API_PORT:-8080}/signal \
	  -H "Content-Type: application/json" \
	  -H "Idempotency-Key: make-demo-1" \
	  -d '{"component":"database","severity":"CRITICAL","source":"manual","occurredAt":"2026-02-22T12:00:00Z"}'

config:
	$(COMPOSE) config
