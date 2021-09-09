package example.repository;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.ComplexDefinition;
import example.definition.IDefinition;
import example.definition.PrimitiveDefinition;
import example.definition.exception.UnknownDefinitionException;
import example.definition.exception.UnknownReferenceException;
import example.definition.specificity.*;
import example.utils.Constant;
import example.utils.ConverterUtils;

import javax.json.JsonObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public class DefinitionRepository {

    private static final Map<String, PrimitiveDefinition> primitiveDefinitions = new HashMap<>();
    private static final Map<String, ComplexDefinition> complexDefinitions = new HashMap<>();
    private static final Map<String, IDefinition> specificDefinitions = new HashMap<>();

    private DefinitionRepository() {}

    public static void populateComplexDefinitions(JsonNode root) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get("discriminator").get("mapping").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            ComplexDefinition complexDefinition = new ComplexDefinition(entry, root.get(Constant.DEFINITIONS).get(entry.getKey()));
            complexDefinitions.put(complexDefinition.getIdentifier(), complexDefinition);
        }
    }

    public static void populatePrimitiveDefinitions(JsonNode root) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get(Constant.DEFINITIONS).fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();

            if (entry.getValue().has(Constant.TYPE)) {
                PrimitiveDefinition primitiveDefinition = new PrimitiveDefinition(entry);
                primitiveDefinitions.put(primitiveDefinition.getIdentifier(), primitiveDefinition);
            } else {
                // Base definition for all elements in a resource, therefore does not need to explicitely added as a special definition.
                if ("Element".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }

                if (SpecificDefinitionFactory.isSupported(entry.getKey())) {
                    SpecificDefinition specialDefinition = SpecificDefinitionFactory.getSpecificDefinition(entry.getKey());
                    specialDefinition.initialize(entry.getKey(), entry.getKey(), entry.getValue());
                    specificDefinitions.put(specialDefinition.getIdentifier(), specialDefinition);
                }

                if (entry.getValue().has(Constant.PROPERTIES)) {
                    ComplexDefinition complexDefinition = new ComplexDefinition(entry, entry.getValue());
                    complexDefinition.generateProperties();
                    if (!complexDefinition.getProperties().isEmpty()) {
                        complexDefinitions.put(complexDefinition.getIdentifier(), complexDefinition);
                    }
                }
            }
        }
    }

    public static ComplexDefinition getComplexDefinitionByIdentifier(String identifier) {
        return Optional.ofNullable(complexDefinitions.get(identifier)).orElseThrow(() -> new UnknownDefinitionException(identifier));
    }

    public static PrimitiveDefinition getPrimitiveDefinitionByIdentifier(String identifier) {
        return Optional.ofNullable(primitiveDefinitions.get(identifier)).orElseThrow(() -> new UnknownDefinitionException(identifier));
    }

    public static JsonObject getReferenceObject(JsonNode node, String name, boolean required) {
        String reference = ConverterUtils.parseReference(node);
        if (primitiveDefinitions.containsKey(reference)) {
            return primitiveDefinitions.get(reference).convertToJson(name, required);
        } else if (complexDefinitions.containsKey(reference)) {
            return complexDefinitions.get(reference).convertToJson(name, required);
        } else if (specificDefinitions.containsKey(reference)) {
            return specificDefinitions.get(reference).convertToJson(name, required);
        } else {
            throw new UnknownReferenceException(reference);
        }
    }

    public static Map<String, ComplexDefinition> getComplexDefinitions() {
        return complexDefinitions;
    }
}
