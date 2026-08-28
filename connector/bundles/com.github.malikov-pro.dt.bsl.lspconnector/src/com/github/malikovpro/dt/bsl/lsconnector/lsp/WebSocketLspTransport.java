package com.github.malikovpro.dt.bsl.lsconnector.lsp;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import com.github.malikovpro.dt.bsl.lsconnector.BSLPlugin;

public class WebSocketLspTransport implements Closeable {
    private static final int PIPE_BUFFER = 1024 * 1024;

    private final PipedInputStream inputFromServer;
    private final PipedOutputStream serverToClient;
    private final LspToWebSocketOutputStream outputToServer;
    private final StringBuilder fragment = new StringBuilder();
    private volatile WebSocket webSocket;
    private volatile boolean open;

    private WebSocketLspTransport() throws IOException {
	inputFromServer = new PipedInputStream(PIPE_BUFFER);
	serverToClient = new PipedOutputStream(inputFromServer);
	outputToServer = new LspToWebSocketOutputStream();
    }

    public static WebSocketLspTransport connect(URI uri) throws IOException {
	var transport = new WebSocketLspTransport();
	var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	try {
	    transport.webSocket = client.newWebSocketBuilder()
		    .buildAsync(uri, transport.new Listener())
		    .get(15, TimeUnit.SECONDS);
	    transport.open = true;
	    return transport;
	} catch (Exception e) {
	    transport.closeQuietly();
	    if (e instanceof InterruptedException) {
		Thread.currentThread().interrupt();
	    }
	    throw new IOException("Не удалось открыть WebSocket " + uri, e);
	}
    }

    public InputStream getInputStream() {
	return inputFromServer;
    }

    public OutputStream getOutputStream() {
	return outputToServer;
    }

    public boolean isOpen() {
	return open && webSocket != null && !webSocket.isOutputClosed();
    }

    @Override
    public void close() {
	open = false;
	var socket = webSocket;
	if (socket != null) {
	    try {
		socket.sendClose(WebSocket.NORMAL_CLOSURE, "").orTimeout(2, TimeUnit.SECONDS);
	    } catch (Exception e) {
		// already closed
	    }
	}
	closeQuietly();
    }

    private void closeQuietly() {
	try {
	    serverToClient.close();
	} catch (IOException e) {
	    // ignore
	}
	try {
	    inputFromServer.close();
	} catch (IOException e) {
	    // ignore
	}
    }

    private void deliverFromServer(String payload) throws IOException {
	byte[] body;
	if (payload.startsWith("Content-Length")) {
	    body = payload.getBytes(StandardCharsets.UTF_8);
	} else {
	    var json = payload.getBytes(StandardCharsets.UTF_8);
	    var header = ("Content-Length: " + json.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
	    body = new byte[header.length + json.length];
	    System.arraycopy(header, 0, body, 0, header.length);
	    System.arraycopy(json, 0, body, header.length, json.length);
	}
	synchronized (serverToClient) {
	    serverToClient.write(body);
	    serverToClient.flush();
	}
    }

    private class Listener implements WebSocket.Listener {
	@Override
	public void onOpen(WebSocket webSocket) {
	    webSocket.request(1);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
	    fragment.append(data);
	    if (last) {
		var payload = fragment.toString();
		fragment.setLength(0);
		try {
		    deliverFromServer(payload);
		} catch (IOException e) {
		    BSLPlugin.logError("Ошибка чтения LSP с WebSocket", e);
		    open = false;
		}
	    }
	    webSocket.request(1);
	    return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
	    open = false;
	    closeQuietly();
	    return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
	    open = false;
	    BSLPlugin.logError("Ошибка WebSocket BSL LS", error);
	    closeQuietly();
	}
    }

    private class LspToWebSocketOutputStream extends OutputStream {
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	@Override
	public synchronized void write(int b) throws IOException {
	    buffer.write(b);
	    flushCompleteMessages();
	}

	@Override
	public synchronized void write(byte[] b, int off, int len) throws IOException {
	    buffer.write(b, off, len);
	    flushCompleteMessages();
	}

	@Override
	public synchronized void flush() throws IOException {
	    flushCompleteMessages();
	}

	private void flushCompleteMessages() throws IOException {
	    while (true) {
		var data = buffer.toByteArray();
		var asLatin = new String(data, StandardCharsets.ISO_8859_1);
		var headerEnd = asLatin.indexOf("\r\n\r\n");
		var sepLen = 4;
		if (headerEnd < 0) {
		    headerEnd = asLatin.indexOf("\n\n");
		    sepLen = 2;
		}
		if (headerEnd < 0) {
		    return;
		}
		var headers = asLatin.substring(0, headerEnd);
		var contentLength = parseContentLength(headers);
		if (contentLength < 0) {
		    throw new IOException("В исходящем LSP-сообщении нет Content-Length");
		}
		var bodyStart = headerEnd + sepLen;
		if (data.length < bodyStart + contentLength) {
		    return;
		}
		var json = new String(data, bodyStart, contentLength, StandardCharsets.UTF_8);
		var socket = webSocket;
		if (socket == null || socket.isOutputClosed()) {
		    throw new IOException("WebSocket закрыт");
		}
		try {
		    socket.sendText(json, true).orTimeout(10, TimeUnit.SECONDS).join();
		} catch (Exception e) {
		    throw new IOException("Не удалось отправить LSP-сообщение в WebSocket", e);
		}
		buffer.reset();
		var remaining = data.length - (bodyStart + contentLength);
		if (remaining > 0) {
		    buffer.write(data, bodyStart + contentLength, remaining);
		}
	    }
	}

	private int parseContentLength(String headers) {
	    for (var line : headers.split("\\r?\\n")) {
		var idx = line.indexOf(':');
		if (idx < 0) {
		    continue;
		}
		if ("Content-Length".equalsIgnoreCase(line.substring(0, idx).trim())) {
		    try {
			return Integer.parseInt(line.substring(idx + 1).trim());
		    } catch (NumberFormatException e) {
			return -1;
		    }
		}
	    }
	    return -1;
	}
    }
}
