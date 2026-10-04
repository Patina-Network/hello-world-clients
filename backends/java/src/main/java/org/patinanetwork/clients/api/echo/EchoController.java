package org.patinanetwork.clients.api.echo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.patinanetwork.clients.common.Responses;
import org.patinanetwork.clients.config.GrpcClient;
import org.patinanetwork.grpc.helloworld.v1.EchoHelloRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public final class EchoController {
    private final GrpcClient client;

    public EchoController(GrpcClient client) {
        this.client = client;
    }

    @GetMapping("/api/echo")
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var stub = client.stub();
        String name = Responses.query(request).getOrDefault("name", "");
        Responses.validate(name, 256, "name");
        Responses.reply(
                response,
                stub.echoHello(EchoHelloRequest.newBuilder().setName(name).build()));
    }
}
