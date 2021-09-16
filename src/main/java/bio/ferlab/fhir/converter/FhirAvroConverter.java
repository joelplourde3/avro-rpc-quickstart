package bio.ferlab.fhir.converter;

import bio.ferlab.fhir.converter.exception.AvroConversionException;
import bio.ferlab.fhir.converter.exception.UnionTypeException;
import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.commons.text.WordUtils;
import org.hl7.fhir.r4.model.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public class FhirAvroConverter {

    public static GenericData.Record readResource(BaseResource baseResource, Schema schema) {
        Object object = FhirAvroConverter.read(schema, Arrays.asList(baseResource));
        GenericData.Record genericRecord = (GenericData.Record) object;
        genericRecord.put("resourceType", schema.getName());
        return genericRecord;
    }

    private static Object read(Schema schema, List<Base> bases) {
        switch (schema.getType()) {
            case RECORD:
                return readRecord(schema, bases);
            case ENUM:
                return ensureEnum(schema, bases);
            case ARRAY:
                return readArray(schema, bases);
            case UNION:
                return readUnion(schema, bases);
            case INT:
                return FhirAvroConverter.onValidType(bases, Integer::valueOf);
            case LONG:
                return FhirAvroConverter.onValidType(bases, Long::valueOf);
            case FLOAT:
                return FhirAvroConverter.onValidType(bases, Float::valueOf);
            case DOUBLE:
                return FhirAvroConverter.onValidType(bases, Double::valueOf);
            case BOOLEAN:
                return FhirAvroConverter.onValidType(bases, Boolean::parseBoolean);
            case STRING:
                return FhirAvroConverter.onValidType(bases, string -> string);
            case BYTES:
                return FhirAvroConverter.onValidType(bases, FhirAvroConverter::bytesForString);
            case NULL:
                break;
            default:
                throw new AvroTypeException("Unsupported type: " + schema.getType());
        }
        return null;
    }

    private static Object readRecord(Schema schema, List<Base> bases) {
        GenericRecordBuilder recordBuilder = new GenericRecordBuilder(schema);

        for (Base base : bases) {
            for (Schema.Field field : schema.getFields()) {
                Property property = base.getNamedProperty(FhirAvroConverter.uncapitalized(field.name()));
                if (property != null) {
                    recordBuilder.set(field.name(), read(field.schema(), property.getValues()));
                }
            }
        }

        try {
            recordBuilder.set("resourceType", schema.getName());
        } catch (Exception ignored) {
        }

        return recordBuilder.build();
    }

    private static List<Object> readArray(Schema schema, List<Base> bases) {
        List<Object> objects = new ArrayList<>();

        for (Base base : bases) {
            objects.add(read(schema.getElementType(), Collections.singletonList(base)));
        }

        return objects;
    }

    private static Object readUnion(Schema schema, List<Base> bases) {
        for (Base base : bases) {
            for (Schema type : schema.getTypes()) {
                try {
                    Object unionValue = FhirAvroConverter.read(type, Collections.singletonList(base));
                    if (unionValue != null) {
                        return unionValue;
                    }
                } catch (UnionTypeException ignored) {}
            }
        }
        return null;
    }

    private static <T> Object onValidType(List<Base> bases, Function<String, T> function) {
        Base base = Optional.ofNullable(bases.get(0)).orElseThrow(() -> new RuntimeException("Please verify this, this isn't suppose to occur."));
        String value = formatPrimitiveValue(base.primitiveValue());
        try {
            return function.apply(value);
        } catch (Exception ex) {
            throw new UnionTypeException();
        }
    }

    // TODO Move this somewhere else.
    private static String formatPrimitiveValue(String value) {
        if (Pattern.compile("^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$")
                .matcher(value)
                .matches()) {
            return Long.toString(parseDate(value, "yyyy-MM-dd")
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toEpochDay());
        }

        if (Pattern.compile("^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\\\.[0-9]+)?(Z|(\\\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?$").
                matcher(value)
                .matches()) {
            return Long.toString(parseDate(value, "yyyy-MM-dd'T'HH:mm:ssXXX")
                    .toInstant()
                    .getEpochSecond());
        }

        if (Pattern.compile("^-?(0|[1-9][0-9]*)(\\\\.[0-9]+)?([eE][+-]?[0-9]+)?$")
                .matcher(value)
                .matches()) {
            System.out.println("Decimal!");
        }

        return value;
    }

    private static Object ensureEnum(Schema schema, List<Base> bases) {
        Base base = Optional.ofNullable(bases.get(0)).orElseThrow(() -> new RuntimeException("Please verify this, this isn't suppose to occur."));
        List<String> symbols = schema.getEnumSymbols();
        if (symbols.contains(base.primitiveValue())) {
            return new GenericData.EnumSymbol(schema, base.primitiveValue());
        } else {
            throw new AvroConversionException(String.format("value: %s was not found within Symbols: %s", base.primitiveValue(), symbols));
        }
    }

    private static ByteBuffer bytesForString(String string) {
        return ByteBuffer.wrap(string.getBytes(StandardCharsets.UTF_8));
    }

    private static String uncapitalized(String string) {
        return WordUtils.uncapitalize(string);
    }

    private static Date parseDate(String date, String format) {
        try {
            return new SimpleDateFormat(format).parse(date);
        } catch (ParseException parseException) {
            throw new AvroConversionException(String.format("value: %s is unparseable according to format: %s", date, format));
        }
    }
}