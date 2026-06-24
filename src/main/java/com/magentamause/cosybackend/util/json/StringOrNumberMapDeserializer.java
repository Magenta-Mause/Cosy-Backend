package com.magentamause.cosybackend.util.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jackson deserializer for a {@code Map<String, String>} whose VALUES are string-or-number scalars.
 *
 * <p>Used for the v3 {@code port_mapping} field, where each value is either an integer (e.g. {@code
 * 25565}) or an unresolved {@code "{{var}}"} placeholder string. Each value is stored in its
 * canonical {@link String} form (integral numbers without a trailing {@code .0}).
 */
public class StringOrNumberMapDeserializer extends JsonDeserializer<Map<String, String>> {

    @Override
    public Map<String, String> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), canonicalize(entry.getValue()));
        }
        return result;
    }

    private String canonicalize(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        if (value.isNumber()) {
            double d = value.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return Long.toString((long) d);
            }
            return value.decimalValue().stripTrailingZeros().toPlainString();
        }
        return value.asText();
    }
}
