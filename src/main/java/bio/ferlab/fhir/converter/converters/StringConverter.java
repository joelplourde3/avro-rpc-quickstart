package bio.ferlab.fhir.converter.converters;

public class StringConverter<T> extends PrimitiveConverter<T> {

    private final T dataType;

    public StringConverter(T dataType) {
        super("String");
        this.dataType = dataType;
    }

    @Override
    public T getDataType() {
        return null;
    }
}
