# Бенчмарки: flag-edge JVM vs GraalVM native

## Методологія

- Машина: MacBook Pro, Apple M1 Max (10 cores: 8P+2E), 32 GB RAM, macOS 26.5.2, Docker 27.3.1 (контейнери — в Linux-VM
  Docker Desktop, aarch64; порівняння коректне, абсолютні числа не переносяться на bare metal)
- Дата замірів: 2026-07-31
- Обидва режими — **в однакових умовах**: той самий контейнерний base-image запуску, той самий CP на хості, той самий
  env `dev` з одним флагом.
- Кожен замір — 3 прогони, у таблицю йде медіана.

### Команди

**JVM (fast-jar):**

```bash
./gradlew :flag-edge:build
docker run --rm -p 8081:8081 \
  -v "$PWD/flag-edge/build/quarkus-app:/app:ro" \
  eclipse-temurin:25-jre \
  java -Dquarkus.grpc.clients.config.host=host.docker.internal -jar /app/quarkus-run.jar
# УВАГА: у JVM-режимі -D має стояти ДО -jar (після — це вже аргументи програми,
# JVM їх ігнорує). Native-бінарник, навпаки, приймає -D у хвості — його лаунчер
# парсить аргументи сам.
```

**Native:**

```bash
./gradlew :flag-edge:build -Dquarkus.native.enabled=true \
  -Dquarkus.package.jar.enabled=false -Dquarkus.native.container-build=true
docker run --rm -p 8081:8081 \
  -v "$PWD/flag-edge/build/flag-edge-0.1.0-runner:/app/flag-edge:ro" \
  registry.access.redhat.com/ubi9/ubi-minimal \
  /app/flag-edge -Dquarkus.grpc.clients.config.host=host.docker.internal
```

### Що і як міряємо

| Метрика                | Як                                                                              |
|------------------------|---------------------------------------------------------------------------------|
| Startup                | рядок `started in X.XXXs` з лога Quarkus                                        |
| Час до готовності      | від `docker run` до першого `200` з `/q/health/ready` (цикл `curl` раз на 50мс) |
| RSS idle               | `docker stats --no-stream` через ~10с після готовності, до трафіку              |
| RSS після навантаження | `docker stats --no-stream` після 1000 запитів `POST /api/v1/evaluate`           |
| Латентність evaluate   | 1000 послідовних `curl -w '%{time_total}'` → медіана і максимум                 |
| Розмір артефакту       | JVM: `du -sh flag-edge/build/quarkus-app`; native: `ls -lh *-runner`            |

## Результати

| Метрика                        | JVM       | Native   | Різниця                   |
|--------------------------------|-----------|----------|---------------------------|
| Startup (лог Quarkus)          | 0.821s    | 0.043s   | native ×19 швидше         |
| Час до готовності              | 1.19s     | 0.28s    | native ×4.3 швидше        |
| RSS idle                       | 223.5 MiB | 62.3 MiB | native ×3.6 менше (−72%)  |
| RSS після 1000 запитів         | 225.9 MiB | 92.5 MiB | native ×2.4 менше (−59%)  |
| Латентність evaluate, медіана  | 7.1ms     | 5.2ms    | native ×1.4 швидше (−27%) |
| Латентність evaluate, максимум | 209.5ms   | 15.9ms   | native ×13 менше          |
| Розмір артефакту               | 37M       | 69M      | **JVM** ×1.9 менше¹       |

¹ Порівняння артефактів оманливе: 37M fast-jar не працює без JRE (образ `eclipse-temurin:25-jre` ≈ +200 MiB), а 69M
native — самодостатній бінарник поверх ~40 MiB `ubi-minimal`. Повна поставка: ≈240 MiB (JVM)
проти ≈110 MiB (native) — у розмірі ОБРАЗУ native виграє ~×2.

## Спостереження

Native розгромно виграє стартом (×19) і часом до готовності (×4.3), а RSS утричі менший — для edge, який
масштабується репліками і часто рестартує, це головні метрики. Несподіванка — медіана латентності:
лише ×1.4, прогрітий JIT майже наздоганяє AOT на гарячому шляху. Зате максимум відрізняється у ×13: у JVM перші запити
ловлять JIT-прогрів (209мс), native відповідає стабільно з першого запиту. Висновок: native тут не про «швидше взагалі»,
а про миттєвий старт, дешеву пам'ять і передбачуваний хвіст — рівно той профіль, що потрібен stateless data-plane
сервісу.

> Історична довідка: перший разовий замір native у 02c (2026-07-29) —
> startup 0.100s, RSS ≈ 52.9 MiB. Тут — систематична пара з методологією;
> числа можуть відрізнятись (з 02e додались REST+validator, з 02f — unified
> server).
