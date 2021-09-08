package example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.repository.DefinitionRepository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class GenerateSchemas {

    public static void main(String[] args) throws URISyntaxException, IOException {
        URL resource = ClassLoader.getSystemClassLoader().getResource("fhir.schema.json");
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        }
        run(new File(resource.toURI()));
    }

    public static void run(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        DefinitionRepository.populatePrimitiveDefinitions(root);
        DefinitionRepository.populateComplexDefinitions(root);

        Tester.loadAll();
    }
}
