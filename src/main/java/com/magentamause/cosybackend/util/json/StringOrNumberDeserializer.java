package com.magentamause.cosybackend.util.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * Jackson deserializer that accepts a JSON value which may be either a string or a number and
 * stores the canonical {@link String} form.
 *
 * <p>The v3 template wire contract emits several fields as string-or-number scalars so they can
 * carry either a concrete value (e.g. a numeric port {@code 25565} or a memory limit {@code 2GiB})
 * or an unresolved {@code {{var}}} placeholder string. Numeric values are rendered without a
 * trailing {@code .0} for integral values (e.g. {@code 2} not {@code 2.0}) so the stored form
 * matches what the Go template-service emits.
 *
 * <p>Applies to: {@code game_id}, {@code resource_limit.cpu}, {@code resource_limit.memory} and
 * every {@code port_mapping} value.
 */
public class StringOrNumberDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isIntegralNumber()) {
            return node.bigIntegerValue().toString();
        }
        if (node.isNumber()) {
            double value = node.doubleValue();
            if (value == Math.rint(value) && !Double.isInfinite(value)) {
                return Long.toString((long) value);
            }
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return node.asText();
    }
}
