import bio.ferlab.fhir.converter.FhirAvroConverter;
import bio.ferlab.fhir.converter.TesterUtils;
import bio.ferlab.fhir.schema.ExtendedJsonParser;
import fixture.*;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BaseResource;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class SchemaTest {

    @Test
    public void test_serialize_patient() {
        assertBaseResource("Patient", PatientFixture.createPatient());
    }

    @Test
    public void test_serialize_appointment() {
        assertBaseResource("Appointment", AppointmentFixture.createAppointment());
    }

    @Test
    public void test_serialize_account() {
        assertBaseResource("Account", AccountFixture.createAccount());
    }

    // Not working because one of the symbols ("<") does not respect the Avro naming convention
    @Test
    public void test_serialize_effectEvidenceSynthesis() {
        assertBaseResource("EffectEvidenceSynthesis", EffectEvidenceSynthesisFixture.createEffectEvidenceSynthesis());
    }

    // Not working because one of the symbols ("text/cql") does not respect the Avro naming convention
    @Test
    public void test_serialize_eventDefinition() {
        assertBaseResource("EventDefinition", EventDefinitionFixture.createEventDefinition());
    }

    // Not working because one of the symbols ("<" located in comparator of Quantity) does not respect the Avro naming convention
    @Test
    public void test_serialize_evidenceVariable() {
        assertBaseResource("EvidenceVariable", EvidenceVariableFixture.createEvidenceVariable());
    }

    private void assertBaseResource(String name, BaseResource baseResource) {
        Schema schema = loadSchema(name.toLowerCase() + ".avsc");

        GenericRecord input = FhirAvroConverter.readResource(baseResource, schema);
        File file = serializeGenericRecord(schema, name, input);

        try {
            GenericRecord output = deserializeGenericRecord(schema, file);
            assertEquals(input, output);

//            System.out.println("Input: " + input);
//            System.out.println("Output: " + output);
            new TesterUtils().readGenericRecord(output, Patient.class, schema);

            // TesterUtils.readGenericRecord(output, Patient.class, schema);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
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

    // Unused.
    // That is the converter to convert to Json then to Avro.
    private GenericRecord convertToRecord(IBaseResource baseResource, Schema schema) {
        String jsonString = new ExtendedJsonParser().encodeResourceToString(baseResource);
        System.out.println(jsonString);
        byte[] bytes = jsonString.getBytes();
        return new JsonAvroConverter().convertToGenericDataRecord(bytes, schema); // <-- This does not fully convert all fields correctly.
    }
}
