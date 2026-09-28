package org.patinanetwork.clients;

import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.patinanetwork.clients.api.Api;
import org.patinanetwork.grpc.helloworld.v1.GreeterServiceGrpc;

public final class ClientsApplication {
    private ClientsApplication() {}

    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }

    public static void main(String[] args) throws Exception {
        long timeoutMs = Long.parseLong(env("GRPC_TIMEOUT_MS", "3000"));
        if (timeoutMs <= 0 || timeoutMs > 30000) {
            throw new IllegalArgumentException("GRPC_TIMEOUT_MS must be 1–30000");
        }
        var builder = ManagedChannelBuilder.forTarget(env("GRPC_TARGET", "hello-world-grpc-service:50051"));
        switch (env("GRPC_TLS", "false")) {
            case "true" -> builder.useTransportSecurity();
            case "false" -> builder.usePlaintext();
            default -> throw new IllegalArgumentException("GRPC_TLS must be true or false");
        }
        System.setProperty("sun.net.httpserver.maxReqTime", "10");
        System.setProperty("sun.net.httpserver.maxRspTime", "35");
        System.setProperty("sun.net.httpserver.maxReqHeaders", "100");
        System.setProperty("jdk.httpserver.maxConnections", "512");
        ManagedChannel channel = builder.build();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(env("HTTP_HOST", "0.0.0.0"), Integer.parseInt(env("HTTP_PORT", "8080"))), 128);
        server.createContext(
                "/",
                new Api(
                        GreeterServiceGrpc.newBlockingStub(channel),
                        Duration.ofMillis(timeoutMs),
                        Path.of(env("STATIC_DIR", "../../frontend/dist"))));
        server.setExecutor(executor);
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            server.stop(10);
                            channel.shutdown();
                            try {
                                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                                    channel.shutdownNow();
                                }
                            } catch (InterruptedException e) {
                                channel.shutdownNow();
                                Thread.currentThread().interrupt();
                            }
                            executor.close();
                        },
                        "shutdown"));
        server.start();
        System.getLogger(ClientsApplication.class.getName())
                .log(System.Logger.Level.INFO, "HTTP server started on {0}", server.getAddress());
    }
}
