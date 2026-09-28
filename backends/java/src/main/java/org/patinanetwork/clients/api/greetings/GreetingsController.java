package org.patinanetwork.clients.api.greetings;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.patinanetwork.clients.api.greetings.body.SayGreetingBody;
import org.patinanetwork.clients.common.Responses;
import org.patinanetwork.clients.utilities.exception.ValidationException;
import org.patinanetwork.grpc.helloworld.v1.GetGreetingsByNameRequest;
import org.patinanetwork.grpc.helloworld.v1.GreeterServiceGrpc;
import org.patinanetwork.grpc.helloworld.v1.SayGreetingRequest;

public final class GreetingsController {
    public void send(HttpExchange exchange, GreeterServiceGrpc.GreeterServiceBlockingStub stub) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].equals("application/json")) {
            throw new ValidationException(415, "application/json required");
        }
        byte[] body = exchange.getRequestBody().readNBytes(SayGreetingBody.MAX_BYTES + 1);
        if (body.length > SayGreetingBody.MAX_BYTES) {
            throw new ValidationException(413, "request body too large");
        }
        SayGreetingBody input = SayGreetingBody.parse(body);
        Responses.reply(
                exchange,
                stub.sayGreeting(SayGreetingRequest.newBuilder()
                        .setSenderName(input.getSenderName())
                        .setRecipientName(input.getRecipientName())
                        .setGreeting(input.getGreeting())
                        .build()));
    }

    public void list(HttpExchange exchange, GreeterServiceGrpc.GreeterServiceBlockingStub stub) throws IOException {
        Responses.requireMethod(exchange, "GET", "POST");
        var request = GetGreetingsByNameRequest.newBuilder();
        Map<String, String> params = Responses.query(exchange);
        if (params.containsKey("recipientName")) {
            String name = params.get("recipientName");
            if (name.getBytes(StandardCharsets.UTF_8).length > 256) {
                throw new ValidationException(400, "recipientName exceeds 256 UTF-8 bytes");
            }
            request.setRecipientName(name);
        }
        Responses.reply(exchange, stub.getGreetingsByName(request.build()));
    }
}
