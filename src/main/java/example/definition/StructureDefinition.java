package example.definition;

import com.fasterxml.jackson.databind.JsonNode;

public class StructureDefinition extends BaseDefinition {

    public StructureDefinition(String name, String identifier, JsonNode definition) {
        super(name, identifier, definition);
    }

    @Override
    public String toString() {
        return "StructureDefinition{" +
                "name='" + getName() + '\'' +
                ", identifier='" + getIdentifier() + '\'' +
                ", definition=" + getDefinition().toPrettyString() +
                '}';
    }
}
