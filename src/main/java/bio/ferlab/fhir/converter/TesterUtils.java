package bio.ferlab.fhir.converter;

import bio.ferlab.fhir.converter.exception.UnionTypeException;
import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.util.TerserUtilHelper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.text.WordUtils;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class TesterUtils {

    private FhirContext fhirContext;
    private RuntimeResourceDefinition resourceDefinition;

    public <T extends BaseResource> T readGenericRecord(GenericRecord genericRecord, Class<T> type, Schema schema) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        T instance = type.getDeclaredConstructor().newInstance();

        fhirContext = FhirContext.forR4();
        resourceDefinition = fhirContext.getResourceDefinition(instance);
        TerserUtilHelper helper = TerserUtilHelper.newHelper(fhirContext, resourceDefinition.getName());

        read(helper, schema, genericRecord, new ArrayDeque<>());

        return helper.getResource();

//        TerserUtilHelper helper = TerserUtilHelper.newHelper(fhirContext, "Patient");
//        helper.setField("identifier.system", "http://org.com/sys");
//        helper.setField("identifier.value", "123");
//
//        Patient patient = helper.getResource();

//         read(instance, null, schema, genericRecord);
    }

//    public <T extends Type> T instantiateType(String name) {
//        try {
//            return resourceDefinition.getChildByName(name).getClass().getDeclaredConstructor().newInstance();
//        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e ) {
//            e.printStackTrace();
//        }
//    }

    public <T extends BaseResource> Object read(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {

        switch (schema.getType()) {
            case RECORD:
                return readRecord(helper, schema, value, path);
            case ENUM:
                break;
            case ARRAY:
                return readArray(helper, schema, value, path);
            case MAP:
                break;
            case UNION:
                return readUnion(helper, schema, value, path);
            case FIXED:
                break;
            case STRING:
                break;
            case BYTES:
                break;
            case INT:
                break;
            case LONG:
                readLong(helper, schema, value, path);
                break;
            case FLOAT:
                break;
            case DOUBLE:
                break;
            case BOOLEAN:
                break;
            case NULL:
                if (value == null) {
                    return null;
                }
                throw new UnionTypeException();
        }
        return null;
    }

    public <T extends BaseResource> Object readRecord(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {
        GenericRecord genericRecord = (GenericRecord) value;
        for (Schema.Field innerField : schema.getFields()) {
            Object object = genericRecord.get(innerField.name());
            if (object != null) {
                path.add(innerField.name());
                Object result = read(helper, innerField.schema(), object, path);

                System.out.println("got the result: " + result);
            }
        }
        return null;
    }

    public <T extends BaseResource> List<Object> readArray(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {
        GenericArray genericArray = (GenericArray) value;

        List<Object> objects = new ArrayList<>();
        genericArray.forEach(x -> {
            Object result = read(helper, schema.getElementType(), x, path);
            objects.add(result);
        });

        return objects;
    }

    public <T extends BaseResource> Object readUnion(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {

        for (Schema type : schema.getTypes()) {
            try {
                Object object = read(helper, type, value, path);
                if (object != null) {
                    System.out.println("Object: " + object);
                }
            } catch (UnionTypeException ignored) {}
        }
        return null;
    }

    public <T extends BaseResource> void readLong(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {
        switch (schema.getLogicalType().getName()) {
            case "time-micros":
                // TODO finish this conversion.
                String absolutePath = getAbsolutePath(path);

                helper.setField(absolutePath, "2015-02-07T13:28:17-05:00");
                // TODO finish this conversion.
                return;
            default:
                throw new IllegalStateException("Unexpected value: " + schema.getLogicalType());
        }
    }

    private static String getAbsolutePath(Deque<String> path) {
        String absolutePath = "";
        Iterator itr = path.iterator();

        if (itr.hasNext()) {
            absolutePath += itr.next().toString().toLowerCase();
        }

        while(itr.hasNext()) {
            absolutePath = absolutePath + "." + itr.next().toString().toLowerCase();
        }
        return absolutePath;
    }
}
