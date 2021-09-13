import ca.uhn.fhir.context.FhirContext;
import fixture.AccountFixture;
import fixture.AppointmentFixture;
import fixture.PatientFixture;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class SchemaTest {

    private static FhirContext fhirContext;

    @BeforeClass
    public static void initialize() {
        fhirContext = FhirContext.forR4();
    }

    @Test
    public void test_serialize_patient() throws IOException {
        assertBaseResource("Patient", PatientFixture.createPatient());
    }

    @Test
    public void test_serialize_appointment() throws IOException {
        assertBaseResource("Appointment", AppointmentFixture.createAppointment());
    }

    @Test
    public void test_serialize_account() throws IOException {
        assertBaseResource("Account", AccountFixture.createAccount());
    }

    private void assertBaseResource(String name, IBaseResource baseResource) throws IOException {
        Schema schema = loadSchema(name.toLowerCase() + ".avsc");
        GenericRecord input = convertToRecord(baseResource, schema);
        File file = serializeGenericRecord(schema, name, input);
        GenericRecord output = deserializeGenericRecord(schema, file);
        assertEquals(input, output);

        System.out.println(output);
    }

    private String convertToJson(IBaseResource baseResource) {
        return fhirContext.newJsonParser()
                .encodeResourceToString(baseResource);
    }

    private GenericRecord convertToRecord(IBaseResource baseResource, Schema schema) {
        byte[] bytes = convertToJson(baseResource).getBytes();
        return new JsonAvroConverter().convertToGenericDataRecord(bytes, schema);
    }

    private <K> K deserializeGenericRecord(Schema schema, File file) throws IOException {
        DatumReader<K> userDatumReader = new GenericDatumReader<>(schema);
        DataFileReader<K> dataFileReader = new DataFileReader<>(file, userDatumReader);
        K data = null;
        while (dataFileReader.hasNext()) {
            data = dataFileReader.next(data);
        }
        return data;
    }

    private File serializeGenericRecord(Schema schema, String name, GenericRecord genericRecord) {
        try {
            File file = new File("./results/" + name + ".avro");
            DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(new GenericDatumWriter<>(schema));
            dataFileWriter.create(schema, file);
            dataFileWriter.append(genericRecord);
            dataFileWriter.close();
            return file;
        } catch (IOException ex) {
            throw new RuntimeException("The following file couldn't be saved at ./results/: " + name);
        }
    }

    private Schema loadSchema(String schema) {
        URL resource = ClassLoader.getSystemClassLoader().getResource(schema);
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        }
        try {
            return new Schema.Parser().parse(new File(resource.toURI()));
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}
