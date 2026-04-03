package com.example.stepparser;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class StepParserHttpServer {

    private final HttpServer server;

    private StepParserHttpServer(HttpServer server) {
        this.server = server;
    }

    public static StepParserHttpServer create(int port, Path assetRootDirectory, StepGlbExporter exporter) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Path normalizedAssetRoot = assetRootDirectory.toAbsolutePath().normalize();
        Path frontendRoot = Path.of("frontend").toAbsolutePath().normalize();
        Files.createDirectories(normalizedAssetRoot);

        server.createContext("/api/assembly-scene", new AssemblySceneHandler(normalizedAssetRoot, exporter));
        server.createContext("/assets/", new AssetHandler(normalizedAssetRoot));
        server.createContext("/", new FrontendHandler(frontendRoot));
        server.setExecutor(Executors.newCachedThreadPool());
        return new StepParserHttpServer(server);
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    private static final class AssemblySceneHandler implements HttpHandler {

        private final Path assetRootDirectory;
        private final StepGlbExporter exporter;

        private AssemblySceneHandler(Path assetRootDirectory, StepGlbExporter exporter) {
            this.assetRootDirectory = assetRootDirectory;
            this.exporter = exporter;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String stepFileValue = query.get("stepFile");
            if (stepFileValue == null || stepFileValue.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"Missing required query parameter: stepFile.\"}");
                return;
            }

            Path stepFile = Path.of(stepFileValue).toAbsolutePath().normalize();
            if (!Files.isRegularFile(stepFile)) {
                sendJson(exchange, 404, "{\"error\":\"STEP file not found.\"}");
                return;
            }

            String requestId = UUID.randomUUID().toString();
            String assetBasePath = "/assets/" + requestId;
            Path requestAssetDirectory = assetRootDirectory.resolve(requestId);

            try {
                StepAssemblyScene scene = StepAssemblySceneBuilder.build(stepFile, requestAssetDirectory, assetBasePath, exporter);
                sendJson(exchange, 200, StepJsonWriter.writeScene(scene));
            } catch (Exception exception) {
                sendJson(exchange, 500, "{\"error\":" + quote(exception.getMessage()) + "}");
            }
        }
    }

    private static final class AssetHandler implements HttpHandler {

        private final Path assetRootDirectory;

        private AssetHandler(Path assetRootDirectory) {
            this.assetRootDirectory = assetRootDirectory;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            String relative = requestPath.substring("/assets/".length());
            Path target = assetRootDirectory.resolve(relative).normalize();
            if (!target.startsWith(assetRootDirectory) || !Files.isRegularFile(target)) {
                sendJson(exchange, 404, "{\"error\":\"Asset not found.\"}");
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "model/gltf-binary");
            headers.set("Access-Control-Allow-Origin", "*");
            byte[] body = Files.readAllBytes(target);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }

    private static final class FrontendHandler implements HttpHandler {

        private final Path frontendRoot;

        private FrontendHandler(Path frontendRoot) {
            this.frontendRoot = frontendRoot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
                return;
            }

            Path target = resolveFrontendTarget(exchange.getRequestURI().getPath());
            if (target == null || !Files.isRegularFile(target)) {
                sendJson(exchange, 404, "{\"error\":\"Frontend file not found.\"}");
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(target));
            headers.set("Access-Control-Allow-Origin", "*");
            byte[] body = Files.readAllBytes(target);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        private Path resolveFrontendTarget(String requestPath) {
            String normalizedRequest = (requestPath == null || requestPath.isBlank()) ? "/" : requestPath;
            if ("/".equals(normalizedRequest)) {
                return frontendRoot.resolve("index.html");
            }

            String relative = normalizedRequest.startsWith("/") ? normalizedRequest.substring(1) : normalizedRequest;
            Path target = frontendRoot.resolve(relative).normalize();
            if (!target.startsWith(frontendRoot)) {
                return null;
            }
            return target;
        }

        private String contentType(Path target) {
            String fileName = target.getFileName().toString();
            if (fileName.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (fileName.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (fileName.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            return "application/octet-stream";
        }
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            String value = parts.length > 1 ? urlDecode(parts[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
