package com.github.malikovpro.dt.bsl.lsconnector.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;
import com.github.malikovpro.dt.bsl.lsconnector.util.GitHubRelease;
import com.github.malikovpro.dt.bsl.lsconnector.util.GitHubReleases;
import com.github.malikovpro.dt.bsl.lsconnector.util.LaunchMode;
import com.github.malikovpro.dt.bsl.lsconnector.util.LsCache;
import com.github.malikovpro.dt.bsl.lsconnector.util.LsInstaller;
import com.github.malikovpro.dt.bsl.lsconnector.util.LsVersionProbe;

public class BSLPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    public static final String LAUNCH_MODE = "LAUNCH_MODE";
    public static final String PATH_TO_JAVA = "PATH_TO_JAVA";
    public static final String JAVA_OPTS = "JAVA_OPTS";
    public static final String WEBSOCKET_URL = "WEBSOCKET_URL";
    public static final String DEFAULT_WEBSOCKET_URL = "ws://localhost:8025/lsp";

    private Button nativeRadio;
    private Button jarRadio;
    private Button websocketRadio;
    private Composite jarComposite;
    private Composite websocketComposite;
    private Composite cacheComposite;
    private Text javaCommandText;
    private Text javaOptsText;
    private Text websocketUrlText;
    private Label javaStateLabel;
    private Label javaHintLabel;
    private Label statusLabel;
    private Composite releaseRow;
    private Combo releaseCombo;
    private Button downloadButton;
    private Button replaceButton;
    private Button checkButton;
    private Job listJob;
    private Job downloadJob;
    private Job javaProbeJob;
    private Job lsVersionJob;
    private List<GitHubRelease> releases = new ArrayList<>();
    private boolean replaceRequested;

    public BSLPreferencePage() {
	setPreferenceStore(BSLPlugin.getPlugin().getPreferenceStore());
    }

    @Override
    public void init(IWorkbench workbench) {
	// none
    }

    @Override
    protected Control createContents(Composite parent) {
	var root = new Composite(parent, SWT.NONE);
	root.setLayout(new GridLayout(1, false));
	root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

	var modeGroup = new Group(root, SWT.NONE);
	modeGroup.setText("Режим запуска");
	modeGroup.setLayout(new GridLayout(3, false));
	modeGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	nativeRadio = new Button(modeGroup, SWT.RADIO);
	nativeRadio.setText("Нативный");
	jarRadio = new Button(modeGroup, SWT.RADIO);
	jarRadio.setText("JAR");
	websocketRadio = new Button(modeGroup, SWT.RADIO);
	websocketRadio.setText("WebSocket");

	var modeListener = new SelectionAdapter() {
	    @Override
	    public void widgetSelected(SelectionEvent e) {
		if (!((Button) e.widget).getSelection()) {
		    return;
		}
		replaceRequested = false;
		updateVisibility();
		refreshStatus();
	    }
	};
	nativeRadio.addSelectionListener(modeListener);
	jarRadio.addSelectionListener(modeListener);
	websocketRadio.addSelectionListener(modeListener);

	jarComposite = new Composite(root, SWT.NONE);
	jarComposite.setLayout(new GridLayout(3, false));
	jarComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	javaStateLabel = new Label(jarComposite, SWT.NONE);
	javaStateLabel.setText("●");
	javaStateLabel.setForeground(jarComposite.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
	createLabel(jarComposite, "Команда Java:");
	javaCommandText = createText(jarComposite);

	javaHintLabel = new Label(jarComposite, SWT.WRAP);
	javaHintLabel.setText("Команда java для запуска BSL LS 1.x (нужна Java 21+; JVM EDT 17 не подходит).");
	var hintData = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
	hintData.widthHint = 420;
	javaHintLabel.setLayoutData(hintData);

	new Label(jarComposite, SWT.NONE);
	createLabel(jarComposite, "Java Opts:");
	javaOptsText = createText(jarComposite);

	websocketComposite = new Composite(root, SWT.NONE);
	websocketComposite.setLayout(new GridLayout(2, false));
	websocketComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	createLabel(websocketComposite, "URL:");
	websocketUrlText = createText(websocketComposite);

	cacheComposite = new Composite(root, SWT.NONE);
	cacheComposite.setLayout(new GridLayout(1, false));
	cacheComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	statusLabel = new Label(cacheComposite, SWT.WRAP);
	var statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);
	statusData.widthHint = 420;
	statusLabel.setLayoutData(statusData);

	releaseRow = new Composite(cacheComposite, SWT.NONE);
	releaseRow.setLayout(new GridLayout(3, false));
	releaseRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	releaseCombo = new Combo(releaseRow, SWT.READ_ONLY);
	releaseCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	downloadButton = new Button(releaseRow, SWT.PUSH);
	downloadButton.setText("Скачать");
	downloadButton.addSelectionListener(new SelectionAdapter() {
	    @Override
	    public void widgetSelected(SelectionEvent e) {
		startDownload();
	    }
	});

	replaceButton = new Button(releaseRow, SWT.PUSH);
	replaceButton.setText("Заменить");
	replaceButton.addSelectionListener(new SelectionAdapter() {
	    @Override
	    public void widgetSelected(SelectionEvent e) {
		replaceRequested = true;
		refreshStatus();
	    }
	});

	checkButton = new Button(root, SWT.PUSH);
	checkButton.setText("Проверить");
	checkButton.addSelectionListener(new SelectionAdapter() {
	    @Override
	    public void widgetSelected(SelectionEvent e) {
		replaceRequested = false;
		refreshStatus();
	    }
	});

	loadValues();
	updateVisibility();
	refreshStatus();
	return root;
    }

    @Override
    public boolean performOk() {
	if (!savePreferences()) {
	    return false;
	}
	BSLPlugin.getPlugin().restartLS();
	return true;
    }

    @Override
    protected void performApply() {
	if (savePreferences()) {
	    BSLPlugin.getPlugin().restartLS();
	}
    }

    @Override
    protected void performDefaults() {
	nativeRadio.setSelection(false);
	jarRadio.setSelection(true);
	websocketRadio.setSelection(false);
	javaCommandText.setText("java");
	javaOptsText.setText("");
	websocketUrlText.setText(DEFAULT_WEBSOCKET_URL);
	replaceRequested = false;
	updateVisibility();
	refreshStatus();
	super.performDefaults();
    }

    @Override
    public void dispose() {
	if (listJob != null) {
	    listJob.cancel();
	}
	if (downloadJob != null) {
	    downloadJob.cancel();
	}
	if (javaProbeJob != null) {
	    javaProbeJob.cancel();
	}
	if (lsVersionJob != null) {
	    lsVersionJob.cancel();
	}
	super.dispose();
    }

    private void loadValues() {
	var store = getPreferenceStore();
	var mode = LaunchMode.from(store.getString(LAUNCH_MODE));
	nativeRadio.setSelection(mode == LaunchMode.NATIVE);
	jarRadio.setSelection(mode == LaunchMode.JAR);
	websocketRadio.setSelection(mode == LaunchMode.WEBSOCKET);
	javaCommandText.setText(store.getString(PATH_TO_JAVA));
	javaOptsText.setText(store.getString(JAVA_OPTS));
	var url = store.getString(WEBSOCKET_URL);
	websocketUrlText.setText(url == null || url.isBlank() ? DEFAULT_WEBSOCKET_URL : url);
    }

    private boolean savePreferences() {
	var store = getPreferenceStore();
	store.setValue(LAUNCH_MODE, selectedMode().getId());
	store.setValue(PATH_TO_JAVA, javaCommandText.getText().trim());
	store.setValue(JAVA_OPTS, javaOptsText.getText().trim());
	var url = websocketUrlText.getText().trim();
	store.setValue(WEBSOCKET_URL, url.isEmpty() ? DEFAULT_WEBSOCKET_URL : url);
	return true;
    }

    private LaunchMode selectedMode() {
	if (nativeRadio.getSelection()) {
	    return LaunchMode.NATIVE;
	}
	if (websocketRadio.getSelection()) {
	    return LaunchMode.WEBSOCKET;
	}
	return LaunchMode.JAR;
    }

    private void updateVisibility() {
	var mode = selectedMode();
	setVisible(jarComposite, mode == LaunchMode.JAR);
	setVisible(websocketComposite, mode == LaunchMode.WEBSOCKET);
	setVisible(cacheComposite, mode != LaunchMode.WEBSOCKET);
	var parent = jarComposite.getParent();
	parent.layout(true, true);
    }

	private void refreshStatus() {
	if (statusLabel == null || statusLabel.isDisposed()) {
	    return;
	}
	var mode = selectedMode();
	if (mode == LaunchMode.WEBSOCKET) {
	    statusLabel.setText("Локальный процесс и кэш не используются. Контейнер поднимается отдельно.");
	    setReleaseControlsVisible(false, false);
	    return;
	}

	if (mode == LaunchMode.JAR) {
	    probeJavaAsync();
	}

	var artifact = LsCache.findArtifact(BSLPlugin.getPlugin().getAppDir(), mode);
	if (artifact.isPresent() && !replaceRequested) {
	    setReleaseControlsVisible(false, true);
	    probeLsVersionAsync(artifact.get(), mode);
	    return;
	}

	setReleaseControlsVisible(true, artifact.isPresent());
	if (releases.isEmpty()) {
	    statusLabel.setText("BSL Language Server не найден. Загрузка списка релизов…");
	    startReleaseList();
	} else {
	    fillReleaseCombo();
	    statusLabel.setText("BSL Language Server не найден. Выберите релиз:");
	}
    }

    private void probeJavaAsync() {
	if (javaProbeJob != null) {
	    javaProbeJob.cancel();
	}
	var display = jarComposite.getDisplay();
	var command = javaCommandText.getText().trim();
	setJavaState(display, SWT.COLOR_DARK_GRAY, "Проверяю Java…");
	var job = new Job("Проверка команды Java") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		String message;
		int colorId;
		try {
		    var output = LsVersionProbe.javaVersionOutput(command);
		    var major = LsVersionProbe.parseJavaMajor(output);
		    var summary = output.isEmpty() ? "вывод java -version пуст" : LsVersionProbe.firstLine(output);
		    if (major.isPresent() && major.getAsInt() >= LsVersionProbe.REQUIRED_JAVA_MAJOR) {
			colorId = SWT.COLOR_DARK_GREEN;
			message = "Java " + summary + " — подходит для BSL LS 1.x.";
		    } else if (major.isPresent()) {
			colorId = SWT.COLOR_DARK_RED;
			message = "Java " + major.getAsInt() + " — не подходит: для BSL LS 1.x нужна Java "
				+ LsVersionProbe.REQUIRED_JAVA_MAJOR + "+.";
		    } else {
			colorId = SWT.COLOR_DARK_RED;
			message = "Не удалось определить версию («" + summary + "»). Для BSL LS 1.x нужна Java "
				+ LsVersionProbe.REQUIRED_JAVA_MAJOR + "+.";
		    }
		} catch (Exception e) {
		    colorId = SWT.COLOR_DARK_RED;
		    message = "Не удалось выполнить «" + command + "»: " + e.getMessage()
			    + ". Проверьте путь к java.";
		}
		final int stateColor = colorId;
		final String stateMessage = message;
		display.asyncExec(() -> {
		    if (!javaStateLabel.isDisposed()) {
			setJavaState(display, stateColor, stateMessage);
		    }
		});
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
	javaProbeJob = job;
    }

    private void probeLsVersionAsync(Path artifact, LaunchMode mode) {
	if (lsVersionJob != null) {
	    lsVersionJob.cancel();
	}
	var display = statusLabel.getDisplay();
	var command = javaCommandText.getText().trim();
	var opts = javaOptsText.getText();
	var jar = mode == LaunchMode.JAR;
	statusLabel.setText("Проверяю версию BSL LS…");
	var job = new Job("Проверка версии BSL LS") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		String text;
		try {
		    var version = LsVersionProbe.languageServerVersion(artifact, jar, command, opts);
		    text = "Версия LS: " + (version.isEmpty() ? artifact.getFileName() : version);
		} catch (Exception e) {
		    text = "Дистрибутив найден, но не удалось выполнить version: " + e.getMessage();
		}
		final String statusText = text;
		display.asyncExec(() -> {
		    if (!statusLabel.isDisposed()) {
			statusLabel.setText(statusText);
		    }
		});
		return Status.OK_STATUS;
	    }
	};
	job.setSystem(true);
	job.schedule();
	lsVersionJob = job;
    }

    private void setJavaState(org.eclipse.swt.widgets.Display display, int colorId, String message) {
	javaStateLabel.setForeground(display.getSystemColor(colorId));
	javaHintLabel.setForeground(display.getSystemColor(colorId));
	javaHintLabel.setText(message);
    }

    private void startReleaseList() {
	if (listJob != null) {
	    listJob.cancel();
	}
	listJob = new Job("Список релизов BSL Language Server") {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		try {
		    var loaded = GitHubReleases.fetchLatest();
		    asyncUi(() -> {
			releases = loaded;
			if (loaded.isEmpty()) {
			    statusLabel.setText("GitHub не вернул релизы.");
			    return;
			}
			fillReleaseCombo();
			statusLabel.setText("BSL Language Server не найден. Выберите релиз:");
		    });
		    return Status.OK_STATUS;
		} catch (Exception e) {
		    asyncUi(() -> statusLabel.setText("Не удалось получить релизы GitHub: " + e.getMessage()));
		    return BSLPlugin.createErrorStatus(e.getMessage(), e);
		}
	    }
	};
	listJob.setUser(false);
	listJob.schedule();
    }

    private void startDownload() {
	var index = releaseCombo.getSelectionIndex();
	if (index < 0 || index >= releases.size()) {
	    statusLabel.setText("Выберите релиз.");
	    return;
	}
	var release = releases.get(index);
	var mode = selectedMode();
	if (!release.hasAsset(mode)) {
	    statusLabel.setText("В релизе " + release.getTag() + " нет файла " + release.assetFileName(mode));
	    return;
	}
	if (downloadJob != null) {
	    downloadJob.cancel();
	}
	downloadButton.setEnabled(false);
	statusLabel.setText("Загрузка " + release.getTag() + "…");
	BSLPlugin.getPlugin().getStatusService().beginBusy();
	downloadJob = new Job("Загрузка BSL Language Server " + release.getTag()) {
	    @Override
	    protected IStatus run(IProgressMonitor monitor) {
		try {
		    LsInstaller.install(BSLPlugin.getPlugin().getAppDir(), mode, release, monitor);
		    BSLPlugin.getPlugin().getStatusService().refreshLocalVersion();
		    asyncUi(() -> {
			replaceRequested = false;
			downloadButton.setEnabled(true);
			refreshStatus();
		    });
		    return Status.OK_STATUS;
		} catch (org.eclipse.core.runtime.OperationCanceledException e) {
		    asyncUi(() -> {
			downloadButton.setEnabled(true);
			statusLabel.setText("Загрузка отменена.");
		    });
		    return Status.CANCEL_STATUS;
		} catch (Exception e) {
		    asyncUi(() -> {
			downloadButton.setEnabled(true);
			statusLabel.setText("Ошибка загрузки: " + e.getMessage());
		    });
		    return BSLPlugin.createErrorStatus(e.getMessage(), e);
		} finally {
		    BSLPlugin.getPlugin().getStatusService().endBusy();
		}
	    }
	};
	downloadJob.setUser(true);
	downloadJob.schedule();
    }

    private void fillReleaseCombo() {
	var previous = releaseCombo.getText();
	releaseCombo.removeAll();
	for (var release : releases) {
	    releaseCombo.add(release.getTag());
	}
	var select = 0;
	for (var i = 0; i < releases.size(); i++) {
	    if (releases.get(i).getTag().equals(previous)) {
		select = i;
		break;
	    }
	}
	if (!releases.isEmpty()) {
	    releaseCombo.select(select);
	}
    }

    private void setReleaseControlsVisible(boolean showList, boolean showReplace) {
	setVisible(releaseRow, showList || showReplace);
	setVisible(releaseCombo, showList);
	setVisible(downloadButton, showList);
	setVisible(replaceButton, showReplace);
	if (cacheComposite != null && !cacheComposite.isDisposed()) {
	    cacheComposite.layout(true, true);
	}
    }

    private void asyncUi(Runnable runnable) {
	var display = Display.getDefault();
	if (display == null || display.isDisposed()) {
	    return;
	}
	display.asyncExec(() -> {
	    if (statusLabel == null || statusLabel.isDisposed()) {
		return;
	    }
	    runnable.run();
	});
    }

    private static void setVisible(Control control, boolean visible) {
	if (control == null || control.isDisposed()) {
	    return;
	}
	control.setVisible(visible);
	var data = control.getLayoutData();
	if (data instanceof GridData) {
	    ((GridData) data).exclude = !visible;
	} else {
	    var gridData = new GridData();
	    gridData.exclude = !visible;
	    control.setLayoutData(gridData);
	}
    }

    private static void createLabel(Composite parent, String text) {
	new Label(parent, SWT.NONE).setText(text);
    }

    private static Text createText(Composite parent) {
	var text = new Text(parent, SWT.BORDER);
	text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	return text;
    }

}
