package org.patinanetwork.clients.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.patinanetwork.clients.api.echo.EchoController;
import org.patinanetwork.clients.api.greetings.GreetingsController;
import org.patinanetwork.clients.api.health.HealthController;
import org.patinanetwork.clients.common.Responses;
import org.patinanetwork.clients.utilities.StaticContent;
import org.patinanetwork.clients.utilities.exception.ControllerExceptionHandler;
import org.patinanetwork.grpc.helloworld.v1.GreeterServiceGrpc;

public final class Api implements HttpHandler {
    private static final int MAX_CONCURRENT_REQUESTS = 256;

    private final GreeterServiceGrpc.GreeterServiceBlockingStub client;
    private final Duration timeout;
    private final StaticContent staticContent;
    private final HealthController health = new HealthController();
    private final EchoController echo = new EchoController();
    private final GreetingsController greetings = new GreetingsController();
    private final Semaphore requests = new Semaphore(MAX_CONCURRENT_REQUESTS);

    public Api(GreeterServiceGrpc.GreeterServiceBlockingStub client, Duration timeout, Path staticDir) {
        this.client = client;
        this.timeout = timeout;
        this.staticContent = new StaticContent(staticDir);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!requests.tryAcquire()) {
            try (exchange) {
                Responses.error(exchange, 503, "service busy");
            }
            return;
        }

        try (exchange) {
            try {
                route(exchange);
            } catch (Exception e) {
                ControllerExceptionHandler.handle(exchange, e);
            }
        } finally {
            requests.release();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals("/healthz")) {
            health.handle(exchange);
            return;
        }

        var stub = client.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (path.equals("/api/echo")) {
            echo.handle(exchange, stub);
        } else if (path.equals("/api/greetings") && method.equals("POST")) {
            greetings.send(exchange, stub);
        } else if (path.equals("/api/greetings")) {
            greetings.list(exchange, stub);
        } else if (path.startsWith("/api/")) {
            Responses.error(exchange, 404, "not found");
        } else {
            staticContent.handle(exchange, path);
        }
    }
}
