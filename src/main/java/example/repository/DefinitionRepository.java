package example.repository;

import com.fasterxml.jackson.databind.JsonNode;
import example.Application;
import example.definition.ComplexDefinition;
import example.definition.PrimitiveDefinition;
import example.definition.StructureDefinition;
import example.utils.ConverterUtils;

import javax.json.JsonObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DefinitionRepository {

    private static final Map<String, StructureDefinition> structureDefinitions = new HashMap<>();

    private static final Map<String, PrimitiveDefinition> primitiveDefinitions = new HashMap<>();

    private static final Map<String, ComplexDefinition> complexDefinitions = new HashMap<>();

    public static void populateStructureDefinitions(JsonNode root) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get("discriminator").get("mapping").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            String identifier = entry.getValue().asText();
            JsonNode definition = root.get("definitions").get(name);
            StructureDefinition structureDefinition = new StructureDefinition(name, identifier, definition);
            structureDefinitions.put(identifier, structureDefinition);
        }
    }

    public static void populatePrimitiveDefinitions(JsonNode root) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get("definitions").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();

            if (entry.getValue().has("type")) {
                PrimitiveDefinition primitiveDefinition = new PrimitiveDefinition(entry);
                primitiveDefinitions.put(primitiveDefinition.getIdentifier(), primitiveDefinition);
            } else {
                if ("xhtml".equalsIgnoreCase(entry.getKey())) {
                    PrimitiveDefinition primitiveDefinition = new PrimitiveDefinition(entry.getKey(), entry.getKey(), entry.getValue());
                    primitiveDefinitions.put(primitiveDefinition.getIdentifier(), primitiveDefinition);
                    continue;
                }

                if (entry.getValue().has("oneOf")) {
                    continue;
                }

                if (entry.getValue().has("properties")) {
                    ComplexDefinition complexDefinition = new ComplexDefinition(entry);
                    if (!complexDefinition.getProperties().isEmpty()) {
                        complexDefinitions.put(complexDefinition.getIdentifier(), complexDefinition);
                    }
                }
            }
        }
    }

    public static StructureDefinition getStructureDefinitionByIdentifier(String identifier) {
        if (structureDefinitions.containsKey(identifier)) {
            return structureDefinitions.get(identifier);
        } else {
            throw new RuntimeException("Unknown StructureDefinition, please verify: " + identifier);
        }
    }

    public static ComplexDefinition getComplexDefinitionByIdentifier(String identifier) {
        if (complexDefinitions.containsKey(identifier)) {
            return complexDefinitions.get(identifier);
        } else {
            throw new RuntimeException("Unknown ComplexDefinition, please verify: " + identifier);
        }
    }

    public static PrimitiveDefinition getPrimitiveDefinitionByIdentifier(String identifier) {
        if (primitiveDefinitions.containsKey(identifier)) {
            return primitiveDefinitions.get(identifier);
        } else {
            throw new RuntimeException("Unknown PrimitiveDefinition, please verify: " + identifier);
        }
    }

    public static JsonObject getReferenceObject(JsonNode root, String name) {
        String reference = ConverterUtils.parseReference(root);
        if (primitiveDefinitions.containsKey(reference)) {
            return primitiveDefinitions.get(reference).toJson(name);
        }  else if (complexDefinitions.containsKey(reference)) {
            return complexDefinitions.get(reference).toJson();
        } else {
            throw new RuntimeException("The reference object is not a primitive nor a complex, this behaviour is not yet implemented.");
        }
    }
}
