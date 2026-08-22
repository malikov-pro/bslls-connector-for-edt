FROM eclipse-temurin:21-jre

ARG BSL_LS_VERSION=1.0.5
ARG BSL_LS_URL=https://github.com/1c-syntax/bsl-language-server/releases/download/v${BSL_LS_VERSION}/bsl-language-server-${BSL_LS_VERSION}-exec.jar

WORKDIR /opt/bsl-ls
ADD ${BSL_LS_URL} /opt/bsl-ls/bsl-language-server-exec.jar

EXPOSE 8025
ENTRYPOINT ["java", "-jar", "/opt/bsl-ls/bsl-language-server-exec.jar"]
CMD ["websocket", "--server.port=8025"]
