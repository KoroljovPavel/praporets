.PHONY: dev dev-down
dev:        ## Postgres (+ пізніше Kafka, Grafana) для локальної розробки
	docker compose up -d --wait
dev-down:
	docker compose down -v
