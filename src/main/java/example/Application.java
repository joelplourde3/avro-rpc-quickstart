package example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.definition.BaseDefinition;
import example.definition.ComplexDefinition;
import example.definition.PrimitiveDefinition;
import example.definition.StructureDefinition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Application {

    public static String[] supportedEntities = new String[] { "Money", "Coding", "CodeableConcept", "Quantity" };

    public static HashMap<String, StructureDefinition> structureDefinitions = new HashMap<>();

    public static HashMap<String, PrimitiveDefinition> primitiveDefinitions = new HashMap<>();

    public static HashMap<String, ComplexDefinition> complexDefinitions = new HashMap<>();

    public static void main(String[] args) throws URISyntaxException {
        URL resource = ClassLoader.getSystemClassLoader().getResource("fhir.schema.json");
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        }

        execute(new File(resource.toURI()));
    }

    public static void execute(File file) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(file);
            populateStructureDefinitions(root);
            populatePrimitiveDefinitions(root);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        // This is for testing purposes
        for (String supportedEntity : supportedEntities) {
            convertComplexDefinition(complexDefinitions.get(supportedEntity));
            saveDefinition(complexDefinitions.get(supportedEntity));
        }
    }

    private static void populateStructureDefinitions(JsonNode root) {
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get("discriminator").get("mapping").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            String identifier = entry.getValue().asText();
            JsonNode definition = root.get("definitions").get(name);
            StructureDefinition structureDefinition = new StructureDefinition(name, identifier, definition);
            structureDefinitions.put(identifier, structureDefinition);
        }
    }

    private static void populatePrimitiveDefinitions(JsonNode root) {
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

    private static void convertComplexDefinition(ComplexDefinition complexDefinition) {
        System.out.println(complexDefinition.getProperties());

        System.out.println(complexDefinition.toJson().toString());

    }

    private static void saveDefinition(BaseDefinition baseDefinition) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./src/resources/" + baseDefinition.getName().toLowerCase() + ".avsc"))) {
            writer.write(baseDefinition.getJsonObject().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
