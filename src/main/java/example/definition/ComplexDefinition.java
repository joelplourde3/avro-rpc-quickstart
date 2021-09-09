package example.definition;

import com.fasterxml.jackson.databind.JsonNode;
import example.parser.ParserServant;
import example.utils.Constant;
import example.utils.JsonObjectUtils;

import javax.json.*;
import java.util.*;

public class ComplexDefinition extends BaseDefinition {

    private final Map<String, Property> properties = new HashMap<>();
    private final List<String> requiredProperties = new ArrayList<>();

    private static final List<String> unsupportedProperties = new ArrayList<String>() {{
        add("id");
        add("extension");
        add("modifierExtension");
    }};

    public ComplexDefinition(Map.Entry<String, JsonNode> entry, JsonNode definition) {
        super(entry.getKey(), entry.getKey(), entry.getValue());
        setDefinition(definition);
    }

    public void generateProperties() {
        if (getDefinition().has(Constant.REQUIRED)) {
            getDefinition().get(Constant.REQUIRED).forEach(x -> requiredProperties.add(x.toString().replace("\"", "")));
        }

        if (getDefinition().has(Constant.DESCRIPTION)) {
            setDescription(getDefinition().get(Constant.DESCRIPTION).asText());
        }

        for (Iterator<Map.Entry<String, JsonNode>> it = getDefinition().get(Constant.PROPERTIES).fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> node = it.next();

            if (node.getKey().contains("_") || unsupportedProperties.contains(node.getKey())) {
                continue;
            }

            properties.put(node.getKey(), new Property(node.getValue(), requiredProperties.contains(node.getKey())));
        }
    }

    public JsonObject convertToJson(String name, boolean required) {
        if (properties.isEmpty()) {
            generateProperties();
        }

        JsonArrayBuilder fields = Json.createArrayBuilder();
        for (Map.Entry<String, Property> node : properties.entrySet()) {
            fields.add(ParserServant.parseField(node.getKey(), node.getValue()));
        }

        // If its an inner field, capitalize the first letter. (to be discussed)
        if (getIdentifier().equalsIgnoreCase(name)) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        // TODO check this to see if we could simply re-use the JsonObject but change its name.
        setJsonObject(JsonObjectUtils.createRecord(name, getDescription(), fields.build(), false));

        return getJsonObject();
    }

    public Map<String, Property> getProperties() {
        return properties;
    }
}
