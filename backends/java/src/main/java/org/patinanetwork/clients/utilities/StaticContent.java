package org.patinanetwork.clients.utilities;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.patinanetwork.clients.common.Responses;

public final class StaticContent {
    private final Path staticDir;

    public StaticContent(Path staticDir) {
        this.staticDir = staticDir.toAbsolutePath().normalize();
    }

    public void handle(HttpExchange exchange, String path) throws IOException {
        Responses.requireMethod(exchange, "GET");
        Path file = staticDir
                .resolve(path.equals("/") ? "index.html" : path.substring(1))
                .normalize();
        if (!file.startsWith(staticDir)
                || !Files.isRegularFile(file)
                || !file.toRealPath().startsWith(staticDir.toRealPath())) {
            Responses.error(exchange, 404, "not found");
            return;
        }
        Responses.send(exchange, 200, contentType(file), Files.readAllBytes(file));
    }

    private static String contentType(Path file) {
        String name = file.toString();
        if (name.endsWith(".js")) {
            return "text/javascript";
        }
        if (name.endsWith(".css")) {
            return "text/css";
        }
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
