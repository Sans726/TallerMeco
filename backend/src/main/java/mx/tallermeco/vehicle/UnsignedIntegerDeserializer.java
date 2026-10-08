package mx.tallermeco.vehicle;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import mx.tallermeco.shared.InputRules;
import java.io.IOException;
/** Preserves lexical numeric input: 2e3 and 12.0 never become integer 2000/12. */
public class UnsignedIntegerDeserializer extends JsonDeserializer<String>{
 @Override public String deserialize(JsonParser p,DeserializationContext c)throws IOException{
  String value=p.getText();if((p.currentToken()!=JsonToken.VALUE_STRING&&p.currentToken()!=JsonToken.VALUE_NUMBER_INT)||(!value.isEmpty()&&!value.matches("[0-9]{1,10}")))InputRules.fail(p.currentName(),"Usa un entero sin signos, decimales ni notación científica");return value;
 }
}
