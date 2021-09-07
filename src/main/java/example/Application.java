package example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.definition.BaseDefinition;
import example.definition.ComplexDefinition;
import example.repository.DefinitionRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class Application {

    public static String[] supportedEntities = new String[]{"Money", "Coding", "CodeableConcept", "Quantity"};

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
            DefinitionRepository.populateStructureDefinitions(root);
            DefinitionRepository.populatePrimitiveDefinitions(root);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        // This is for testing purposes
        for (String supportedEntity : supportedEntities) {
            convertComplexDefinition(DefinitionRepository.getComplexDefinitionByIdentifier(supportedEntity));
            saveDefinition(DefinitionRepository.getComplexDefinitionByIdentifier(supportedEntity));
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
