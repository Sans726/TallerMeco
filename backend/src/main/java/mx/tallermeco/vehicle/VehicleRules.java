package mx.tallermeco.vehicle;
import mx.tallermeco.shared.InputRules;
import java.time.LocalDate;
import java.util.Locale;
public final class VehicleRules {
 private VehicleRules(){}
 public static VehicleData normalize(VehicleData r){
  if(r==null)InputRules.fail("vehicle","Los datos del vehículo son obligatorios");
  String vin=r.vin()==null?"":r.vin().strip().toUpperCase(Locale.ROOT);
  if(!vin.matches("[A-HJ-NPR-Z0-9]{17}"))InputRules.fail("vin","VIN: exactamente 17 letras o dígitos, sin I, O, Q, espacios ni signos");
  String plate=r.plate()==null?"":r.plate().strip().replace(" ","").toUpperCase(Locale.ROOT);
  if(!plate.matches("[A-Z0-9]+(?:-[A-Z0-9]+)*")||plate.length()<3||plate.length()>20)InputRules.fail("plate","Placa: de 3 a 20 letras, números o guiones entre grupos");
  int year=integer("year",r.year(),true,LocalDate.now().getYear()+1);
  if(r.year().length()!=4||year<1900)InputRules.fail("year","Año: cuatro dígitos, desde 1900 hasta "+(LocalDate.now().getYear()+1));
  Integer km=r.odometer()==null||r.odometer().isEmpty()?null:integer("odometer",r.odometer(),false,Integer.MAX_VALUE);
  return new VehicleData(vin,plate,commercial("make",r.make(),60),commercial("model",r.model(),80),commercial("trim",r.trim(),80),commercial("color",r.color(),60),String.valueOf(year),km==null?null:String.valueOf(km));
 }
 static String commercial(String field,String raw,int max){String value=InputRules.text(field,raw,max,true);if(!value.matches("[\\p{L}\\p{M}0-9 .,&'()/+\\-]+")||!value.matches(".*[\\p{L}0-9].*"))InputRules.fail(field,"Usa un nombre comercial con letras, números y separadores razonables");return value;}
 static int integer(String field,String value,boolean required,int max){if(value==null||value.isEmpty()){if(required)InputRules.fail(field,"Este campo es obligatorio");return 0;}if(!value.matches("[0-9]{1,10}"))InputRules.fail(field,"Usa un entero sin signos, decimales ni notación científica");long n=Long.parseLong(value);if(n>max)InputRules.fail(field,"Máximo permitido: "+max);return (int)n;}
}
