package org.patinanetwork.clients.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.patinanetwork.grpc.helloworld.v1.GreeterServiceGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class GrpcClient {
    private final ManagedChannel channel;
    private final long timeoutMs;

    @org.springframework.beans.factory.annotation.Autowired
    public GrpcClient(
            @Value("${grpc.target}") String target,
            @Value("${grpc.tls}") String tls,
            @Value("${grpc.timeout-ms}") long timeoutMs) {
        if (timeoutMs < 1 || timeoutMs > 30000) {
            throw new IllegalArgumentException("GRPC_TIMEOUT_MS must be 1–30000");
        }
        this.timeoutMs = timeoutMs;
        var builder = ManagedChannelBuilder.forTarget(target);
        switch (tls) {
            case "true" -> builder.useTransportSecurity();
            case "false" -> builder.usePlaintext();
            default -> throw new IllegalArgumentException("GRPC_TLS must be true or false");
        }
        channel = builder.build();
    }

    public GrpcClient(ManagedChannel channel, long timeoutMs) {
        this.channel = channel;
        this.timeoutMs = timeoutMs;
    }

    public GreeterServiceGrpc.GreeterServiceBlockingStub stub() {
        return GreeterServiceGrpc.newBlockingStub(channel).withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
