package bio.ferlab.fhir.schema.utils;

public class Constant {

    private Constant() {}

    public static final String NAMESPACE = "namespace";
    public static final String DEFAULT = "default";
    public static final String TYPE = "type";
    public static final String DOC = "doc";
    public static final String ITEMS = "items";
    public static final String NAME = "name";
    public static final String FIELDS = "fields";
    public static final String SYMBOLS = "symbols";
    public static final String DESCRIPTION = "description";
    public static final String DEFINITIONS = "definitions";
    public static final String REF = "$ref";
    public static final String PROPERTIES = "properties";
    public static final String REQUIRED = "required";
    public static final String LOGICAL_TYPE = "logicalType";
    public static final String PATTERN = "pattern";

    // Avro-specific
    public static final String ENUM = "enum";
    public static final String ARRAY = "array";
    public static final String RECORD = "record";
    public static final String STRING = "string";
    public static final String BYTES = "bytes";
    public static final String DECIMAL = "decimal";
    public static final String INT = "int";
    public static final String LONG = "long";
    public static final String DATE = "date";
    public static final String TIME_MICROS = "time-micros";

    public static final String NULL = "null";

    public static final String NAMESPACE_VALUE = "bio.ferlab.fhir";
}
