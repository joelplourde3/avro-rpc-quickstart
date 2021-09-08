package example.repository;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.ComplexDefinition;
import example.definition.IDefinition;
import example.definition.PrimitiveDefinition;
import example.definition.specificity.ExtensionDefinition;
import example.definition.specificity.ReferenceDefinition;
import example.definition.specificity.ResourceListDefinition;
import example.definition.specificity.xHtmlDefinition;
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
            ComplexDefinition complexDefinition = new ComplexDefinition(entry, root.get("definitions").get(entry.getKey()));
            complexDefinitions.put(complexDefinition.getIdentifier(), complexDefinition);
        }
    }

    public static void populatePrimitiveDefinitions(JsonNode root) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get("definitions").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();

            if (entry.getValue().has("type")) {
                PrimitiveDefinition primitiveDefinition = new PrimitiveDefinition(entry);
                primitiveDefinitions.put(primitiveDefinition.getIdentifier(), primitiveDefinition);
            } else {
                // Base definition for all elements in a resource, therefore does not need to explicitely added as a special definition.
                if ("Element".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                // TODO do a factory here for the Tag and the IDefinition.

                // xhtml
                if ("xhtml".equalsIgnoreCase(entry.getKey())) {
                    xHtmlDefinition xHtmlDefinition = new xHtmlDefinition(entry.getKey(), entry.getKey(), entry.getValue());
                    specificDefinitions.put(xHtmlDefinition.getIdentifier(), xHtmlDefinition);
                    continue;
                }

                if ("Reference".equalsIgnoreCase(entry.getKey())) {
                    ReferenceDefinition referenceDefinition = new ReferenceDefinition(entry.getKey(), entry.getKey(), entry.getValue());
                    specificDefinitions.put(referenceDefinition.getIdentifier(), referenceDefinition);
                    continue;
                }

                if ("Extension".equalsIgnoreCase(entry.getKey())) {
                    ExtensionDefinition extensionDefinition = new ExtensionDefinition(entry.getKey(), entry.getKey(), entry.getValue());
                    specificDefinitions.put(extensionDefinition.getIdentifier(), extensionDefinition);
                    continue;
                }

                if ("ResourceList".equalsIgnoreCase(entry.getKey())) {
                    ResourceListDefinition resourceListDefinition = new ResourceListDefinition(entry.getKey(), entry.getKey(), entry.getValue());
                    specificDefinitions.put(resourceListDefinition.getIdentifier(), resourceListDefinition);
                    continue;
                }

                if (entry.getValue().has("properties")) {
                    ComplexDefinition complexDefinition = new ComplexDefinition(entry, entry.getValue());
                    complexDefinition.generateProperties();
                    if (!complexDefinition.getProperties().isEmpty()) {
                        complexDefinitions.put(complexDefinition.getIdentifier(), complexDefinition);
                    }
                }
            }
        }
    }

    public static Map<String, ComplexDefinition> getComplexDefinitions() {
        return complexDefinitions;
    }

    public static ComplexDefinition getComplexDefinitionByIdentifier(String identifier) {
        return Optional.ofNullable(complexDefinitions.get(identifier))
                .orElseThrow(() -> new RuntimeException("Unknown ComplexDefinition, please verify: " + identifier));
    }

    public static PrimitiveDefinition getPrimitiveDefinitionByIdentifier(String identifier) {
        return Optional.ofNullable(primitiveDefinitions.get(identifier))
                .orElseThrow(() -> new RuntimeException("Unknown ComplexDefinition, please verify: " + identifier));
    }

    public static JsonObject getReferenceObject(JsonNode root, String name, boolean required) {
        String reference = ConverterUtils.parseReference(root);
        if (primitiveDefinitions.containsKey(reference)) {
            return primitiveDefinitions.get(reference).convertToJson(name, required);
        } else if (complexDefinitions.containsKey(reference)) {
            return complexDefinitions.get(reference).convertToJson(name, required);
        } else if (specificDefinitions.containsKey(reference)) {
            return specificDefinitions.get(reference).convertToJson(name, required);
        } else {
            throw new RuntimeException("The reference object is not a known object OR this behaviour is not yet implemented by : " + reference);
        }
    }
}
