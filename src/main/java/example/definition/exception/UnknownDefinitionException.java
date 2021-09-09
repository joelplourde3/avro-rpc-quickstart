package example.definition.exception;

public class UnknownDefinitionException extends RuntimeException {

    public UnknownDefinitionException(String identifier) {
        super("Unknown definition, please verify: " + identifier);
    }
}
