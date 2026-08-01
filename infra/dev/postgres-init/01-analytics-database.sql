-- Виконується entrypoint-ом Postgres ТІЛЬКИ при ініціалізації порожнього
-- volume (03d-1, камінь #1). Окрема БД для analytics — розділення даних
-- між сервісами (спека §5.2); схему в ній веде Flyway самого analytics.
CREATE
DATABASE praporets_analytics OWNER praporets;
