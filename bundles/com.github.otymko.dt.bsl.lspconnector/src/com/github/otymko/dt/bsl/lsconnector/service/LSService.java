package com.github.otymko.dt.bsl.lsconnector.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;
import com.github.otymko.dt.bsl.lsconnector.lsp.BSLConnector;
import com.github.otymko.dt.bsl.lsconnector.lsp.BSLLanguageClient;
import com.github.otymko.dt.bsl.lsconnector.lsp.WebSocketLspTransport;
import com.github.otymko.dt.bsl.lsconnector.ui.BSLPreferencePage;
import com.github.otymko.dt.bsl.lsconnector.util.LaunchMode;
import com.github.otymko.dt.bsl.lsconnector.util.LsCache;
import com.github.otymko.dt.bsl.lsconnector.util.LsVersionProbe;

public class LSService {
    private final BSLPlugin plugin;
    private final WindowsEventService windowsEventService;
    private final ScopedPreferenceStore preferenceStore;
    private Process process;
    private BSLConnector connector;
    private WebSocketLspTransport webSocketTransport;

    public BSLConnector getConnector() {
	return connector;
    }

    public LSService(BSLPlugin plugin) {
	this.plugin = plugin;
	windowsEventService = plugin.getWindowsEventService();
	preferenceStore = plugin.getPreferenceStore();
    }

    public void start() {
	if (getLaunchMode() == LaunchMode.WEBSOCKET) {
	    connectWebSocket();
	} else {
	    createProcess();
	    connectToProcess();
	}
	if (isLaunched()) {
	    windowsEventService.start();
	}
	plugin.getStatusService().refreshLocalVersion();
    }

    public void stop() {
	try {
	    if (connector != null) {
		connector.shutdown();
		connector.exit();
	    }
	} catch (Exception e) {
	    BSLPlugin.createWarningStatus("Остановка BSL LS: " + e.getMessage());
	}
	windowsEventService.stop();
	if (process != null && process.isAlive()) {
	    process.destroy();
	    try {
		if (!process.waitFor(2, TimeUnit.SECONDS)) {
		    process.destroyForcibly();
		}
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
		process.destroyForcibly();
	    }
	}
	if (webSocketTransport != null) {
	    webSocketTransport.close();
	}
	clear();
	plugin.getStatusService().fireChanged();
    }

    public void restart() {
	stop();
	start();
    }

    public LaunchMode getLaunchMode() {
	return LaunchMode.from(preferenceStore.getString(BSLPreferencePage.LAUNCH_MODE));
    }

    public boolean isLaunched() {
	if (getLaunchMode() == LaunchMode.WEBSOCKET) {
	    return webSocketTransport != null && webSocketTransport.isOpen();
	}
	return process != null && process.isAlive();
    }

    private void createProcess() {
	var pathToConfiguration = plugin.getPathToConfiguration();
	var pathToWorkspace = plugin.getPathToWorkspace();
	var mode = getLaunchMode();
	var pathToLSP = findCachedArtifact(mode);

	if (pathToLSP.isEmpty()) {
	    BSLPlugin.createWarningStatus(
		    "BSL Language Server не найден в ~/.bsl-connector-for-edt. Выберите релиз в настройках.");
	    return;
	}

	List<String> arguments = new ArrayList<>();
	if (mode == LaunchMode.JAR) {
	    arguments.add(javaCommand());
	    LsVersionProbe.addOpts(arguments, preferenceStore.getString(BSLPreferencePage.JAVA_OPTS));
	    arguments.add("-jar");
	}
	arguments.add(pathToLSP.get().toString());

	if (pathToConfiguration.isPresent()) {
	    arguments.add("--configuration");
	    arguments.add(pathToConfiguration.get().toString());
	}

	BSLPlugin.createWarningStatus(arguments.toString());

	try {
	    process = new ProcessBuilder()
		    .command(arguments)
		    .directory(pathToWorkspace.toFile())
		    .start();
	    plugin.sleepCurrentThread(500);
	    if (!process.isAlive()) {
		BSLPlugin.createWarningStatus("Не удалалось запустить процесс с BSL LS. Процесс был аварийно завершен.");
	    }
	} catch (IOException e) {
	    BSLPlugin.createErrorStatus("Не удалось запустить процесс BSL LS", e);
	}
    }

    private void connectToProcess() {
	if (process == null) {
	    return;
	}
	startConnector(process.getInputStream(), process.getOutputStream());
    }

    private void connectWebSocket() {
	var url = preferenceStore.getString(BSLPreferencePage.WEBSOCKET_URL);
	if (url == null || url.isBlank()) {
	    url = BSLPreferencePage.DEFAULT_WEBSOCKET_URL;
	}
	try {
	    webSocketTransport = WebSocketLspTransport.connect(URI.create(url));
	    startConnector(webSocketTransport.getInputStream(), webSocketTransport.getOutputStream());
	} catch (Exception e) {
	    BSLPlugin.createErrorStatus("Не удалось подключиться к BSL LS по WebSocket: " + url, e);
	    if (webSocketTransport != null) {
		webSocketTransport.close();
		webSocketTransport = null;
	    }
	}
    }

    private void startConnector(InputStream in, OutputStream out) {
	var client = new BSLLanguageClient();
	connector = new BSLConnector(client, in, out);
	connector.startInThread();
	plugin.sleepCurrentThread(2000);
	connector.initialize();
    }

    private void clear() {
	process = null;
	connector = null;
	webSocketTransport = null;
    }

    private Optional<Path> findCachedArtifact(LaunchMode mode) {
	return LsCache.findArtifact(plugin.getAppDir(), mode);
    }

    private String javaCommand() {
	var command = preferenceStore.getString(BSLPreferencePage.PATH_TO_JAVA);
	return command == null || command.isBlank() ? "java" : command;
    }
}
