package org.patinanetwork.clients.api.echo;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import org.patinanetwork.clients.common.Responses;
import org.patinanetwork.grpc.helloworld.v1.EchoHelloRequest;
import org.patinanetwork.grpc.helloworld.v1.GreeterServiceGrpc;

public final class EchoController {
    public void handle(HttpExchange exchange, GreeterServiceGrpc.GreeterServiceBlockingStub stub) throws IOException {
        Responses.requireMethod(exchange, "GET");
        String name = Responses.query(exchange).getOrDefault("name", "");
        Responses.validate(name, 256, "name");
        Responses.reply(
                exchange,
                stub.echoHello(EchoHelloRequest.newBuilder().setName(name).build()));
    }
}
