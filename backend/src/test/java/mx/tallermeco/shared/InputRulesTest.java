package mx.tallermeco.shared;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class InputRulesTest {
 @ParameterizedTest @ValueSource(strings={"0100","010000","01,000","0100A","01000,","12e3","-0100"," 01000","01000 "})
 void postalRejectsMalformed(String value){assertThrows(FieldValidationException.class,()->InputRules.postal(value));}
 @Test void namesAndCanonicalContacts(){
  assertEquals("01000",InputRules.postal("01000"));
  for(String name:new String[]{"María José","Muñoz","O'Neill","Ana-María","D’Ávila","O","李"})assertNotNull(InputRules.name("name",name,50,true));
  assertEquals("maría josé",InputRules.name("name","  MARÍA   JOSÉ  ",50,true));
  for(String name:new String[]{"Ana123","Ana, Pérez","Ana\nPérez","<script>"})assertThrows(FieldValidationException.class,()->InputRules.name("name",name,50,true));
  for(String phone:new String[]{"5512345678","55 1234 5678","+52 55 1234 5678","(55) 1234-5678"})assertEquals("5512345678",InputRules.phone("phone",phone,false));
  for(String phone:new String[]{"123","55123456789","55ABC5678","1e10","55.1234.5678","--5512345678"})assertThrows(FieldValidationException.class,()->InputRules.phone("phone",phone,false));
  assertEquals("ana@example.com",InputRules.email("email"," ANA@example.com ",false));
  for(String email:new String[]{"ana @example.com","a..b@example.com","a@example","a@-bad.com"})assertThrows(FieldValidationException.class,()->InputRules.email("email",email,false));
 }
 @Test void mexicanIdentifiersValidateStructureAndDates(){
  var birth=LocalDate.of(1990,2,3);
  assertEquals("GODE900203HDFMNN01",InputRules.curp("gode900203hdfmnn01",birth));
  assertEquals("GODE900203ABC",InputRules.rfc("rfc","gode900203abc",false,birth));
  assertEquals("GODE900203HDFMNN01",InputRules.curp("GODE900203HDFMNN01",birth));
  assertEquals("GODE900203ABC",InputRules.rfc("rfc","GODE900203ABC",false,birth));
  assertEquals("ABC900203A12",InputRules.rfc("rfc","ABC900203A12",true,null));
  for(String raw:new String[]{"GODE900230HDFMNN01","GODE900203HXXMNN01","GODE900203HDFMNN0!","GODE900203HDFMNN0"," GODE900203HDFMNN01"})assertThrows(FieldValidationException.class,()->InputRules.curp(raw,null));
  for(String raw:new String[]{"GODE900230ABC","ABCDE900203ABC","ABC900203!!1","GODE900203 ABC"})assertThrows(FieldValidationException.class,()->InputRules.rfc("rfc",raw,false,null));
  assertThrows(FieldValidationException.class,()->InputRules.curp("GODE900203HDFMNN01",birth.plusDays(1)));
  assertThrows(FieldValidationException.class,()->InputRules.birth(LocalDate.now().plusDays(1)));
 }
}
