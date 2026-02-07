cd ..
mvn dependency:copy@get-lombok -pl bundles/com.github.otymko.dt.bsl.lspconnector
set "MAVEN_OPTS=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -javaagent:%cd%\bundles\com.github.otymko.dt.bsl.lspconnector\target\lombok.jar=ECJ"
mvn clean verify -Dtycho.localArtifacts=ignore