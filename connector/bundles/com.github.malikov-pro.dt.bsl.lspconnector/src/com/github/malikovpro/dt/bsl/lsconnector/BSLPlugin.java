package com.github.malikovpro.dt.bsl.lsconnector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
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

    @Override
    public void start(BundleContext bundleContext) throws Exception {
	plugin = this;
	super.start(bundleContext);
	BSLPlugin.context = bundleContext;

	initialize();
	startServices();
	startLS();
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
	    createWarningStatus(e.getMessage());
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
	preferenceStore.setDefault(BSLPreferencePage.WEBSOCKET_URL, BSLPreferencePage.DEFAULT_WEBSOCKET_URL);
    }

    private void prepareForStart() {
	pathToWorkspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();

	try {
	    searchConfigurationFile();
	} catch (IOException e) {
	    createErrorStatus(e.getMessage(), e);
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
