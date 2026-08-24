rem Локальная сборка на Windows (аналог compile.sh). Запуск из корня репозитория.
cd connector
mvn verify -Dtycho.localArtifacts=ignore
