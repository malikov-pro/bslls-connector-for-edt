package com.github.otymko.dt.bsl.lsconnector.check;

import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;

public final class LsDiagnosticInfo {
    private final String code;
    private final String title;
    private final IssueType issueType;
    private final IssueSeverity severity;

    LsDiagnosticInfo(String code, String title, IssueType issueType, IssueSeverity severity) {
	this.code = code;
	this.title = title;
	this.issueType = issueType;
	this.severity = severity;
    }

    public String getCode() {
	return code;
    }

    public String getTitle() {
	return title;
    }

    public IssueType getIssueType() {
	return issueType;
    }

    public IssueSeverity getSeverity() {
	return severity;
    }

    public String getV8stdUrl() {
	return LsDiagnosticCatalog.v8stdUrl(code);
    }

    public String getDescription() {
	return title + ". Документация: " + getV8stdUrl();
    }
}
