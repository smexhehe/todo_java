# Todo Scheduler

Планировщик задач на Java 21, Spring Boot и PostgreSQL. Существующий интерфейс из `web/` включается в JAR при сборке. Сервер сохраняет прежние адреса и JSON-формат API.

## Запуск через Docker Compose

```bash
docker compose up -d --build
```

Compose запускает приложение, PostgreSQL, Prometheus и Grafana. Данные PostgreSQL, Prometheus, Grafana и диагностические файлы JVM сохраняются в отдельных именованных томах.

| Адрес | Сервис |
| --- | --- |
| <http://localhost:7540> | Планировщик задач |
| <http://localhost:7540/actuator/prometheus> | Метрики приложения |
| <http://localhost:9090/targets> | Состояние сбора метрик Prometheus |
| <http://localhost:3000/d/todo-overview> | Панель Grafana |

В Grafana войдите с логином `admin` и паролем `admin`; пароль можно задать через `GRAFANA_ADMIN_PASSWORD` в `.env`. Источник Prometheus и панель `Todo Scheduler: JVM and HTTP` создаются автоматически. Порты публикуются только на `127.0.0.1`.

Для включения входа задайте `TODO_PASSWORD` в файле `.env` рядом с `docker-compose.yml`:

```dotenv
TODO_PASSWORD=change-me
POSTGRES_PASSWORD=change-db-password
GRAFANA_ADMIN_PASSWORD=change-grafana-password
```

После запуска откройте <http://localhost:7540/login.html>. Если `TODO_PASSWORD` пуст, проверка токена отключена. При смене пароля ранее выданные токены перестанут работать.

## Проверка метрик

Откройте `/actuator/prometheus` и убедитесь, что там есть `jvm_memory_used_bytes`, `http_server_requests_seconds_count` и `hikaricp_connections_active`. Метрики HTTP появятся после запросов к API; для проверки можно запустить `powershell -ExecutionPolicy Bypass -File scripts\smoke.ps1`. На странице Prometheus `/targets` цель `todo` должна иметь состояние `UP`.

Панель Grafana показывает частоту запросов, задержки p95/p99, ответы 5xx, память JVM, паузы GC, CPU и подключения Hikari. Данные p95/p99 появятся после нескольких минут запросов: для них используются пятиминутные окна. Prometheus опрашивает приложение каждые 5 секунд и хранит метрики 7 дней. Для остановки используйте `docker compose down`; именованные тома при этом сохранятся.

## JFR и heap dump во время нагрузки

Команды ниже выполняются в PowerShell из папки Java-проекта после `docker compose up -d --build`. Контейнер приложения содержит JDK 21 и `jcmd`; Java-процесс в нём имеет PID `1`. Диагностические файлы сначала попадают в именованный том `/diagnostics`, затем копируются в локальную папку `diagnostics/` (она исключена из Git).

Начните запись JFR непосредственно перед нагрузочным тестом. `settings=default` подходит для записи на протяжении всего теста:

```powershell
$recording = "load-$(Get-Date -Format yyyyMMdd-HHmmss)"
docker compose exec -T todo jcmd 1 JFR.start "name=$recording" settings=default disk=true maxsize=0
```

После окончания нагрузки остановите запись и скопируйте её на компьютер в том же PowerShell-терминале:

```powershell
docker compose exec -T todo jcmd 1 JFR.stop "name=$recording" "filename=/diagnostics/$recording.jfr"
New-Item -ItemType Directory -Force diagnostics | Out-Null
docker compose cp "todo:/diagnostics/$recording.jfr" "./diagnostics/$recording.jfr"
```

`maxsize=0` отключает ограничение объёма записи: без явного параметра JVM в этом образе ограничивает запись 250 МБ. Следите за свободным местом на диске. Для более подробного профиля на коротком участке используйте `settings=profile`; такая запись сильнее влияет на результаты нагрузки.

Для снимка heap в нужный момент выполните:

```powershell
$dump = "heap-$(Get-Date -Format yyyyMMdd-HHmmss).hprof"
docker compose exec -T todo jcmd 1 GC.heap_dump "/diagnostics/$dump"
docker compose cp "todo:/diagnostics/$dump" "./diagnostics/$dump"
```

`GC.heap_dump` обычно запускает полный GC и может заметно приостановить приложение; отметьте этот момент при анализе задержек. При `OutOfMemoryError` JVM также попробует создать дамп в `/diagnostics` благодаря `JAVA_TOOL_OPTIONS` в Compose. Список файлов можно посмотреть командой `docker compose exec -T todo ls -lh /diagnostics`. JFR удобно открыть в JDK Mission Control, а `.hprof` — в анализаторе heap, например Eclipse MAT. Дамп может содержать данные приложения и занимать сотни мегабайт.

## Первый нагрузочный тест

Сценарий `loadtest/read-only.js` проверяет чтение задач из PostgreSQL и вычисление следующей даты. Он не изменяет данные. k6 запускается в отдельном контейнере той же сети Compose и обращается к `http://todo:7540`. Если в `.env` задан `TODO_PASSWORD`, сценарий получает токен через `/api/signin` и передаёт его в cookie.

После `docker compose up -d --build` сначала выполните короткую проверку сценария:

```powershell
docker compose --profile load run --rm -e SMOKE=1 loadtest
```

Если проверка прошла, начните JFR по инструкции выше. Затем в другом PowerShell-терминале из той же папки запустите пятиминутную нагрузку:

```powershell
docker compose --profile load run --rm loadtest
```

Нагрузка плавно растёт от 5 до 20 виртуальных пользователей. После её завершения остановите JFR в первом терминале и скопируйте файл. В выводе k6 посмотрите долю ошибок, число запросов в секунду и p95/p99 времени ответа; в Grafana — HTTP, heap, GC, CPU и подключения к PostgreSQL. Если ошибок нет, результаты этого прогона можно сохранить как исходную точку для следующих тестов. Ограничения по времени ответа здесь нет: его нужно выбрать после первого измерения.

## Запуск без Docker

Нужны Java 21, Maven 3.6.3 или новее и работающий PostgreSQL. Создайте пользователя и базу `todo`, затем задайте параметры подключения и запустите сервер:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/todo
export SPRING_DATASOURCE_USERNAME=todo
export SPRING_DATASOURCE_PASSWORD=todo
mvn spring-boot:run
```

Порт по умолчанию `7540`; его можно изменить через `TODO_PORT`. Схема создаётся скриптом `src/main/resources/schema.sql`. Проверка сборки: `mvn verify`. Для проверки запущенного сервера в PowerShell выполните `powershell -ExecutionPolicy Bypass -File scripts\smoke.ps1`.

## API

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/api/nextdate?now=20240126&date=20240113&repeat=d%207` | Следующая дата для правила повторения |
| `POST` | `/api/task` | Создать задачу |
| `GET` | `/api/tasks?search=текст` | Получить до 50 задач |
| `GET` | `/api/task?id=1` | Получить задачу |
| `PUT` | `/api/task` | Изменить задачу |
| `DELETE` | `/api/task?id=1` | Удалить задачу |
| `POST` | `/api/task/done?id=1` | Завершить задачу или перенести повторяющуюся |
| `POST` | `/api/signin` | Получить токен по паролю |

Пример тела задачи:

```json
{"date":"20260924","title":"Сделать задачу","comment":"Описание","repeat":"d 7"}
```

`date` записывается в формате `yyyyMMdd`; пустая дата означает сегодня. Правила `repeat`: `d N` (каждые N дней, 1–400), `y` (ежегодно), `w 1,3,5` (дни недели от понедельника до воскресенья), `m 1,15,-1` (дни месяца, где `-1` означает последний день). У месячного правила можно указать месяцы: `m 10,17 1,8,12`. Поле `id` в полученной задаче является строкой, как в Go-версии.

Если включён пароль, запросы к задачам должны передавать cookie `token`, полученный через `/api/signin`. Веб-интерфейс делает это автоматически. `/api/nextdate` остаётся доступным без входа.

## Старые данные SQLite

Файлы `scheduler.db` и `scheduler.backup.db` остались в исходной папке Go-проекта. Новая PostgreSQL-база начинается пустой. Если нужно перенести существующие задачи, это следует сделать отдельным шагом после проверки содержимого резервной копии.
