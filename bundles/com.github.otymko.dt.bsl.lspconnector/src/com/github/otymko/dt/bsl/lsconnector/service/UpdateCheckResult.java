package com.github.otymko.dt.bsl.lsconnector.service;

public final class UpdateCheckResult {
    private String lsLatestTag = "";
    private String connectorLatestTag = "";
    private String error = "";

    public boolean hasLsUpdate() {
	return lsLatestTag != null && !lsLatestTag.isBlank();
    }

    public boolean hasConnectorUpdate() {
	return connectorLatestTag != null && !connectorLatestTag.isBlank();
    }

    public boolean hasAnyUpdate() {
	return hasLsUpdate() || hasConnectorUpdate();
    }

    public String getLsLatestTag() {
	return lsLatestTag;
    }

    public void setLsLatestTag(String lsLatestTag) {
	this.lsLatestTag = lsLatestTag == null ? "" : lsLatestTag;
    }

    public String getConnectorLatestTag() {
	return connectorLatestTag;
    }

    public void setConnectorLatestTag(String connectorLatestTag) {
	this.connectorLatestTag = connectorLatestTag == null ? "" : connectorLatestTag;
    }

    public String getError() {
	return error;
    }

    public void setError(String error) {
	this.error = error == null ? "" : error;
    }

    public String menuText() {
	if (!error.isEmpty()) {
	    return "Ошибка проверки: " + error;
	}
	if (!hasAnyUpdate()) {
	    return "Обновлений нет";
	}
	var text = new StringBuilder("Есть обновление");
	if (hasLsUpdate()) {
	    text.append(" · LS ").append(lsLatestTag);
	}
	if (hasConnectorUpdate()) {
	    text.append(" · коннектор ").append(connectorLatestTag);
	}
	return text.toString();
    }
}
