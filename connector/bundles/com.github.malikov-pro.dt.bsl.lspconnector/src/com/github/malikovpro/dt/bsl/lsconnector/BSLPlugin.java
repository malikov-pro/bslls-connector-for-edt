package com.github.malikovpro.dt.bsl.lsconnector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.BundleContext;

import com.github.malikovpro.dt.bsl.lsconnector.listener.WindowEventListener;
import com.github.malikovpro.dt.bsl.lsconnector.service.LSService;
import com.github.malikovpro.dt.bsl.lsconnector.service.LsStatusService;
import com.github.malikovpro.dt.bsl.lsconnector.service.WindowsEventService;
import com.github.malikovpro.dt.bsl.lsconnector.ui.BSLPreferencePage;
import com.github.malikovpro.dt.bsl.lsconnector.util.BSLCommon;
import com.github.malikovpro.dt.bsl.lsconnector.util.LaunchMode;
import com.github.malikovpro.dt.bsl.lsconnector.util.LsCache;

public class BSLPlugin extends Plugin {
    public static final String PLUGIN_ID = "com.github.malikovpro.dt.bsl.ls_connector";
    private Set<String> workbenchParts = Collections.synchronizedSet(new HashSet<>());
    private static BSLPlugin plugin;
    protected static BundleContext context;
    private WindowsEventService windowsEventService;
    private LSService lsService;
    private LsStatusService statusService;
    private Path appDir;
    private Path pathToWorkspace;
    private Optional<Path> pathToConfiguration;
    private ScopedPreferenceStore preferenceStore;

    public Set<String> getWorkbenchParts() {
	return workbenchParts;
    }

    public static BSLPlugin getPlugin() {
	return plugin;
    }

    public static BundleContext getContext() {
	return context;
    }

    public WindowsEventService getWindowsEventService() {
	return windowsEventService;
    }

    public LSService getLsService() {
	return lsService;
    }

    public LsStatusService getStatusService() {
	return statusService;
    }

    public Path getAppDir() {
	return appDir;
    }

    public Path getPathToWorkspace() {
	return pathToWorkspace;
    }

    public Optional<Path> getPathToConfiguration() {
	return pathToConfiguration;
    }

    public ScopedPreferenceStore getPreferenceStore() {
	return preferenceStore;
    }

    public static IStatus createErrorStatus(String message, Throwable throwable) {
	return new Status(IStatus.ERROR, PLUGIN_ID, 0, message, throwable);
    }

    public static IStatus createWarningStatus(final String message, Exception throwable) {
	return new Status(IStatus.WARNING, PLUGIN_ID, 0, message, throwable);
    }

    public static IStatus createWarningStatus(String message) {
	return new Status(IStatus.WARNING, PLUGIN_ID, 0, message, null);
    }

    /** Пишет статус в журнал ошибок Eclipse («Журнал ошибок» / .metadata/.log). */
    public static void log(IStatus status) {
	var instance = plugin;
	if (instance == null) {
	    return;
	}
	var eclipseLog = instance.getLog();
	if (eclipseLog != null) {
	    eclipseLog.log(status);
	}
    }

    public static void logError(String message, Throwable throwable) {
	log(createErrorStatus(message, throwable));
    }

    public static void logWarning(String message) {
	log(createWarningStatus(message));
    }

    public static void logWarning(String message, Exception throwable) {
	log(createWarningStatus(message, throwable));
    }

    public static boolean isDebugEnabled() {
	var instance = plugin;
	if (instance == null || instance.preferenceStore == null) {
	    return false;
	}
	return instance.preferenceStore.getBoolean(BSLPreferencePage.DEBUG);
    }

    /** Отладочное сообщение: в журнал попадает только при включённой настройке «Отладка». */
    public static void debug(String message) {
	if (!isDebugEnabled()) {
	    return;
	}
	log(new Status(IStatus.INFO, PLUGIN_ID, 0, message, null));
    }

    @Override
    public void start(BundleContext bundleContext) throws Exception {
	plugin = this;
	super.start(bundleContext);
	BSLPlugin.context = bundleContext;

	initialize();
	startServices();
	// LS стартует в фоне: активатор не должен задерживать загрузку EDT
	// (initialize теперь ждёт ответ ограниченно, но это всё равно не работа для старта бандла).
	var job = new Job("Запуск BSL LS") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		startLS();
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
	stopLS();
	plugin = null;

	if (PlatformUI.isWorkbenchRunning()) {
	    for (var window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
		WindowEventListener.removeListenerFromAllPages(window);
	    }
	}

	super.stop(bundleContext);
    }

    public void sleepCurrentThread(long value) {
	try {
	    Thread.sleep(value);
	} catch (Exception e) {
	    logWarning(e.getMessage());
	}
    }

    private void startLS() {
	lsService.start();
    }

    private void stopLS() {
	lsService.stop();
    }

    public void restartLS() {
	lsService.restartAsync();
    }

    public boolean isRunningLS() {
	return lsService.isLaunched();
    }

    private void initialize() {
	initAppDir();
	initPreferenceStore();
	prepareForStart();
    }

    private void startServices() {
	windowsEventService = new WindowsEventService();
	statusService = new LsStatusService();
	lsService = new LSService(this);
    }

    private void initPreferenceStore() {
	preferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID);
	preferenceStore.setDefault(BSLPreferencePage.LAUNCH_MODE, LaunchMode.JAR.getId());
	preferenceStore.setDefault(BSLPreferencePage.PATH_TO_JAVA, "java");
	preferenceStore.setDefault(BSLPreferencePage.JAVA_OPTS, "");
	preferenceStore.setDefault(BSLPreferencePage.DEBUG, false);
    }

    private void prepareForStart() {
	pathToWorkspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();

	try {
	    searchConfigurationFile();
	} catch (IOException e) {
	    logError(e.getMessage(), e);
	}
    }

    private void searchConfigurationFile() throws IOException {
	pathToConfiguration = BSLCommon.getConfigurationFileFromWorkspace(pathToWorkspace);
    }

    private void initAppDir() {
	appDir = Path.of(System.getProperty("user.home"), ".bsl-connector-for-edt");
	if (!appDir.toFile().exists()) {
	    appDir.toFile().mkdir();
	}
	LsCache.nativeDir(appDir).toFile().mkdir();
	LsCache.jarDir(appDir).toFile().mkdir();
    }
}
