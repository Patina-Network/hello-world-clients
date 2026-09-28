package org.patinanetwork.clients.api.greetings.body;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.patinanetwork.clients.common.Responses;
import org.patinanetwork.clients.utilities.exception.ValidationException;

public final class SayGreetingBody {
    public static final int MAX_BYTES = 16 * 1024;

    private static final Set<String> FIELDS = Set.of("senderName", "recipientName", "greeting");

    private final String senderName;
    private final String recipientName;
    private final String greeting;

    private SayGreetingBody(String senderName, String recipientName, String greeting) {
        this.senderName = senderName;
        this.recipientName = recipientName;
        this.greeting = greeting;
    }

    public static SayGreetingBody parse(byte[] body) {
        JsonNode input;
        try {
            input = Responses.JSON.readTree(body);
        } catch (Exception e) {
            throw new ValidationException(400, "invalid JSON request");
        }
        if (input == null || !input.isObject()) {
            throw new ValidationException(400, "JSON object required");
        }
        input.fieldNames().forEachRemaining(key -> {
            if (!FIELDS.contains(key)) {
                throw new ValidationException(400, "unknown field");
            }
        });
        return new SayGreetingBody(
                field(input, "senderName", 256), field(input, "recipientName", 256), field(input, "greeting", 4096));
    }

    public String getSenderName() {
        return senderName;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getGreeting() {
        return greeting;
    }

    private static String field(JsonNode input, String name, int max) {
        JsonNode node = input.get(name);
        if (node == null || !node.isTextual()) {
            throw new ValidationException(400, name + " must be a string");
        }
        String value = node.textValue();
        Responses.validate(value, max, name);
        return value;
    }
}
