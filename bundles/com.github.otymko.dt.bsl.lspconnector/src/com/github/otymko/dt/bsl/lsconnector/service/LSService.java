package com.github.otymko.dt.bsl.lsconnector.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;
import com.github.otymko.dt.bsl.lsconnector.lsp.BSLConnector;
import com.github.otymko.dt.bsl.lsconnector.lsp.BSLLanguageClient;
import com.github.otymko.dt.bsl.lsconnector.ui.BSLPreferencePage;
import com.github.otymko.dt.bsl.lsconnector.util.BSLCommon;

public class LSService {
    private final BSLPlugin plugin;
    private final WindowsEventService windowsEventService;
    private final ScopedPreferenceStore preferenceStore;
    private Process process;
    private BSLConnector connector;

    public BSLConnector getConnector() {
	return connector;
    }
    
    public LSService(BSLPlugin plugin) {
	this.plugin = plugin;
	windowsEventService = plugin.getWindowsEventService();
	preferenceStore = plugin.getPreferenceStore();
    }
    
    public void start() {
	createProcess();
	connectToProcess();
	if (isLaunched()) {
	    windowsEventService.start();
	}
    }
    
    public void stop() {
	if (connector != null) {
	    connector.shutdown();
	    connector.exit();
	}
	windowsEventService.stop();
	clear();
    }
    
    public void restart() {
	stop();
	start();
    }
    
    public boolean isLaunched() {
	return process != null && process.isAlive();
    }

    private void createProcess() {
	var pathToConfiguration = plugin.getPathToConfiguration();
	var pathToWorkspace = plugin.getPathToWorkspace();
	var pathToLSP = getPathToBSLLS();

	if (!pathToLSP.toFile().isFile()) {
	    BSLPlugin.createWarningStatus("BSL Language Server не найден: " + pathToLSP);
	    return;
	}

	var launchAsJar = preferenceStore.getBoolean(BSLPreferencePage.EXTERNAL_JAR)
		|| pathToLSP.toString().endsWith(".jar");

	List<String> arguments = new ArrayList<>();
	if (launchAsJar) {
	    arguments.add(preferenceStore.getString(BSLPreferencePage.PATH_TO_JAVA));
	    var javaOpts = preferenceStore.getString(BSLPreferencePage.JAVA_OPTS);
	    if (javaOpts != null && !javaOpts.isBlank()) {
		for (var opt : javaOpts.trim().split("\\s+")) {
		    if (!opt.isEmpty()) {
			arguments.add(opt);
		    }
		}
	    }
	    arguments.add("-jar");
	}
	arguments.add(pathToLSP.toString());

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
	var client = new BSLLanguageClient();
	connector = new BSLConnector(client, process.getInputStream(), process.getOutputStream());
	connector.startInThread();
	plugin.sleepCurrentThread(2000); // FIXME: Сколько нужно ждать?
	connector.initialize();
    }
    
    private void clear() {
	process = null; 
	connector = null;
    }
    
    private Path getPathToBSLLS() {
	var stored = preferenceStore.getString(BSLPreferencePage.PATH_TO_BSLLS);
	if (stored != null && !stored.isBlank()) {
	    var path = Path.of(stored);
	    if (path.toFile().isFile()) {
		return path;
	    }
	}
	return BSLCommon.getBundledLanguageServerJar().orElse(Path.of(stored == null ? "" : stored));
    }
}
