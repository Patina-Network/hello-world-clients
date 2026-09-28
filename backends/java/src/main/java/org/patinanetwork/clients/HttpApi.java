package org.patinanetwork.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.patinanetwork.grpc.helloworld.v1.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class HttpApi implements HttpHandler {
    private static final int MAX_BODY = 16 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final JsonFormat.Printer PROTO_JSON = JsonFormat.printer()
            .alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace();
    private static final System.Logger LOG = System.getLogger(HttpApi.class.getName());
    private final GreeterServiceGrpc.GreeterServiceBlockingStub client;
    private final Duration timeout;
    private final Path staticDir;
    private final Semaphore requests = new Semaphore(256);

    public HttpApi(GreeterServiceGrpc.GreeterServiceBlockingStub client, Duration timeout, Path staticDir) {
        this.client = client;
        this.timeout = timeout;
        this.staticDir = staticDir.toAbsolutePath().normalize();
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!requests.tryAcquire()) {
            try (exchange) { error(exchange, 503, "service busy"); }
            return;
        }
        try (exchange) {
            try {
                route(exchange);
            } catch (StatusRuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "gRPC request failed: {0}", e.getStatus().getCode());
                var failure = grpcError(e.getStatus().getCode());
                error(exchange, failure.status(), failure.message());
            } catch (BadRequest e) {
                error(exchange, e.status, e.getMessage());
            } catch (IllegalArgumentException e) {
                error(exchange, 400, "invalid request");
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "HTTP request failed", e);
                error(exchange, 500, "internal error");
            }
        } finally { requests.release(); }
    }

    private void route(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        String method = x.getRequestMethod();
        if (path.equals("/healthz")) {
            requireMethod(x, "GET");
            send(x, 200, "application/json", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        var stub = client.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (path.equals("/api/echo")) {
            requireMethod(x, "GET");
            String name = query(x).getOrDefault("name", "");
            validate(name, 256, "name");
            reply(x, stub.echoHello(EchoHelloRequest.newBuilder().setName(name).build()));
        } else if (path.equals("/api/greetings") && method.equals("POST")) {
            String type = x.getRequestHeaders().getFirst("Content-Type");
            if (type == null || !type.split(";", 2)[0].equals("application/json")) {
                throw new BadRequest(415, "application/json required");
            }
            byte[] body = x.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) throw new BadRequest(413, "request body too large");
            JsonNode input;
            try { input = JSON.readTree(body); }
            catch (IOException e) { throw new BadRequest(400, "invalid JSON request"); }
            if (input == null || !input.isObject()) throw new BadRequest(400, "JSON object required");
            Set<String> allowed = Set.of("senderName", "recipientName", "greeting");
            input.fieldNames().forEachRemaining(key -> {
                if (!allowed.contains(key)) throw new BadRequest(400, "unknown field");
            });
            reply(x, stub.sayGreeting(SayGreetingRequest.newBuilder()
                    .setSenderName(field(input, "senderName", 256))
                    .setRecipientName(field(input, "recipientName", 256))
                    .setGreeting(field(input, "greeting", 4096)).build()));
        } else if (path.equals("/api/greetings")) {
            requireMethod(x, "GET", "POST");
            var request = GetGreetingsByNameRequest.newBuilder();
            Map<String, String> params = query(x);
            if (params.containsKey("recipientName")) {
                String name = params.get("recipientName");
                if (name.getBytes(StandardCharsets.UTF_8).length > 256)
                    throw new BadRequest(400, "recipientName exceeds 256 UTF-8 bytes");
                request.setRecipientName(name);
            }
            reply(x, stub.getGreetingsByName(request.build()));
        } else if (path.startsWith("/api/")) {
            error(x, 404, "not found");
        } else {
            requireMethod(x, "GET");
            Path file = staticDir.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
            if (!file.startsWith(staticDir) || !Files.isRegularFile(file)
                    || !file.toRealPath().startsWith(staticDir.toRealPath())) {
                error(x, 404, "not found"); return;
            }
            String contentType = file.toString().endsWith(".js") ? "text/javascript" :
                    file.toString().endsWith(".css") ? "text/css" :
                    file.toString().endsWith(".html") ? "text/html; charset=utf-8" : "application/octet-stream";
            send(x, 200, contentType, Files.readAllBytes(file));
        }
    }
    private static String field(JsonNode input, String name, int max) {
        JsonNode node = input.get(name);
        if (node == null || !node.isTextual()) throw new BadRequest(400, name + " must be a string");
        String value = node.textValue(); validate(value, max, name); return value;
    }
    private static void validate(String value, int max, String name) {
        if (value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max)
            throw new BadRequest(400, name + " must contain 1–" + max + " UTF-8 bytes");
    }
    private static Map<String, String> query(HttpExchange x) {
        Map<String, String> params = new HashMap<>();
        String query = x.getRequestURI().getRawQuery();
        if (query != null) for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            params.putIfAbsent(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }
    private static void requireMethod(HttpExchange x, String... methods) {
        if (!Set.of(methods).contains(x.getRequestMethod())) {
            x.getResponseHeaders().set("Allow", String.join(", ", methods));
            throw new BadRequest(405, "method not allowed");
        }
    }
    private static void reply(HttpExchange x, MessageOrBuilder message) throws IOException {
        send(x, 200, "application/json", PROTO_JSON.print(message).getBytes(StandardCharsets.UTF_8));
    }
    private static void error(HttpExchange x, int status, String message) throws IOException {
        send(x, status, "application/json", JSON.writeValueAsBytes(Map.of("error", message)));
    }
    private static void send(HttpExchange x, int status, String type, byte[] body) throws IOException {
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.sendResponseHeaders(status, body.length);
        x.getResponseBody().write(body);
    }
    private record Failure(int status, String message) {}
    private static Failure grpcError(Status.Code code) {
        return switch (code) {
            case INVALID_ARGUMENT -> new Failure(400, "invalid request");
            case NOT_FOUND -> new Failure(404, "not found");
            case ALREADY_EXISTS -> new Failure(409, "already exists");
            case UNAUTHENTICATED -> new Failure(401, "authentication required");
            case PERMISSION_DENIED -> new Failure(403, "permission denied");
            case RESOURCE_EXHAUSTED -> new Failure(429, "resource exhausted");
            case UNAVAILABLE -> new Failure(503, "service unavailable");
            case DEADLINE_EXCEEDED, CANCELLED -> new Failure(504, "upstream timeout");
            default -> new Failure(502, "upstream request failed");
        };
    }
    private static final class BadRequest extends RuntimeException {
        private final int status;
        BadRequest(int status, String message) { super(message); this.status = status; }
    }
}
