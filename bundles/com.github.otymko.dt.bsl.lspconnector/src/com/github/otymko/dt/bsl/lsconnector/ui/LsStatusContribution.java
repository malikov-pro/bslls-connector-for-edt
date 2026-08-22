package com.github.otymko.dt.bsl.lsconnector.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.menus.WorkbenchWindowControlContribution;

import com.github.otymko.dt.bsl.lsconnector.BSLPlugin;
import com.github.otymko.dt.bsl.lsconnector.service.LsStatusService;
import com.github.otymko.dt.bsl.lsconnector.util.LaunchMode;

public class LsStatusContribution extends WorkbenchWindowControlContribution {
    private static final int CANVAS_WIDTH_HINT = 160;
    private static final int TEXT_X = 18;
    private static final int CIRCLE_SIZE = 12;
    private static final int REFRESH_MS = 500;

    private Composite container;
    private Canvas canvas;
    private Font font;
    private Image greenImage;
    private Image greyImage;
    private Image yellowImage;
    private Image currentImage;
    private Menu popupMenu;
    private MenuItem updateItem;
    private String statusText = "BSL LS";
    private boolean blinkOn = true;
    private boolean disposed;
    private Runnable modelListener;

    @Override
    protected Control createControl(Composite parent) {
	container = new Composite(parent, SWT.NONE);
	var layout = new GridLayout(1, false);
	layout.marginWidth = 2;
	layout.marginHeight = 0;
	container.setLayout(layout);

	createCircles(parent.getDisplay());
	currentImage = greyImage;

	canvas = new Canvas(container, SWT.NONE);
	var canvasData = new GridData(SWT.FILL, SWT.FILL, true, true);
	canvasData.widthHint = CANVAS_WIDTH_HINT;
	canvas.setLayoutData(canvasData);

	var fontData = canvas.getFont().getFontData()[0];
	fontData.setHeight(Math.max(8, (int) (fontData.getHeight() * 0.9)));
	font = new Font(canvas.getDisplay(), new FontData[] { fontData });
	canvas.setFont(font);
	canvasData.heightHint = measureHeight();
	canvas.addPaintListener(this::paint);

	createMenu();
	canvas.addMouseListener(new MouseAdapter() {
	    @Override
	    public void mouseUp(MouseEvent e) {
		if (e.button == 1 && popupMenu != null) {
		    refreshMenu();
		    popupMenu.setVisible(true);
		}
	    }
	});

	modelListener = () -> asyncRefresh();
	var service = optionalStatus();
	if (service != null) {
	    service.addListener(modelListener);
	}
	refreshFromModel();
	scheduleTick();
	return container;
    }

    @Override
    public boolean isDynamic() {
	return true;
    }

    @Override
    public void dispose() {
	disposed = true;
	var service = optionalStatus();
	if (service != null && modelListener != null) {
	    service.removeListener(modelListener);
	}
	disposeImage(greenImage);
	disposeImage(greyImage);
	disposeImage(yellowImage);
	if (font != null && !font.isDisposed()) {
	    font.dispose();
	}
	if (popupMenu != null && !popupMenu.isDisposed()) {
	    popupMenu.dispose();
	}
	super.dispose();
    }

    private void scheduleTick() {
	if (disposed || canvas == null || canvas.isDisposed()) {
	    return;
	}
	canvas.getDisplay().timerExec(REFRESH_MS, () -> {
	    if (disposed || canvas == null || canvas.isDisposed()) {
		return;
	    }
	    blinkOn = !blinkOn;
	    refreshFromModel();
	    scheduleTick();
	});
    }

    private void asyncRefresh() {
	if (disposed) {
	    return;
	}
	var display = Display.getDefault();
	if (display == null || display.isDisposed()) {
	    return;
	}
	display.asyncExec(this::refreshFromModel);
    }

    private void refreshFromModel() {
	if (disposed || canvas == null || canvas.isDisposed()) {
	    return;
	}
	var service = optionalStatus();
	var running = service != null && service.isRunning();
	var busy = service != null && service.isBusy();
	var update = service != null && service.getLastCheck() != null && service.getLastCheck().hasAnyUpdate();
	if (busy) {
	    currentImage = blinkOn ? yellowImage : greyImage;
	} else if (running) {
	    currentImage = greenImage;
	} else {
	    currentImage = greyImage;
	}
	statusText = buildLabel(service, running, update);
	canvas.setToolTipText(buildTooltip(service, running, busy));
	canvas.redraw();
    }

    private String buildLabel(LsStatusService service, boolean running, boolean update) {
	var suffix = update ? " \u2191" : "";
	if (service == null) {
	    return "BSL LS" + suffix;
	}
	if (service.getLaunchMode() == LaunchMode.WEBSOCKET) {
	    return (running ? "BSL LS ws" : "BSL LS") + suffix;
	}
	var version = service.getCachedLsVersion();
	if (version != null && !version.isBlank()) {
	    return "BSL LS " + version + suffix;
	}
	return (running ? "BSL LS" : "BSL LS") + suffix;
    }

    private String buildTooltip(LsStatusService service, boolean running, boolean busy) {
	if (service == null) {
	    return "Коннектор BSL LS";
	}
	var mode = modeLabel(service.getLaunchMode());
	var state = busy ? "проверка" : running ? "запущен" : "не запущен";
	var version = service.getCachedLsVersion();
	var text = "Коннектор BSL LS · " + mode + " · " + state;
	if (version != null && !version.isBlank()) {
	    text += " · " + version;
	}
	var check = service.getLastCheck();
	if (check != null) {
	    text += "\n" + check.menuText();
	}
	return text;
    }

    private static String modeLabel(LaunchMode mode) {
	if (mode == LaunchMode.NATIVE) {
	    return "нативный";
	}
	if (mode == LaunchMode.WEBSOCKET) {
	    return "WebSocket";
	}
	return "JAR";
    }

    private void createMenu() {
	popupMenu = new Menu(canvas);
	addMenuItem("Открыть настройки", this::openPreferences);
	new MenuItem(popupMenu, SWT.SEPARATOR);
	updateItem = new MenuItem(popupMenu, SWT.PUSH);
	updateItem.setText("Проверить обновления");
	updateItem.addSelectionListener(new SelectionAdapter() {
	    @Override
	    public void widgetSelected(SelectionEvent e) {
		var service = optionalStatus();
		if (service == null) {
		    return;
		}
		var check = service.getLastCheck();
		if (check != null && check.hasAnyUpdate()) {
		    openPreferences();
		    return;
		}
		service.checkUpdates();
	    }
	});
    }

    private void refreshMenu() {
	if (updateItem == null || updateItem.isDisposed()) {
	    return;
	}
	var service = optionalStatus();
	if (service == null) {
	    return;
	}
	if (service.isCheckingUpdates()) {
	    updateItem.setText("Проверяю обновления…");
	    updateItem.setEnabled(false);
	    return;
	}
	var check = service.getLastCheck();
	if (check == null) {
	    updateItem.setText("Проверить обновления");
	    updateItem.setEnabled(true);
	    return;
	}
	updateItem.setText(check.menuText());
	updateItem.setEnabled(true);
    }

    private void addMenuItem(String title, Runnable action) {
	var item = new MenuItem(popupMenu, SWT.PUSH);
	item.setText(title);
	item.addSelectionListener(new SelectionAdapter() {
	    @Override
	    public void widgetSelected(SelectionEvent e) {
		action.run();
	    }
	});
    }

    private void openPreferences() {
	var shell = canvas.getShell();
	PreferencesUtil.createPreferenceDialogOn(shell, "com.github.otymko.dt.bsl.lsconnector.plugin.page",
		null, null).open();
    }

    private void paint(PaintEvent event) {
	var bounds = canvas.getBounds();
	if (bounds.width <= 0 || bounds.height <= 0) {
	    return;
	}
	var gc = event.gc;
	gc.setAntialias(SWT.ON);
	gc.setTextAntialias(SWT.ON);
	if (font != null && !font.isDisposed()) {
	    gc.setFont(font);
	}
	var centerY = bounds.height / 2;
	if (currentImage != null && !currentImage.isDisposed()) {
	    var imageBounds = currentImage.getBounds();
	    gc.drawImage(currentImage, 0, centerY - imageBounds.height / 2);
	}
	gc.setForeground(canvas.getForeground());
	var text = statusText == null ? "BSL LS" : statusText;
	var room = bounds.width - TEXT_X - 4;
	while (text.length() > 3 && gc.textExtent(text).x > room) {
	    text = text.substring(0, text.length() - 2) + "\u2026";
	}
	var extent = gc.textExtent(text);
	gc.drawText(text, TEXT_X, centerY - extent.y / 2, SWT.DRAW_TRANSPARENT);
    }

    private int measureHeight() {
	var gc = new GC(canvas);
	try {
	    gc.setFont(font);
	    return Math.max(gc.getFontMetrics().getHeight(), CIRCLE_SIZE);
	} finally {
	    gc.dispose();
	}
    }

    private void createCircles(Display display) {
	greenImage = circle(display, 50, 205, 50, 34, 139, 34);
	greyImage = circle(display, 128, 128, 128, 64, 64, 64);
	yellowImage = circle(display, 255, 215, 0, 184, 134, 11);
    }

    private static Image circle(Display display, int r, int g, int b, int br, int bg, int bb) {
	var data = new ImageData(CIRCLE_SIZE, CIRCLE_SIZE, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
	data.transparentPixel = data.palette.getPixel(new RGB(255, 0, 255));
	for (var y = 0; y < CIRCLE_SIZE; y++) {
	    for (var x = 0; x < CIRCLE_SIZE; x++) {
		data.setPixel(x, y, data.transparentPixel);
	    }
	}
	var center = CIRCLE_SIZE / 2.0;
	var radius = center - 1;
	for (var y = 0; y < CIRCLE_SIZE; y++) {
	    for (var x = 0; x < CIRCLE_SIZE; x++) {
		var distance = Math.hypot(x - center, y - center);
		if (distance <= radius - 0.5) {
		    data.setPixel(x, y, data.palette.getPixel(new RGB(r, g, b)));
		} else if (distance <= radius + 0.5) {
		    data.setPixel(x, y, data.palette.getPixel(new RGB(br, bg, bb)));
		}
	    }
	}
	return new Image(display, data);
    }

    private static void disposeImage(Image image) {
	if (image != null && !image.isDisposed()) {
	    image.dispose();
	}
    }

    private LsStatusService optionalStatus() {
	var plugin = BSLPlugin.getPlugin();
	return plugin == null ? null : plugin.getStatusService();
    }
}
