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

# Платформа образів = архітектура машини (дефолт Jib — amd64 НЕЗАЛЕЖНО від
# хоста: на Apple Silicon це Rosetta-емуляція для JVM-образів і зламаний
# native-edge — arm64-бінарник в amd64-базі). CP/analytics рахують те саме
# в своїх jib{}-блоках; Quarkus-у передаємо явно
UNAME_M := $(shell uname -m)
ifneq (,$(filter $(UNAME_M),arm64 aarch64))
JIB_PLATFORM := linux/arm64/v8
else
JIB_PLATFORM := linux/amd64
endif

.PHONY: dev dev-down test-e2e images kind-up metrics-server kind-load seed-env up down urls

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
	./gradlew :flag-edge:imageBuild $(EDGE_FLAGS) -Dquarkus.jib.platforms=$(JIB_PLATFORM)

kind-up:
	@kind get clusters 2>/dev/null | grep -qx $(KIND_CLUSTER) \
		|| kind create cluster --config infra/kind/cluster.yaml

kind-load:
	kind load docker-image $(IMAGES) --name $(KIND_CLUSTER)

# metrics-server (04b, рішення I1): metrics.k8s.io для HPA за CPU —
# kind його з коробки не має. Манiфест запінений локально
# (infra/kind/metrics-server.yaml, v0.9.0 + --kubelet-insecure-tls);
# apply ідемпотентний, тому викликається на кожен up.
metrics-server:
	kubectl apply -f infra/kind/metrics-server.yaml
	kubectl -n kube-system rollout status deployment/metrics-server --timeout=120s

# Демо-сід (04b, рішення I7): readiness edge = «снапшот завантажено» (02c),
# а снапшот environment-а, якого немає в БД, — це NOT_FOUND у retry-циклі.
# На свіжому кластері БЕЗ environment `dev` rollout status flag-edge не
# завершився б ніколи. 201 = створено, 409 = уже існує (ідемпотентність).
seed-env:
	@code=$$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:30080/api/v1/environments \
		-H 'Content-Type: application/json' -d '{"key":"dev","name":"Dev"}'); \
	case $$code in 201|409) echo "environment dev: HTTP $$code (ok)";; \
	*) echo "environment dev: HTTP $$code — CP не готовий?" >&2; exit 1;; esac

up: kind-up metrics-server images kind-load
	helm upgrade --install praporets $(CHART) \
		--namespace $(NS) --create-namespace \
		-f $(CHART)/values-local.yaml
	kubectl -n $(NS) rollout status statefulset/postgres --timeout=180s
	kubectl -n $(NS) rollout status statefulset/kafka --timeout=180s
	kubectl -n $(NS) rollout status deployment/control-plane --timeout=300s
	@$(MAKE) --no-print-directory seed-env
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
