package bio.ferlab.fhir.converter;

import bio.ferlab.fhir.converter.exception.AvroConversionException;
import bio.ferlab.fhir.converter.exception.UnionTypeException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.util.TerserUtilHelper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.text.WordUtils;
import org.hl7.fhir.r4.model.BaseResource;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;

public class AvroFhirConverter {

    private AvroFhirConverter() {}

    public static <T extends BaseResource> T readGenericRecord(GenericRecord genericRecord, Class<T> type, Schema schema) {
        T instance;
        try {
            instance = type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException("Something happened .. ?");
        }

        FhirContext fhirContext = FhirContext.forR4();
        RuntimeResourceDefinition resourceDefinition = FhirContext.forR4().getResourceDefinition(instance);
        TerserUtilHelper helper = TerserUtilHelper.newHelper(fhirContext, resourceDefinition.getName());

        read(helper, null, schema, genericRecord, new ArrayDeque<>());

        return helper.getResource();
    }

    protected static void read(TerserUtilHelper helper, Schema.Field field, Schema schema, Object value, Deque<String> path) {
        switch (schema.getType()) {
            case RECORD:
                readRecord(helper, schema, value, path);
                break;
            case ARRAY:
                readArray(helper, field, schema, value, path);
                break;
            case UNION:
                readUnion(helper, field, schema, value, path);
                break;
            case ENUM:
            case BYTES:
            case STRING:
            case FIXED:
            case BOOLEAN:
                readType(helper, value.toString(), path);
                break;
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
                readNumber(helper, schema, value, path);
                break;
            case NULL:
                if (value == null) {
                    return;
                }
                throw new UnionTypeException();
            default:
                throw new AvroConversionException(String.format("The following type is unknown: %s", schema.getType()));
        }
    }

    protected static void readRecord(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {
        GenericRecord genericRecord = (GenericRecord) value;
        for (Schema.Field innerField : schema.getFields()) {
            if ("resourceType".equalsIgnoreCase(innerField.name())) {
                continue;
            }

            Object object = genericRecord.get(innerField.name());
            if (object != null) {
                path.addLast(innerField.name());
                read(helper, innerField, innerField.schema(), object, path);
            }
        }
        if (!path.isEmpty()) {
            path.removeLast();
        }
    }

    protected static void readArray(TerserUtilHelper helper, Schema.Field field, Schema schema, Object value, Deque<String> path) {
        if (!(value instanceof GenericArray)) {
            throw new RuntimeException("Something is wrong, please verify this.");
        }

        ((GenericArray) value).forEach(x -> {
            read(helper, field, schema.getElementType(), x, path);
            path.addLast(field.name());
        });
        path.removeLast();
    }

    protected static void readUnion(TerserUtilHelper helper, Schema.Field field, Schema schema, Object value, Deque<String> path) {
        for (Schema type : schema.getTypes()) {
            try {
                read(helper, field, type, value, path);
            } catch (UnionTypeException ignored) {}
        }
    }

    protected static void readNumber(TerserUtilHelper helper, Schema schema, Object value, Deque<String> path) {
        switch (schema.getLogicalType().getName()) {
            case "time-micros":
                readType(helper, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date((Long) value * 1000)), path);
                return;
            case "date":
                readType(helper, new SimpleDateFormat("yyyy-MM-dd").format(new Date((Integer) value * 86400000L)), path);
                return;
            default:
                readType(helper, value.toString(), path);
        }
    }

    protected static void readType(TerserUtilHelper helper, String value, Deque<String> path) {
        String absolutePath = getAbsolutePath(path);
        helper.setField(absolutePath, value);
        path.removeLast();
    }

    public static String getAbsolutePath(Deque<String> path) {
        StringBuilder absolutePath = new StringBuilder();
        Iterator<String> itr = path.iterator();

        if (itr.hasNext()) {
            absolutePath.append(WordUtils.uncapitalize(itr.next()));
        }

        while (itr.hasNext()) {
            absolutePath.append(".");
            absolutePath.append(WordUtils.uncapitalize(itr.next()));
        }
        return absolutePath.toString();
    }
}
