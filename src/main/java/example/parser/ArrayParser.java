package example.parser;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArrayParser implements IParser {

    private static final List<IParser> innerParser = Collections.unmodifiableList(new ArrayList<IParser>() {{
        add(new ReferenceParser());
        add(new EnumParser());
    }});

    @Override
    public boolean canParse(JsonNode node) {
        return node.has("items");
    }

    @Override
    public JsonObject parseField(String identifier, JsonNode node) {
        JsonNode root = node.get("items");
        JsonObject jsonObject = null;
        for (IParser fieldParser : innerParser) {
            if (fieldParser.canParse(root)) {
                jsonObject = fieldParser.parseField(identifier, root);
                break;
            }
        }

        if (jsonObject == null) {
            throw new RuntimeException("We do not know how to parse this node yet.");
        }

        return jsonObject;
    }
}
