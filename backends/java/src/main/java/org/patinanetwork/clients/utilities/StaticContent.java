package org.patinanetwork.clients.utilities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.patinanetwork.clients.common.Responses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public final class StaticContent {
    private final Path staticDir;

    public StaticContent(@Value("${client.static-dir}") String staticDir) {
        this.staticDir = Path.of(staticDir).toAbsolutePath().normalize();
    }

    @GetMapping("/**")
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        Path file = staticDir
                .resolve(path.equals("/") ? "index.html" : path.substring(1))
                .normalize();
        if (!file.startsWith(staticDir)
                || !Files.isRegularFile(file)
                || !file.toRealPath().startsWith(staticDir.toRealPath())) {
            Responses.error(response, 404, "not found");
            return;
        }
        Responses.send(response, 200, contentType(file), Files.readAllBytes(file));
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
