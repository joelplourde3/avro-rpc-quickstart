import ca.uhn.fhir.context.FhirContext;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Test;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class SchemaTest {

    @Test
    public void test_serialize_patient() throws IOException {
        Patient patient = new Patient();
        patient.setActive(true);
        patient.setMultipleBirth(new BooleanType(true));
        patient.addName().setFamily("Simpson").addGiven("Homer");

        Schema schema = loadSchema("patient.avsc");
        GenericRecord input = convertToRecord(patient, schema);
        File file = serializeGenericRecord(schema, "Patient", input);
        GenericRecord output = deserializeGenericRecord(schema, file);
        assertEquals(input, output);

        System.out.println(output);
    }

    @Test
    public void test_serialize_appointment() throws IOException {
        Appointment appointment = new Appointment();
        appointment.setDescription("This is a very good description");
        appointment.setPriority(42);
        appointment.setStart(new Date());

        Schema schema = loadSchema("appointment.avsc");
        GenericRecord input = convertToRecord(appointment, schema);
        File file = serializeGenericRecord(schema, "Appointment", input);
        GenericRecord output = deserializeGenericRecord(schema, file);
        assertEquals(input, output);

        System.out.println(output);
    }

    private String convertToJson(IBaseResource baseResource) {
        return FhirContext.forR4()
                .newJsonParser()
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
