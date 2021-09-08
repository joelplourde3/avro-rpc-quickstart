package example.parser;

import example.definition.Property;
import example.repository.DefinitionRepository;
import example.utils.ConverterUtils;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;

public class TypeParser implements IParser {

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has("type");
    }

    @Override
    public JsonObject parseField(String identifier, Property property) {
        String reference = ConverterUtils.parsePrimitiveType(property.getJsonNode().get("type").toString());
        return JsonObjectUtils.createField(identifier, DefinitionRepository.getPrimitiveDefinitionByIdentifier(reference).convertToJson(identifier, property.isRequired()), property.isRequired());
    }
}
