package org.patinanetwork.clients.api.greetings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.patinanetwork.clients.api.greetings.body.SayGreetingBody;
import org.patinanetwork.clients.common.Responses;
import org.patinanetwork.clients.config.GrpcClient;
import org.patinanetwork.clients.utilities.exception.ValidationException;
import org.patinanetwork.grpc.helloworld.v1.GetGreetingsByNameRequest;
import org.patinanetwork.grpc.helloworld.v1.SayGreetingRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public final class GreetingsController {
    private final GrpcClient client;

    public GreetingsController(GrpcClient client) {
        this.client = client;
    }

    @PostMapping("/api/greetings")
    public void send(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var stub = client.stub();
        String type = request.getContentType();
        if (type == null || !type.split(";", 2)[0].equals("application/json")) {
            throw new ValidationException(415, "application/json required");
        }
        byte[] body = request.getInputStream().readNBytes(SayGreetingBody.MAX_BYTES + 1);
        if (body.length > SayGreetingBody.MAX_BYTES) {
            throw new ValidationException(413, "request body too large");
        }
        SayGreetingBody input = SayGreetingBody.parse(body);
        Responses.reply(
                response,
                stub.sayGreeting(SayGreetingRequest.newBuilder()
                        .setSenderName(input.getSenderName())
                        .setRecipientName(input.getRecipientName())
                        .setGreeting(input.getGreeting())
                        .build()));
    }

    @GetMapping("/api/greetings")
    public void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var stub = client.stub();
        var rpcRequest = GetGreetingsByNameRequest.newBuilder();
        Map<String, String> params = Responses.query(request);
        if (params.containsKey("recipientName")) {
            String name = params.get("recipientName");
            if (name.getBytes(StandardCharsets.UTF_8).length > 256) {
                throw new ValidationException(400, "recipientName exceeds 256 UTF-8 bytes");
            }
            rpcRequest.setRecipientName(name);
        }
        Responses.reply(response, stub.getGreetingsByName(rpcRequest.build()));
    }
}
