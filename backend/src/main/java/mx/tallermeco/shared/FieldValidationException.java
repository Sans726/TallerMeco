package mx.tallermeco.shared;
import java.util.Map;
public class FieldValidationException extends RuntimeException {
    private final Map<String,String> fields;
    public FieldValidationException(String field,String message) { super(message); fields=Map.of(field,message); }
    public Map<String,String> fields() { return fields; }
}
