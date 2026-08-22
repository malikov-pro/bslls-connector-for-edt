package com.github.otymko.dt.bsl.lsconnector;

import static com._1c.g5.v8.dt.bsl.model.BslPackage.Literals.MODULE;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com._1c.g5.v8.dt.bsl.model.Module;
import com.e1c.g5.v8.dt.check.BslDirectLocationIssue;
import com.e1c.g5.v8.dt.check.CheckComplexity;
import com.e1c.g5.v8.dt.check.DirectLocation;
import com.e1c.g5.v8.dt.check.ICheckParameters;
import com.e1c.g5.v8.dt.check.components.BasicCheck;
import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;
import com.github.otymko.dt.bsl.lsconnector.lsp.BSLConnector;
import com.github.otymko.dt.bsl.lsconnector.util.BSLCommon;

public class BSLLsCheck extends BasicCheck<Void> {
    public static final String CHECK_ID = "bsl-ls";

    @Override
    public String getCheckId() {
	return CHECK_ID;
    }

    @Override
    protected void configureCheck(CheckConfigurer configurationBuilder) {
	configurationBuilder.title("BSL Language Server")
		.description("Diagnostics from BSL Language Server")
		.complexity(CheckComplexity.COMPLEX)
		.severity(IssueSeverity.MINOR)
		.issueType(IssueType.CODE_STYLE)
		.module()
		.checkedObjectType(MODULE);
    }

    @Override
    protected void check(Object object, ResultAcceptor resultAcceptor, ICheckParameters parameters,
	    IProgressMonitor progressMonitor) {
	if (progressMonitor.isCanceled() || !(object instanceof Module)) {
	    return;
	}

	var plugin = BSLPlugin.getPlugin();
	if (plugin == null || !plugin.isRunningLS()) {
	    return;
	}

	var connector = plugin.getLsService().getConnector();
	if (connector == null) {
	    return;
	}

	plugin.getStatusService().beginBusy();
	try {
	    checkModule(plugin, connector, (Module) object, resultAcceptor, progressMonitor);
	} finally {
	    plugin.getStatusService().endBusy();
	}
    }

    private void checkModule(BSLPlugin plugin, BSLConnector connector,
	    Module module, ResultAcceptor resultAcceptor, IProgressMonitor progressMonitor) {
	var node = NodeModelUtils.findActualNodeFor(module);
	if (node == null) {
	    return;
	}
	var content = node.getText();
	if (content == null) {
	    content = "";
	}
	var document = new Document(content);
	var moduleFile = ResourcesPlugin.getWorkspace().getRoot()
		.getFile(new Path(EcoreUtil.getURI(module).toPlatformString(true)));
	var uri = BSLCommon.uri(moduleFile.getLocationURI());

	var alreadyOpened = plugin.getWorkbenchParts().contains(uri.toString());
	if (alreadyOpened) {
	    connector.textDocumentDidChange(uri, content);
	} else {
	    plugin.getWorkbenchParts().add(uri.toString());
	    connector.textDocumentDidOpen(uri, content);
	}

	try {
	    plugin.sleepCurrentThread(1000);
	    if (progressMonitor.isCanceled() || !plugin.getWorkbenchParts().contains(uri.toString())) {
		return;
	    }

	    var diagnostics = connector.diagnostics(uri.toString());
	    if (progressMonitor.isCanceled()) {
		return;
	    }

	    for (Diagnostic diagnostic : diagnostics) {
		if (progressMonitor.isCanceled()) {
		    return;
		}
		acceptIssue(module, resultAcceptor, diagnostic, document);
	    }
	} finally {
	    if (!alreadyOpened && plugin.getWorkbenchParts().contains(uri.toString())) {
		plugin.getWorkbenchParts().remove(uri.toString());
		connector.textDocumentDidClose(uri);
	    }
	}
    }

    private void acceptIssue(Module module, ResultAcceptor resultAcceptor, Diagnostic diagnostic, Document document) {
	int[] offsetParams;
	try {
	    offsetParams = BSLCommon.getOffsetByRange(diagnostic.getRange(), document);
	} catch (BadLocationException e) {
	    BSLPlugin.createErrorStatus(e.getMessage(), e);
	    return;
	}

	var code = diagnostic.getCode() == null ? "BSL LS" : diagnostic.getCode().map(c -> c, Object::toString);
	var message = "[" + code + "] " + diagnostic.getMessage();
	var lineNumber = diagnostic.getRange().getStart().getLine() + 1;
	var location = new DirectLocation(offsetParams[0], offsetParams[1], lineNumber, module);
	resultAcceptor.addIssue(new BslDirectLocationIssue(message, location));
    }

}
