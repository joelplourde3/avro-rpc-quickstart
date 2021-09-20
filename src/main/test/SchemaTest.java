import bio.ferlab.fhir.converter.AvroFhirConverter;
import bio.ferlab.fhir.converter.FhirAvroConverter;
import ca.uhn.fhir.context.FhirContext;
import fixture.*;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class SchemaTest {

    @Test
    public void test_serialize_patient() {
        assertBaseResource("Patient", PatientFixture.createPatient(), Patient.class);
    }

    @Test
    public void test_serialize_appointment() {
        assertBaseResource("Appointment", AppointmentFixture.createAppointment(), Appointment.class);
    }

    @Test
    public void test_serialize_account() {
        assertBaseResource("Account", AccountFixture.createAccount(), Account.class);
    }

    // Not working because one of the symbols ("<") does not respect the Avro naming convention
    @Test
    public void test_serialize_effectEvidenceSynthesis() {
        assertBaseResource("EffectEvidenceSynthesis", EffectEvidenceSynthesisFixture.createEffectEvidenceSynthesis(), EffectEvidenceSynthesis.class);
    }

    // Not working because one of the symbols ("text/cql") does not respect the Avro naming convention
    @Test
    public void test_serialize_eventDefinition() {
        assertBaseResource("EventDefinition", EventDefinitionFixture.createEventDefinition(), EventDefinition.class);
    }

    // Not working because one of the symbols ("<" located in comparator of Quantity) does not respect the Avro naming convention
    @Test
    public void test_serialize_evidenceVariable() {
        assertBaseResource("EvidenceVariable", EvidenceVariableFixture.createEvidenceVariable(), EvidenceVariable.class);
    }

    private <T extends BaseResource> void assertBaseResource(String name, BaseResource baseResource, Class<T> type) {
        Schema schema = loadSchema(name.toLowerCase() + ".avsc");

        GenericRecord input = FhirAvroConverter.readResource(baseResource, schema);
        File file = serializeGenericRecord(schema, name, input);

        GenericRecord output = null;
        try {
            output = deserializeGenericRecord(schema, file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        T result = AvroFhirConverter.readGenericRecord(output, type, schema);

        String inputString = FhirContext.forR4().newJsonParser().encodeResourceToString(baseResource);
        String outputString = FhirContext.forR4().newJsonParser().encodeResourceToString(result);

        assertEquals(inputString, outputString);
    }

    public static Schema loadSchema(String schema) {
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

    public static File serializeGenericRecord(Schema schema, String name, GenericRecord genericRecord) {
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

    public static <K> K deserializeGenericRecord(Schema schema, File file) throws IOException {
        DatumReader<K> userDatumReader = new GenericDatumReader<>(schema);
        DataFileReader<K> dataFileReader = new DataFileReader<>(file, userDatumReader);
        K data = null;
        while (dataFileReader.hasNext()) {
            data = dataFileReader.next(data);
        }
        return data;
    }
}
