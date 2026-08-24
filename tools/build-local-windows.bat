rem Локальная сборка на Windows (аналог compile.sh). Запуск из корня репозитория.
cd connector
mvn dependency:copy@get-lombok -pl bundles/com.github.malikov-pro.dt.bsl.lspconnector
set MAVEN_OPTS=-javaagent:%cd%\bundles\com.github.malikov-pro.dt.bsl.lspconnector\target\lombok.jar=ECJ
mvn verify -Dtycho.localArtifacts=ignore
