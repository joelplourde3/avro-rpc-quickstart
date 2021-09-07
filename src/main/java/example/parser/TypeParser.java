package example.parser;

import com.fasterxml.jackson.databind.JsonNode;
import example.repository.DefinitionRepository;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;

public class TypeParser implements IParser {

    @Override
    public boolean canParse(JsonNode node) {
        return node.has("type");
    }

    @Override
    public JsonObject parseField(String identifier, JsonNode node) {
        String reference = node.get("type").toString();
        return JsonObjectUtils.createField(identifier, DefinitionRepository.getPrimitiveDefinitionByIdentifier(reference).toJson(identifier));
    }
}
