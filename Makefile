# Три режими запуску: dev = Compose + сервіси в IDE,
# up = kind + Helm повний стек, CI — GitHub Actions.

KIND_CLUSTER := praporets
NS           := praporets
CHART        := infra/helm/praporets
IMAGE_PREFIX := ghcr.io/koroljovpavel/praporets
IMAGES       := $(IMAGE_PREFIX)-control-plane:local \
                $(IMAGE_PREFIX)-flag-edge:local \
                $(IMAGE_PREFIX)-analytics:local

# EDGE=native (дефолт, ідентичність проекту) або EDGE=jvm (швидкі ітерації
# над чартом/інфрою: та сама назва образу й тег — Helm різниці не бачить).
# Аргументація: steps/04a-kind-helm-jib.md, рішення I2.
EDGE ?= native
ifeq ($(EDGE),native)
EDGE_FLAGS := -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false -Dquarkus.native.container-build=true
else
EDGE_FLAGS :=
endif

.PHONY: dev dev-down test-e2e images kind-up kind-load up down urls

dev:
	docker compose up -d --wait
dev-down:
	docker compose down -v
test-e2e:
	./gradlew :e2e:test

# Образи в ЛОКАЛЬНИЙ Docker daemon (push — справа CI):
# CP/analytics — Jib-плагін (jibDockerBuild), flag-edge — Quarkus imageBuild
# (не :flag-edge:build — той тягне повільні інтеграційні тести).
images:
	./gradlew :control-plane:jibDockerBuild :analytics:jibDockerBuild
	./gradlew :flag-edge:imageBuild $(EDGE_FLAGS)

kind-up:
	@kind get clusters 2>/dev/null | grep -qx $(KIND_CLUSTER) \
		|| kind create cluster --config infra/kind/cluster.yaml

kind-load:
	kind load docker-image $(IMAGES) --name $(KIND_CLUSTER)

up: kind-up images kind-load
	helm upgrade --install praporets $(CHART) \
		--namespace $(NS) --create-namespace \
		-f $(CHART)/values-local.yaml
	kubectl -n $(NS) rollout status statefulset/postgres --timeout=180s
	kubectl -n $(NS) rollout status statefulset/kafka --timeout=180s
	kubectl -n $(NS) rollout status deployment/control-plane --timeout=300s
	kubectl -n $(NS) rollout status deployment/flag-edge --timeout=300s
	kubectl -n $(NS) rollout status deployment/analytics --timeout=300s
	@$(MAKE) --no-print-directory urls

down:
	kind delete cluster --name $(KIND_CLUSTER)

urls:
	@echo ""
	@echo "Praporets у kind — сервіси:"
	@echo "  control-plane REST   http://localhost:30080   (health: /actuator/health)"
	@echo "  control-plane gRPC   localhost:30090          (grpcurl -plaintext localhost:30090 list)"
	@echo "  flag-edge REST+gRPC  http://localhost:30081   (health: /q/health; gRPC на тому ж порту)"
	@echo "  analytics REST       http://localhost:30082   (health: /actuator/health)"
	@echo ""
