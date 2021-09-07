package example.parser;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.JsonObject;

public interface IParser {

    boolean canParse(JsonNode node);

    JsonObject parseField(String identifier, JsonNode node);
}
