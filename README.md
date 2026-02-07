# Коннектор BSLLS для 1С:EDT

Плагин включает проверки [BSL LS](https://github.com/1c-syntax/bsl-language-server) в среде разработки [1С:EDT](https://edt.1c.ru/).
Это добавляет `128+` [диагностик](https://1c-syntax.github.io/bsl-language-server/diagnostics/).

## Возможности

- [x] Проверки кода
- [ ] Быстрые исправления
- [ ] Произвольные ссылки

## Установка

1. Откройте `Справка` -> `Установить новое ПО`.
2. Введите ссылку:
```
https://otymko.github.io/bslls-connector-for-edt/update/bslls-connector-for-edt/latest/
```
3. Нажмите `Добавить`.
4. Установите флажок на `BSL LS connector for EDT`.
5. Убедитесь, что установлен фложок `Обращаться во время инсталяции ко всем сайтам обновления для поиска требуемого ПО`.
6. Нажмите `Далее` -> `Готово`.
7. Перезапустите 1С:EDT.

### Первый запуск

При первом запуске нужно загрузить BSL LS.
1. Откройте  `Окно` -> `Параметры`.
2. Перейдите на вкладку `Коннектор BSLLS`.
3. Убедитесь что запущено задание `Загрузка BSL LS`.

Загрузка выполняется в каталог `%USER_HOME%/.bsl-connector-for-edt/bsl-language-server`.

Для настройки проверки используется файл [.bsl-language-server.json](https://1c-syntax.github.io/bsl-language-server/features/ConfigurationFile/).

Шаблон файла `.bsl-language-server.json` можно взять [example/.bsl-language-server.json](/example/.bsl-language-server.json).

Конфигурационный файл должен содержать:
* Событие запуска анализа `computeTrigger` на `onSave`.
* Путь к метаданным проекта в свойстве `configurationRoot`.

### Просмотр списка найденных проблем

Проверки, выполняемые 1С:EDT и текущим плагином используют разные панели отображения ошибок. Панель 1С:EDT разработана отдельно, называется `Проблемы конфигурации`. Плагин использует типовую панель Eclipse `Проблемы`.

### Установка из архива

Аналогична установке по адресу.
При выполнении шага 2 нажмите `Архив`.

## Разработчикам

### Требования

* JDK 17+
* Maven 3.9+
* Доступ к репозиторию EDT (credentials в `bom/settings.xml`)
* Плагин lombok (https://projectlombok.org/setup/eclipse) — для работы в IDE

### Локальная сборка

> `tycho-compiler-plugin` не умеет обрабатывать аннотации `lombok` вне Eclipse IDE,
> поэтому `lombok.jar` подключается как `-javaagent` через `MAVEN_OPTS`.

#### Linux / macOS

```bash
# 1. Скачайте lombok
export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0"
mvn dependency:copy@get-lombok -pl bundles/com.github.otymko.dt.bsl.lspconnector

# 2. Соберите проект
export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -javaagent:$(pwd)/bundles/com.github.otymko.dt.bsl.lspconnector/target/lombok.jar=ECJ"
mvn verify -Dtycho.localArtifacts=ignore
```

#### Windows

```bat
rem 1. Скачайте lombok
set MAVEN_OPTS=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0
mvn dependency:copy@get-lombok -pl bundles/com.github.otymko.dt.bsl.lspconnector

rem 2. Соберите проект
set MAVEN_OPTS=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -javaagent:%cd%\bundles\com.github.otymko.dt.bsl.lspconnector\target\lombok.jar=ECJ
mvn verify -Dtycho.localArtifacts=ignore
```

Результат сборки — p2-репозиторий в `repositories/com.github.otymko.dt.bsl.lsconnector.repository/target/`.
