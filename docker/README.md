# BSL Language Server в Docker

Плагин EDT **не** поднимает контейнер. Сначала запустите сервер здесь, затем в настройках коннектора укажите режим **WebSocket** и URL `ws://localhost:8025/lsp`.

Jar кладётся в образ на этапе `docker build` (не при старте EDT).

```bash
cd docker
docker compose up --build
```

Другая версия:

```bash
docker compose build --build-arg BSL_LS_VERSION=1.0.5
docker compose up
```

Либо свой URL на `*-exec.jar`:

```bash
docker compose build --build-arg BSL_LS_URL=https://example.com/bsl-language-server-1.0.5-exec.jar
```

Либо локальный файл: скопируйте `*-exec.jar` в `docker/bsl-language-server-exec.jar` и замените в `Dockerfile` строку `ADD ${BSL_LS_URL} …` на `COPY bsl-language-server-exec.jar /opt/bsl-ls/bsl-language-server-exec.jar`.
