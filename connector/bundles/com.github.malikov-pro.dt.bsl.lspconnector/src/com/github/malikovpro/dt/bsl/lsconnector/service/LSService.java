package com.github.malikovpro.dt.bsl.lsconnector.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.check.LsIssueCleaner;
import com.github.malikovpro.dt.bsl.lsconnector.lsp.BSLConnector;
import com.github.malikovpro.dt.bsl.lsconnector.lsp.BSLLanguageClient;
import com.github.malikovpro.dt.bsl.lsconnector.ui.BSLPreferencePage;
import com.github.malikovpro.dt.bsl.lsconnector.util.LaunchMode;
import com.github.malikovpro.dt.bsl.lsconnector.util.LsCache;
import com.github.malikovpro.dt.bsl.lsconnector.util.LsVersionProbe;

public class LSService {
    private final BSLPlugin plugin;
    private final WindowsEventService windowsEventService;
    private final ScopedPreferenceStore preferenceStore;
    private Process process;
    private BSLConnector connector;
    /** Защёлка неудачного старта: после отказа не поднимаем LS заново на каждом модуле. */
    private volatile boolean startupFailed;
    /** Замечания уже сняты в текущий «выключенный» период — очистка не повторяется на каждом модуле. */
    private volatile boolean clearedWhileDisabled;

    public BSLConnector getConnector() {
	return connector;
    }

    public LSService(BSLPlugin plugin) {
	this.plugin = plugin;
	windowsEventService = plugin.getWindowsEventService();
	preferenceStore = plugin.getPreferenceStore();
    }

    public synchronized boolean ensureStarted() {
	if (isLaunched()) {
	    return true;
	}
	// После неудачного старта попытка не повторяется на каждом модуле:
	// каждый повтор стоит таймаут initialize и строку в журнале. Повторить
	// можно явно — «Проверить»/«Применить» в настройках или перезапуск EDT.
	if (startupFailed) {
	    return false;
	}
	start();
	return isLaunched();
    }

    public synchronized void start() {
	if (isLaunched()) {
	    return;
	}
	// Плагин выключен в настройках: процесс не запускаем и отказ не фиксируем.
	// Уже опубликованные замечания снимаем — один раз за «выключенный» период
	// (ensureStarted() зовётся на каждый модуль, повторять очистку нельзя).
	if (!plugin.isEnabled()) {
	    if (!clearedWhileDisabled) {
		clearedWhileDisabled = true;
		LsIssueCleaner.clearAsync();
	    }
	    return;
	}
	createProcess();
	connectToProcess();
	if (isLaunched()) {
	    windowsEventService.start();
	} else {
	    // Запоминаем отказ, иначе каждая проверка модуля заново оплатит таймаут initialize.
	    startupFailed = true;
	}
	plugin.getStatusService().refreshLocalVersion();
    }

    public synchronized void stop() {
	try {
	    if (connector != null) {
		connector.shutdown();
		connector.exit();
	    }
	} catch (Exception e) {
	    BSLPlugin.logWarning("Остановка BSL LS: " + e.getMessage());
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
	clear();
	// Явный stop (настройки/рестарт) снимает защёлку отказа — следующая попытка разрешена,
	// а «выключенный» период начат заново: при выключении замечания сняты повторно.
	startupFailed = false;
	clearedWhileDisabled = false;
	plugin.getStatusService().fireChanged();
    }

    public void restart() {
	stop();
	start();
    }

    /** Перезапуск вне UI-потока: для вызова из диалогов и обработчиков интерфейса. */
    public synchronized void restartAsync() {
	var job = new Job("Перезапуск BSL LS") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		restart();
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
    }

    /** Сбрасывает защёлку неудачного старта: следующая проверка модуля снова попробует поднять LS. */
    public synchronized void resetStartupFailure() {
	startupFailed = false;
    }

    public LaunchMode getLaunchMode() {
	return LaunchMode.from(preferenceStore.getString(BSLPreferencePage.LAUNCH_MODE));
    }

    public boolean isLaunched() {
	return process != null && process.isAlive();
    }

    private void createProcess() {
	var pathToConfiguration = plugin.getPathToConfiguration();
	var pathToWorkspace = plugin.getPathToWorkspace();
	var mode = getLaunchMode();
	var pathToLSP = findCachedArtifact(mode);

	if (pathToLSP.isEmpty()) {
	    BSLPlugin.logWarning(
		    "BSL Language Server не найден в ~/.bsl-connector-for-edt. Выберите релиз в настройках.");
	    return;
	}

	if (pathToConfiguration.isPresent() && !pathToConfiguration.get().toFile().exists()) {
	    BSLPlugin.logWarning("Файл конфигурации BSL LS не найден: " + pathToConfiguration.get()
		    + ". Конфиг (.bsl-language-server.json) ищется автоматически в корне воркспейса"
		    + " — проверьте, что файл на месте.");
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

    // Командная строка запуска — только при включённой настройке «Отладка».
    BSLPlugin.debug("Команда запуска BSL LS: " + arguments);

	try {
	    var builder = new ProcessBuilder()
		    .command(arguments)
		    .directory(pathToWorkspace.toFile());
	    // stderr обязательно дренируем: переполненный пайп блокирует процесс LS.
	    // Заодно лог пригодится при диагностике зависаний.
	    var logDir = plugin.getAppDir().resolve("logs");
	    java.nio.file.Files.createDirectories(logDir);
	    builder.redirectError(logDir.resolve("ls-stderr-" + mode.getId() + ".log").toFile());
	    process = builder.start();
	    if (!process.waitFor(2, TimeUnit.SECONDS) && process.isAlive()) {
		// процесс жив и не вышел за 2 с — для LS это норма
		return;
	    }
	    if (!process.isAlive()) {
		BSLPlugin.logWarning("Не удалалось запустить процесс с BSL LS. Процесс был аварийно завершен."
			+ " Лог: " + logDir.resolve("ls-stderr-" + mode.getId() + ".log"));
	    }
	} catch (IOException e) {
	    BSLPlugin.logError("Не удалось запустить процесс BSL LS", e);
	} catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	}
    }

    private void connectToProcess() {
	if (process == null) {
	    return;
	}
	startConnector(process.getInputStream(), process.getOutputStream());
    }

    private void startConnector(InputStream in, OutputStream out) {
	var client = new BSLLanguageClient();
	connector = new BSLConnector(client, in, out);
	connector.startInThread();
	var future = connector.initialize();
	var timeoutSeconds = initTimeoutSeconds();
	try {
	    // Ждём ответ ограниченно: зависший LS не должен блокировать вызывающий поток навсегда.
	    future.get(timeoutSeconds, TimeUnit.SECONDS);
	} catch (java.util.concurrent.TimeoutException e) {
	    BSLPlugin.logWarning("BSL LS не ответил на initialize за " + timeoutSeconds + " с. Процесс остановлен."
		    + " Повторные попытки остановлены до «Проверить»/«Применить» в настройках или перезапуска EDT."
		    + " Крупной конфигурации времени старта не хватает — увеличьте таймаут initialize"
		    + " в настройках коннектора (Окно → Параметры → Коннектор BSL LS).");
	    stop();
	} catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	} catch (Exception e) {
	    BSLPlugin.logWarning("Ошибка инициализации BSL LS: " + e.getMessage());
	}
    }

    /** Таймаут initialize из настроек («Таймаут initialize, с»), по умолчанию 15 с. */
    private long initTimeoutSeconds() {
	try {
	    var parsed = Long.parseLong(preferenceStore
		    .getString(BSLPreferencePage.INIT_TIMEOUT_SECONDS).trim());
	    if (parsed > 0) {
		return parsed;
	    }
	} catch (NumberFormatException e) {
	    // В настройках не число — используем умолчание.
	}
	return BSLPreferencePage.DEFAULT_INIT_TIMEOUT_SECONDS;
    }

    private void clear() {
	process = null;
	connector = null;
    }

    private Optional<Path> findCachedArtifact(LaunchMode mode) {
	return LsCache.findArtifact(plugin.getAppDir(), mode);
    }

    private String javaCommand() {
	var command = preferenceStore.getString(BSLPreferencePage.PATH_TO_JAVA);
	return command == null || command.isBlank() ? "java" : command;
    }
}
