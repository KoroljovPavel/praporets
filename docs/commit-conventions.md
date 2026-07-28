# Commit conventions

Проект використовує [Conventional Commits](https://www.conventionalcommits.org/): `тип(скоуп): опис`. Скоуп опційний. Опис — у теперішньому часі, малими літерами, без крапки в кінці, англійською.

## Типи

| Тип | Коли використовувати | Приклад |
|---|---|---|
| `feat` | Нова функціональність (те, що видно «користувачу» коду) | `feat: implement cumulative bucketing` |
| `fix` | Виправлення багу | `fix: handle empty rollout bucket list` |
| `test` | Тільки тести, поведінка коду не змінюється | `test: rollout must be monotonic when weights increase` |
| `docs` | Тільки документація/коментарі | `docs: add ADR-011 on cumulative bucketing` |
| `refactor` | Зміна структури коду без зміни поведінки | `refactor: extract clause matching into sealed hierarchy` |
| `build` | Збірка, залежності, Gradle | `build: bootstrap Gradle skeleton with Java 25 toolchain` |
| `chore` | Дрібна рутина, що нікуди не влазить | `chore: bump assertj to 3.27.8` |
| `ci` | GitHub Actions і подібне | `ci: add integration test job` |
| `perf` | Оптимізація продуктивності | `perf: cache compiled clause matchers` |

## Правила

1. **Один коміт = одна логічна зміна**, яку можна описати одним реченням без «and». Тип визначається змістом зміни, а не типами файлів.
2. Фіча разом зі своїми тестами, написаними одночасно, — це **один** коміт `feat:`.
3. **TDD-цикл у `praporets-core` — два окремі коміти:**
   - `test: <очікувана поведінка>` — тест-файл + порожній скелет класу (сигнатури кидають `UnsupportedOperationException`). Компілюється, тест червоний.
   - `feat: <реалізація>` — тіло методів, тест зелений.

   Пара `test:` → `feat:` у `git log` — доказ test-first, який вимагає спека проекту.
4. Кожен виправлений баг починається з коміту `test:`, що відтворює баг (червоний), потім `fix:` (зелений).
5. Вибіркове стейджування для роздільних комітів: `git add <шлях>` або галочки у вікні Commit в IntelliJ (можна навіть окремі чанки файлу).