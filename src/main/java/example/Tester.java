package example;

import example.definition.BaseDefinition;
import example.repository.DefinitionRepository;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// This class is for testing purpose, this shouldn't go anywhere close to production in this form.
public class Tester {

    public static List<String> unsupportedEntities = new ArrayList<>();
    public static List<String> supportedEntities = new ArrayList<>();

    public static void loadAll() {
        String currentKey = "";
        try {
            for (Iterator<String> iterator = DefinitionRepository.getComplexDefinitions().keySet().iterator(); iterator.hasNext(); ) {
                currentKey = iterator.next();
                if (unsupportedEntities.contains(currentKey)) {
                    continue;
                }

                DefinitionRepository.getComplexDefinitionByIdentifier(currentKey).convertToJson(currentKey, currentKey, true);
                saveDefinition(DefinitionRepository.getComplexDefinitionByIdentifier(currentKey));
                supportedEntities.add(currentKey);

                System.out.println("Supported Entity: " + currentKey);
            }
            System.out.println("Supported Entities: " + supportedEntities.size());
            System.out.println("Unsupported Entities: " + unsupportedEntities.size());
        } catch(StackOverflowError stackOverflowError) {
            unsupportedEntities.add(currentKey);
            loadAll();
        }
    }

    public static void loadOne(String identifier) {
        DefinitionRepository.getComplexDefinitionByIdentifier(identifier).convertToJson(identifier, identifier, true);
        saveDefinition(DefinitionRepository.getComplexDefinitionByIdentifier(identifier));
    }

    private static void saveDefinition(BaseDefinition baseDefinition) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./src/resources/" + baseDefinition.getName().toLowerCase() + ".avsc"))) {
            writer.write(baseDefinition.getJsonObject().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
