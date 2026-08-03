.PHONY: dev dev-down test-e2e
dev:
	docker compose up -d --wait
dev-down:
	docker compose down -v
test-e2e:
	./gradlew :e2e:test
