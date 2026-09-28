package org.patinanetwork.clients.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.patinanetwork.clients.utilities.exception.ValidationException;

public final class Responses {
    public static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final JsonFormat.Printer PROTO_JSON =
            JsonFormat.printer().alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace();

    private Responses() {}

    public static void requireMethod(HttpExchange exchange, String... methods) {
        if (!Set.of(methods).contains(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", String.join(", ", methods));
            throw new ValidationException(405, "method not allowed");
        }
    }

    public static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                params.putIfAbsent(
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
        }
        return params;
    }

    public static void validate(String value, int max, String name) {
        if (value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max) {
            throw new ValidationException(400, name + " must contain 1–" + max + " UTF-8 bytes");
        }
    }

    public static void reply(HttpExchange exchange, MessageOrBuilder message) throws IOException {
        send(exchange, 200, "application/json", PROTO_JSON.print(message).getBytes(StandardCharsets.UTF_8));
    }

    public static void error(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, "application/json", JSON.writeValueAsBytes(Map.of("error", message)));
    }

    public static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
