package example.parser;

import example.definition.Property;
import example.repository.DefinitionRepository;

import javax.json.JsonObject;

public class ReferenceParser implements IParser {

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has("$ref");
    }

    @Override
    public JsonObject parseField(String identifier, Property property) {
        return DefinitionRepository.getReferenceObject(property.getJsonNode(), identifier, property.isRequired());
    }
}
