package org.patinanetwork.clients.utilities.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.patinanetwork.clients.common.Responses;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ControllerExceptionHandler {
    private static final System.Logger LOG = System.getLogger(ControllerExceptionHandler.class.getName());

    @ExceptionHandler(Exception.class)
    public void handle(Exception exception, HttpServletResponse response) throws IOException {
        if (exception instanceof HttpRequestMethodNotSupportedException method) {
            response.setHeader("Allow", String.join(", ", method.getSupportedMethods()));
            Responses.error(response, 405, "method not allowed");
            return;
        }
        if (exception instanceof StatusRuntimeException statusException) {
            Status.Code code = statusException.getStatus().getCode();
            LOG.log(System.Logger.Level.WARNING, "gRPC request failed: {0}", code);
            Failure failure = grpcError(code);
            Responses.error(response, failure.status(), failure.message());
            return;
        }
        if (exception instanceof ValidationException validation) {
            Responses.error(response, validation.status(), validation.getMessage());
            return;
        }
        if (exception instanceof IllegalArgumentException) {
            Responses.error(response, 400, "invalid request");
            return;
        }
        LOG.log(System.Logger.Level.ERROR, "HTTP request failed", exception);
        Responses.error(response, 500, "internal error");
    }

    private static Failure grpcError(Status.Code code) {
        return switch (code) {
            case INVALID_ARGUMENT -> new Failure(400, "invalid request");
            case NOT_FOUND -> new Failure(404, "not found");
            case ALREADY_EXISTS -> new Failure(409, "already exists");
            case UNAUTHENTICATED -> new Failure(401, "authentication required");
            case PERMISSION_DENIED -> new Failure(403, "permission denied");
            case RESOURCE_EXHAUSTED -> new Failure(429, "resource exhausted");
            case UNAVAILABLE -> new Failure(503, "service unavailable");
            case DEADLINE_EXCEEDED, CANCELLED -> new Failure(504, "upstream timeout");
            default -> new Failure(502, "upstream request failed");
        };
    }

    private record Failure(int status, String message) {}
}
