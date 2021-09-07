package example.parser;

import com.fasterxml.jackson.databind.JsonNode;
import example.repository.DefinitionRepository;

import javax.json.JsonObject;

public class ReferenceParser implements IParser {

    @Override
    public boolean canParse(JsonNode node) {
        return node.has("$ref");
    }

    @Override
    public JsonObject parseField(String identifier, JsonNode node) {
        return DefinitionRepository.getReferenceObject(node, identifier);
    }
}
