package org.patinanetwork.clients.api.health;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.patinanetwork.clients.common.Responses;

public final class HealthController {
    public void handle(HttpExchange exchange) throws IOException {
        Responses.requireMethod(exchange, "GET");
        Responses.send(exchange, 200, "application/json", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
    }
}
