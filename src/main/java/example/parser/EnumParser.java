package example.parser;

import com.fasterxml.jackson.databind.JsonNode;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;

public class EnumParser implements IParser {

    @Override
    public boolean canParse(JsonNode node) {
        return node.has("enum");
    }

    @Override
    public JsonObject parseField(String identifier, JsonNode node) {
        return JsonObjectUtils.createField(identifier, JsonObjectUtils.createEnum(node));
    }
}
