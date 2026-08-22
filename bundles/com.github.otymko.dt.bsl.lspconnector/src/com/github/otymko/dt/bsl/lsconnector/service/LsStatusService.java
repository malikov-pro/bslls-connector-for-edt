package com.github.otymko.dt.bsl.lsconnector.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.FrameworkUtil;

import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;
import com.github.otymko.dt.bsl.lsconnector.ui.BSLPreferencePage;
import com.github.otymko.dt.bsl.lsconnector.util.GitHubRelease;
import com.github.otymko.dt.bsl.lsconnector.util.GitHubReleases;
import com.github.otymko.dt.bsl.lsconnector.util.LaunchMode;
import com.github.otymko.dt.bsl.lsconnector.util.LsCache;
import com.github.otymko.dt.bsl.lsconnector.util.LsVersionProbe;
import com.github.otymko.dt.bsl.lsconnector.util.VersionCompare;

public class LsStatusService {
    private static final String CONNECTOR_FORK_API = "https://api.github.com/repos/malikov-pro/bslls-connector-for-edt/releases?per_page=5";
    private static final String CONNECTOR_UPSTREAM_API = "https://api.github.com/repos/otymko/bslls-connector-for-edt/releases?per_page=5";

    private final AtomicInteger busy = new AtomicInteger();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile String cachedLsVersion = "";
    private volatile UpdateCheckResult lastCheck;
    private volatile boolean checkingUpdates;

    public void addListener(Runnable listener) {
	listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
	listeners.remove(listener);
    }

    public void beginBusy() {
	busy.incrementAndGet();
	notifyListeners();
    }

    public void endBusy() {
	if (busy.updateAndGet(value -> value > 0 ? value - 1 : 0) == 0) {
	    notifyListeners();
	} else {
	    notifyListeners();
	}
    }

    public boolean isBusy() {
	return busy.get() > 0 || checkingUpdates;
    }

    public String getCachedLsVersion() {
	return cachedLsVersion;
    }

    public UpdateCheckResult getLastCheck() {
	return lastCheck;
    }

    public boolean isCheckingUpdates() {
	return checkingUpdates;
    }

    public LaunchMode getLaunchMode() {
	var plugin = BSLPlugin.getPlugin();
	if (plugin == null || plugin.getLsService() == null) {
	    return LaunchMode.JAR;
	}
	return plugin.getLsService().getLaunchMode();
    }

    public boolean isRunning() {
	var plugin = BSLPlugin.getPlugin();
	return plugin != null && plugin.isRunningLS();
    }

    public void fireChanged() {
	notifyListeners();
    }

    public void refreshLocalVersion() {
	var job = new Job("Версия BSL Language Server") {
	    @Override
	    protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
		cachedLsVersion = probeLocalVersion();
		notifyListeners();
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
    }

    public void checkUpdates() {
	if (checkingUpdates) {
	    return;
	}
	checkingUpdates = true;
	notifyListeners();
	var job = new Job("Проверка обновлений BSL LS") {
	    @Override
	    protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
		var result = new UpdateCheckResult();
		try {
		    fillLsUpdate(result);
		    fillConnectorUpdate(result);
		} catch (Exception e) {
		    result.setError(e.getMessage());
		} finally {
		    lastCheck = result;
		    checkingUpdates = false;
		    notifyListeners();
		}
		return Status.OK_STATUS;
	    }
	};
	job.setUser(true);
	job.schedule();
    }

    private void fillLsUpdate(UpdateCheckResult result) throws Exception {
	var mode = getLaunchMode();
	if (mode == LaunchMode.WEBSOCKET) {
	    return;
	}
	var current = cachedLsVersion;
	if (current == null || current.isBlank()) {
	    current = probeLocalVersion();
	    cachedLsVersion = current;
	}
	for (GitHubRelease release : GitHubReleases.fetchLatest()) {
	    if (!release.hasAsset(mode)) {
		continue;
	    }
	    if (current.isBlank() || VersionCompare.compare(release.getTag(), current) > 0) {
		result.setLsLatestTag(release.getTag());
	    }
	    break;
	}
    }

    private void fillConnectorUpdate(UpdateCheckResult result) {
	var current = currentPluginVersion();
	var latest = latestConnectorTag();
	if (latest != null && !latest.isBlank() && VersionCompare.compare(latest, current) > 0) {
	    result.setConnectorLatestTag(latest);
	}
    }

    private String latestConnectorTag() {
	for (var url : List.of(CONNECTOR_FORK_API, CONNECTOR_UPSTREAM_API)) {
	    try {
		var releases = GitHubReleases.fetchLatest(url);
		if (!releases.isEmpty()) {
		    return releases.get(0).getTag();
		}
	    } catch (Exception e) {
		// пробуем следующий репозиторий
	    }
	}
	return "";
    }

    private String currentPluginVersion() {
	var bundle = FrameworkUtil.getBundle(BSLPlugin.class);
	if (bundle == null) {
	    return "0.0.0";
	}
	var version = bundle.getVersion();
	return version.getMajor() + "." + version.getMinor() + "." + version.getMicro();
    }

    private String probeLocalVersion() {
	var plugin = BSLPlugin.getPlugin();
	if (plugin == null) {
	    return "";
	}
	var mode = getLaunchMode();
	if (mode == LaunchMode.WEBSOCKET) {
	    return "";
	}
	var artifact = LsCache.findArtifact(plugin.getAppDir(), mode);
	if (artifact.isEmpty()) {
	    return "";
	}
	try {
	    var store = plugin.getPreferenceStore();
	    return LsVersionProbe.languageServerVersion(artifact.get(), mode == LaunchMode.JAR,
		    store.getString(BSLPreferencePage.PATH_TO_JAVA), store.getString(BSLPreferencePage.JAVA_OPTS));
	} catch (Exception e) {
	    return "";
	}
    }

    private void notifyListeners() {
	for (var listener : listeners) {
	    listener.run();
	}
    }
}
