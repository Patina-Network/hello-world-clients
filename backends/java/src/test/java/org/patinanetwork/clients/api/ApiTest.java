package org.patinanetwork.clients.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.patinanetwork.clients.api.echo.EchoController;
import org.patinanetwork.clients.api.greetings.GreetingsController;
import org.patinanetwork.clients.api.health.HealthController;
import org.patinanetwork.clients.config.GrpcClient;
import org.patinanetwork.clients.utilities.StaticContent;
import org.patinanetwork.clients.utilities.exception.ControllerExceptionHandler;
import org.patinanetwork.grpc.helloworld.v1.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Import;

class ApiTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        EchoController.class,
        GreetingsController.class,
        HealthController.class,
        StaticContent.class,
        ControllerExceptionHandler.class
    })
    static class TestApplication {}

    @TempDir
    private Path staticDir;

    private Server grpc;
    private ManagedChannel channel;
    private ServletWebServerApplicationContext http;
    private HttpClient client;
    private String base;
    private final AtomicReference<SayGreetingRequest> sent = new AtomicReference<>();
    private final AtomicReference<GetGreetingsByNameRequest> filter = new AtomicReference<>();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        String name = InProcessServerBuilder.generateName();
        grpc = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new GreeterServiceGrpc.GreeterServiceImplBase() {
                    @Override
                    public void echoHello(EchoHelloRequest r, StreamObserver<EchoHelloResponse> o) {
                        switch (r.getName()) {
                            case "unavailable" ->
                                o.onError(Status.UNAVAILABLE
                                        .withDescription("private infrastructure details")
                                        .asRuntimeException());
                            case "invalid" -> o.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                            case "slow" -> {}
                            default -> {
                                o.onNext(EchoHelloResponse.newBuilder()
                                        .setResponse("hello " + r.getName())
                                        .build());
                                o.onCompleted();
                            }
                        }
                    }

                    @Override
                    public void sayGreeting(SayGreetingRequest r, StreamObserver<SayGreetingResponse> o) {
                        sent.set(r);
                        o.onNext(SayGreetingResponse.getDefaultInstance());
                        o.onCompleted();
                    }

                    @Override
                    public void getGreetingsByName(GetGreetingsByNameRequest r, StreamObserver<GreetingsResponse> o) {
                        filter.set(r);
                        if (r.getRecipientName().equals("missing")) {
                            o.onError(Status.NOT_FOUND.asRuntimeException());
                            return;
                        }
                        o.onNext(GreetingsResponse.newBuilder()
                                .addReplies(GreetingResponse.newBuilder()
                                        .setId(-1)
                                        .setMessage("hi")
                                        .setSenderName("Ada")
                                        .setRecipientName("Lin")
                                        .setReceivedAt(Timestamp.getDefaultInstance()))
                                .build());
                        o.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        http = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class)
                .initializers(context ->
                        context.getBeanFactory().registerSingleton("grpcClient", new GrpcClient(channel, 150)))
                .run("--server.port=0", "--server.address=127.0.0.1", "--client.static-dir=" + staticDir);
        base = "http://127.0.0.1:" + http.getWebServer().getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void cleanup() throws Exception {
        client.close();
        http.close();
        channel.shutdownNow();
        grpc.shutdownNow();
        channel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        grpc.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    HttpResponse<String> call(String method, String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void callsAllGeneratedRpcsAndPreservesPresence() throws Exception {
        var echo = call("GET", "/api/echo?name=Ada", "");
        assertEquals(200, echo.statusCode());
        assertEquals("hello Ada", json.readTree(echo.body()).get("response").asText());
        assertEquals(200, call("POST", "/api/greetings", """
                {"senderName":"Ada","recipientName":"Lin","greeting":"hi"}
                """).statusCode());
        assertEquals("Ada", sent.get().getSenderName());
        assertEquals("Lin", sent.get().getRecipientName());
        assertEquals("hi", sent.get().getGreeting());
        for (String path :
                new String[] {"/api/greetings", "/api/greetings?recipientName=", "/api/greetings?recipientName=Lin"}) {
            var response = call("GET", path, "");
            assertEquals(200, response.statusCode());
            var reply = json.readTree(response.body()).get("replies").get(0);
            assertEquals(4294967295L, reply.get("id").asLong());
            assertEquals("1970-01-01T00:00:00Z", reply.get("receivedAt").asText());
            assertEquals(path.contains("?"), filter.get().hasRecipientName());
        }
    }

    @Test
    void mapsUpstreamErrorsAndHonorsDeadline() throws Exception {
        for (var row : new String[][] {{"invalid", "400"}, {"unavailable", "503"}, {"slow", "504"}}) {
            var r = call("GET", "/api/echo?name=" + row[0], "");
            assertEquals(Integer.parseInt(row[1]), r.statusCode());
            assertFalse(r.body().contains("private infrastructure"));
        }
        assertEquals(
                404, call("GET", "/api/greetings?recipientName=missing", "").statusCode());
    }

    @Test
    void validatesHttpBoundary() throws Exception {
        assertEquals(400, call("GET", "/api/echo", "").statusCode());
        assertEquals(400, call("GET", "/api/echo?name=%20", "").statusCode());
        assertEquals(400, call("GET", "/api/echo?name=" + "x".repeat(257), "").statusCode());
        for (String body : new String[] {"{", "{}", "null", "[]", """
            {"senderName":"A","recipientName":"B","greeting":"hi","unknown":1}
            """, """
            {"senderName":"A","recipientName":"B","greeting":"hi"} {}
            """}) {
            assertEquals(400, call("POST", "/api/greetings", body).statusCode(), body);
        }
        assertEquals(413, call("POST", "/api/greetings", "x".repeat(16385)).statusCode());
        assertEquals(200, call("GET", "/healthz", "").statusCode());
        var method = call("DELETE", "/api/greetings", "");
        assertEquals(405, method.statusCode());
        assertTrue(method.headers().firstValue("Allow").orElse("").contains("GET"));
        var unsupported = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/greetings"))
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(415, unsupported.statusCode());
        assertEquals(404, call("GET", "/api/unknown", "").statusCode());
    }

    @Test
    void servesFrontendAndRejectsTraversal() throws Exception {
        java.nio.file.Files.writeString(staticDir.resolve("index.html"), "<html>client</html>");
        assertEquals("<html>client</html>", call("GET", "/", "").body());
        // Tomcat rejects traversal before Spring dispatches the request.
        assertEquals(400, call("GET", "/../pom.xml", "").statusCode());
    }
}
