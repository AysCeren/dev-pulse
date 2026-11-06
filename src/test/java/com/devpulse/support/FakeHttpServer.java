package com.devpulse.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * A local stand-in for an external API, so tests make no real network calls.
 */
public class FakeHttpServer implements AutoCloseable {

	private final HttpServer server;

	private volatile int status = 200;
	private volatile String responseBody = "{}";
	private volatile Duration delay = Duration.ZERO;
	private volatile int requestCount;
	private volatile String lastRequestBody;
	private volatile String lastRequestPath;
	private volatile Headers lastRequestHeaders;

	public FakeHttpServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/", exchange -> {
			requestCount++;
			lastRequestPath = exchange.getRequestURI().getPath();
			lastRequestHeaders = exchange.getRequestHeaders();
			lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	public String url() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	public void respondWith(int status, String body) {
		this.status = status;
		this.responseBody = body;
	}

	public void delayResponsesBy(Duration delay) {
		this.delay = delay;
	}

	public int requestCount() {
		return requestCount;
	}

	public String lastRequestBody() {
		return lastRequestBody;
	}

	public String lastRequestPath() {
		return lastRequestPath;
	}

	public String lastRequestHeader(String name) {
		return lastRequestHeaders.getFirst(name);
	}

	@Override
	public void close() {
		server.stop(0);
	}

}
