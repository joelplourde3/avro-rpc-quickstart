package example.parser;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.JsonObject;
import java.util.*;

public class ParserServant {

    public static final List<IParser> parsers = Collections.unmodifiableList(new ArrayList<IParser>() {{
        add(new ReferenceParser());
        add(new ArrayParser());
        add(new EnumParser());
        add(new TypeParser());
    }});

    public static JsonObject parseField(String identifier, JsonNode node) {
        JsonObject jsonObject = null;
        for (IParser fieldParser : parsers) {
            if (fieldParser.canParse(node)) {
                jsonObject = fieldParser.parseField(identifier, node);
                break;
            }
        }

        if (jsonObject == null) {
            throw new RuntimeException("We do not know how to parse this node yet.");
        }

        return jsonObject;
    }
}
