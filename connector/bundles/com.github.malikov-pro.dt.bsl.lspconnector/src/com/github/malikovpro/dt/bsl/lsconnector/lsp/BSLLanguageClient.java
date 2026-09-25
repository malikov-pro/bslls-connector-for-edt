package com.github.malikovpro.dt.bsl.lsconnector.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

public class BSLLanguageClient implements LanguageClient {

    @Override
    public void telemetryEvent(Object object) {
	// none
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
	// none
    }

    @Override
    public void showMessage(MessageParams messageParams) {
	// none
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
	// Headless-клиент: отвечаем сразу, чтобы сервер не ждал пользователя без таймаума.
	// null внутри future = «никакое действие не выбрано»; при sendErrors=ask это
	// трактуется как отказ от отправки и разблокирует поток диагностики (issue #14).
	return CompletableFuture.completedFuture(new MessageActionItem());
    }

    @Override
    public void logMessage(MessageParams message) {
	// none
    }

}
