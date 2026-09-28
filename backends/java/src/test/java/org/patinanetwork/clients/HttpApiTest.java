package org.patinanetwork.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.patinanetwork.grpc.helloworld.v1.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpApiTest {
    @TempDir Path staticDir;
    Server grpc;
    ManagedChannel channel;
    HttpServer http;
    ExecutorService executor;
    HttpClient client;
    String base;
    final AtomicReference<SayGreetingRequest> sent = new AtomicReference<>();
    final AtomicReference<GetGreetingsByNameRequest> filter = new AtomicReference<>();
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        String name = InProcessServerBuilder.generateName();
        grpc = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new GreeterServiceGrpc.GreeterServiceImplBase() {
                    @Override public void echoHello(EchoHelloRequest r, StreamObserver<EchoHelloResponse> o) {
                        switch (r.getName()) {
                            case "unavailable" -> o.onError(Status.UNAVAILABLE.withDescription("private infrastructure details").asRuntimeException());
                            case "invalid" -> o.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                            case "slow" -> { }
                            default -> { o.onNext(EchoHelloResponse.newBuilder().setResponse("hello " + r.getName()).build()); o.onCompleted(); }
                        }
                    }
                    @Override public void sayGreeting(SayGreetingRequest r, StreamObserver<SayGreetingResponse> o) {
                        sent.set(r); o.onNext(SayGreetingResponse.getDefaultInstance()); o.onCompleted();
                    }
                    @Override public void getGreetingsByName(GetGreetingsByNameRequest r, StreamObserver<GreetingsResponse> o) {
                        filter.set(r);
                        if (r.getRecipientName().equals("missing")) { o.onError(Status.NOT_FOUND.asRuntimeException()); return; }
                        o.onNext(GreetingsResponse.newBuilder().addReplies(GreetingResponse.newBuilder()
                                .setId(-1).setMessage("hi").setSenderName("Ada").setRecipientName("Lin")
                                .setReceivedAt(Timestamp.getDefaultInstance())).build()); o.onCompleted();
                    }
                }).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        http.setExecutor(executor);
        http.createContext("/", new HttpApi(GreeterServiceGrpc.newBlockingStub(channel), Duration.ofMillis(150), staticDir));
        http.start();
        base = "http://127.0.0.1:" + http.getAddress().getPort();
        client = HttpClient.newHttpClient();
    }
    @AfterEach void cleanup() throws Exception {
        client.close(); http.stop(0); executor.close(); channel.shutdownNow(); grpc.shutdownNow();
        channel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        grpc.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    HttpResponse<String> call(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void callsAllGeneratedRpcsAndPreservesPresence() throws Exception {
        var echo = call("GET", "/api/echo?name=Ada", "");
        assertEquals(200, echo.statusCode()); assertEquals("hello Ada", json.readTree(echo.body()).get("response").asText());
        assertEquals(200, call("POST", "/api/greetings", """
                {"senderName":"Ada","recipientName":"Lin","greeting":"hi"}
                """).statusCode());
        assertEquals("Ada", sent.get().getSenderName()); assertEquals("Lin", sent.get().getRecipientName()); assertEquals("hi", sent.get().getGreeting());
        for (String path : new String[]{"/api/greetings", "/api/greetings?recipientName=", "/api/greetings?recipientName=Lin"}) {
            var response = call("GET", path, ""); assertEquals(200, response.statusCode());
            var reply = json.readTree(response.body()).get("replies").get(0);
            assertEquals(4294967295L, reply.get("id").asLong()); assertEquals("1970-01-01T00:00:00Z", reply.get("receivedAt").asText());
            assertEquals(path.contains("?"), filter.get().hasRecipientName());
        }
    }
    @Test void mapsUpstreamErrorsAndHonorsDeadline() throws Exception {
        for (var row : new String[][]{{"invalid","400"},{"unavailable","503"},{"slow","504"}}) {
            var r = call("GET", "/api/echo?name=" + row[0], "");
            assertEquals(Integer.parseInt(row[1]), r.statusCode()); assertFalse(r.body().contains("private infrastructure"));
        }
        assertEquals(404, call("GET", "/api/greetings?recipientName=missing", "").statusCode());
    }
    @Test void validatesHttpBoundary() throws Exception {
        assertEquals(400, call("GET", "/api/echo", "").statusCode());
        assertEquals(400, call("GET", "/api/echo?name=%20", "").statusCode());
        assertEquals(400, call("GET", "/api/echo?name=" + "x".repeat(257), "").statusCode());
        for (String body : new String[]{"{", "{}", "null", "[]", """
            {"senderName":"A","recipientName":"B","greeting":"hi","unknown":1}
            """, """
            {"senderName":"A","recipientName":"B","greeting":"hi"} {}
            """}) assertEquals(400, call("POST", "/api/greetings", body).statusCode(), body);
        assertEquals(413, call("POST", "/api/greetings", "x".repeat(16385)).statusCode());
        assertEquals(200, call("GET", "/healthz", "").statusCode());
    }
    @Test void servesFrontendAndRejectsTraversal() throws Exception {
        java.nio.file.Files.writeString(staticDir.resolve("index.html"), "<html>client</html>");
        assertEquals("<html>client</html>", call("GET", "/", "").body());
        assertEquals(404, call("GET", "/../pom.xml", "").statusCode());
    }
}
