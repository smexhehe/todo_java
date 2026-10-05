# Todo Scheduler

Планировщик задач на Java 21, Spring Boot и PostgreSQL. В проект входят веб-интерфейс, мониторинг Prometheus и Grafana, а также сценарии нагрузочного тестирования.

## Состав проекта

| Компонент | Назначение |
| --- | --- |
| Spring Boot | HTTP API и веб-интерфейс на порту `7540` |
| PostgreSQL 17 | хранение задач |
| HikariCP | пул соединений приложения с PostgreSQL |
| Prometheus | сбор метрик каждые 5 секунд |
| Grafana | дашборд JVM, HTTP и блокировок |
| k6 / JMeter | нагрузочные сценарии |

## Быстрый запуск

Из корня проекта:

```bash
docker compose up -d --build
```

Compose сначала ждёт healthcheck PostgreSQL, затем запускает приложение. Проверить состояние:

```bash
docker compose ps
curl.exe -s http://127.0.0.1:7540/actuator/health
```

Ожидаемый ответ healthcheck:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

Сервисы доступны только с этого компьютера:

| Адрес | Сервис |
| --- | --- |
| <http://127.0.0.1:7540> | Todo Scheduler |
| <http://127.0.0.1:7540/actuator/prometheus> | метрики приложения |
| <http://127.0.0.1:9090/targets> | цели Prometheus |
| <http://127.0.0.1:3000/d/todo-overview> | Grafana dashboard |

Для остановки контейнеров без удаления данных:

```bash
docker compose down
```

PostgreSQL, Prometheus, Grafana и JVM diagnostics используют именованные Docker volumes, поэтому данные сохраняются между запусками.

## Настройки

Можно создать файл `.env` рядом с `docker-compose.yml`:

```dotenv
TODO_PASSWORD=change-me
POSTGRES_PASSWORD=change-db-password
GRAFANA_ADMIN_PASSWORD=change-grafana-password
```

Если `TODO_PASSWORD` задан, для работы с задачами нужно сначала получить cookie через `/api/signin`. Если переменная пуста, авторизация отключена.

## API

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/api/nextdate` | вычислить следующую дату повторения |
| `POST` | `/api/task` | создать задачу |
| `GET` | `/api/tasks?search=текст` | получить до 50 задач или выполнить поиск |
| `GET` | `/api/task?id=1` | получить одну задачу |
| `PUT` | `/api/task` | изменить задачу |
| `DELETE` | `/api/task?id=1` | удалить задачу |
| `POST` | `/api/task/done?id=1` | завершить задачу или перенести повторяющуюся |

Пример создания задачи:

```bash
curl.exe -i -X POST http://127.0.0.1:7540/api/task ^
  -H "Content-Type: application/json" ^
  -d "{\"date\":\"20261010\",\"title\":\"Подготовить отчёт\",\"comment\":\"\",\"repeat\":\"d 7\"}"
```

Дата хранится в формате `yyyyMMdd`. Поддерживаются правила повторения: `d N`, `y`, `w 1,3,5` и `m 1,15,-1`.

## Мониторинг

Prometheus должен показывать цель `todo` в состоянии `UP` на странице `/targets`.

Дашборд **Todo Scheduler: JVM and HTTP** создаётся из файла [`monitoring/grafana/dashboards/todo-overview.json`](monitoring/grafana/dashboards/todo-overview.json). Он provisioned, поэтому изменения панели нужно сохранять в этот JSON-файл, затем перезапускать Grafana или ждать её перечитывания.

На дашборде есть:

- запросы API в секунду, p95/p99 и ответы 5xx;
- heap JVM, паузы и число сборок GC, CPU и RSS процесса;
- активные, свободные и ожидающие соединения Hikari;
- число конфликтов блокировок задач за выбранный период.

Проверить счётчик конфликтов напрямую:

```bash
curl.exe -s http://127.0.0.1:7540/actuator/metrics/todo.tasks.lock.conflicts
```

## Алерты

Prometheus вычисляет правила каждые 5 секунд и передаёт firing alerts в Alertmanager. Локальный Alertmanager доступен на <http://127.0.0.1:9093>; на первом этапе он показывает активные alerts в интерфейсе, но не отправляет сообщения во внешние сервисы.

Правила лежат в `monitoring/alerts/todo.yml`:

| Alert | Условие | Зачем нужен |
| --- | --- | --- |
| `TodoTargetDown` | приложение не scrape-ится 30 секунд | приложение недоступно |
| `TodoApi5xxErrors` | API возвращает 5xx не менее минуты | серверная ошибка API |
| `TodoTaskLockConflict` | был конфликт блокировки за последние 5 минут | запрос `done` требуется повторить |
| `TodoHeapUsageHigh` | heap больше 85% пять минут | риск `OutOfMemoryError` |
| `TodoHikariConnectionsPending` | есть ожидающие соединения минуту | пул или PostgreSQL перегружены |

После изменения правил перезапустите Prometheus и Alertmanager:

```bash
docker compose up -d alertmanager prometheus
```

Проверить правила и их текущее состояние:

- <http://127.0.0.1:9090/rules> — загруженные правила;
- <http://127.0.0.1:9090/alerts> — состояния `pending` и `firing`;
- <http://127.0.0.1:9093> — alerts, сгруппированные Alertmanager.

Для безопасной проверки `TodoTargetDown` временно остановите только приложение:

```bash
docker compose stop todo
```

Через 30 секунд alert перейдёт в `firing`. После проверки верните приложение:

```bash
docker compose start todo
```
## Блокировки задач

`POST /api/task/done` выполняется в транзакции. Перед изменением повторяющейся задачи приложение читает строку через `SELECT ... FOR UPDATE`.

Это не даёт двум одновременным запросам вычислить одну и ту же следующую дату. Если база данных отменяет ожидание блокировки или обнаруживает deadlock, API отвечает `409 Conflict` с сообщением:

```json
{"error":"задача временно занята, повторите запрос"}
```

Каждый такой ответ увеличивает метрику `todo_tasks_lock_conflicts_total`. Рост счётчика означает, что клиенту следует повторить запрос с небольшой задержкой.

## Нагрузочные тесты

Перед долгим тестом проверьте короткий сценарий k6:

```bash
docker compose --profile load run --rm -e SMOKE=1 loadtest
```

Основной k6-сценарий:

```bash
docker compose --profile load run --rm loadtest
```

Он обращается из отдельного контейнера к `http://todo:7540`. В результатах смотрите `http_req_failed`, `http_reqs`, `p95` и `p99`; одновременно наблюдайте Grafana.

JMeter-сценарии лежат в `loadtest/`. Например, `jmeter-tasks-crud-smoke-perf.jmx` запускает CRUD-проверку против тестового приложения на порту `7541`.

## PostgreSQL: диагностика запросов

В Compose включено расширение `pg_stat_statements`. Оно показывает агрегированную статистику SQL-запросов:

```bash
docker compose exec -T postgres psql -U todo -d todo -c "
SELECT query, calls, rows,
       round(total_exec_time::numeric, 3) AS total_ms,
       round(mean_exec_time::numeric, 3) AS avg_ms
FROM pg_stat_statements
WHERE query LIKE '%scheduler%'
ORDER BY total_exec_time DESC
LIMIT 10;"
```

Для исследования одного запроса используйте `EXPLAIN (ANALYZE, BUFFERS)`. `Execution Time` — фактическое время выполнения, а `Buffers` показывает работу PostgreSQL с кэшем и страницами таблиц.

```bash
docker compose exec -T postgres psql -U todo -d todo -c "
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, date, title, comment, repeat
FROM scheduler
ORDER BY date, id
LIMIT 50;"
```

## JFR и heap dump

В контейнере приложения доступен `jcmd`, а Java-процесс имеет PID `1`. Начать запись JFR перед нагрузкой:

```powershell
$recording = "load-$(Get-Date -Format yyyyMMdd-HHmmss)"
docker compose exec -T todo jcmd 1 JFR.start "name=$recording" settings=default disk=true
```

После теста остановить её и скопировать на компьютер:

```powershell
docker compose exec -T todo jcmd 1 JFR.stop "name=$recording" "filename=/diagnostics/$recording.jfr"
New-Item -ItemType Directory -Force diagnostics | Out-Null
docker compose cp "todo:/diagnostics/$recording.jfr" "./diagnostics/$recording.jfr"
```

Heap dump:

```powershell
$dump = "heap-$(Get-Date -Format yyyyMMdd-HHmmss).hprof"
docker compose exec -T todo jcmd 1 GC.heap_dump "/diagnostics/$dump"
docker compose cp "todo:/diagnostics/$dump" "./diagnostics/$dump"
```

JFR удобно открыть в JDK Mission Control, `.hprof` — в Eclipse MAT. Heap dump может кратко остановить приложение и содержать данные из памяти, поэтому не добавляйте его в Git.

## Сборка и тесты

Maven установлен в build stage Dockerfile, поэтому проверка выполняется без локальной установки Maven:

```bash
docker compose build todo
```

Команда запускает `mvn -B -q test package`: сначала JUnit-тесты, затем сборку JAR. В том числе тест проверяет, что завершение повторяющейся задачи использует блокирующее чтение `findForUpdate`.

## Работа с Git

Перед началом работы:

```bash
git status --short
```

После готового изменения:

```bash
git add <файлы>
git commit -m "Краткое описание изменения"
git push
```

Git хранит историю исходного кода и конфигурации. В Git не должны попадать `target/`, `diagnostics/`, Docker volumes и локальная папка Eclipse `workspace/`.
## CI gate

Изменения в `main` объединяются через Pull Request только после успешных `test` и `load-smoke`.
