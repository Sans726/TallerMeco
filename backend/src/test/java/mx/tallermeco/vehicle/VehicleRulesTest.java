package mx.tallermeco.vehicle;
import mx.tallermeco.shared.FieldValidationException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class VehicleRulesTest {
 VehicleData data(String vin,String year,String km){return new VehicleData(vin," ABC -123 ","Mercedes-Benz","CX-5 2.0","GT-Line+","Azul metálico",year,km);}
 @Test void canonicalizesWithoutInventingDomainData(){var d=VehicleRules.normalize(data(" 1hgcm82633a004352 ","2020",null));assertEquals("1HGCM82633A004352",d.vin());assertEquals("ABC-123",d.plate());assertEquals("mercedes-benz",d.make());assertNull(d.odometer());for(String km:new String[]{"0","123456","2147483647"})assertEquals(km,VehicleRules.normalize(data("1HGCM82633A004352","2020",km)).odometer());}
 @Test void rejectsNonIntegerAndUnsafeRanges(){for(String km:new String[]{"-1","12.5","2e3","NaN","Infinity","2147483648"," 12"})assertThrows(FieldValidationException.class,()->VehicleRules.normalize(data("1HGCM82633A004352","2020",km)));for(String year:new String[]{"1899","12.5","2e3","-2020","020","9999"})assertThrows(FieldValidationException.class,()->VehicleRules.normalize(data("1HGCM82633A004352",year,null)));assertNotNull(VehicleRules.normalize(data("1HGCM82633A004352",String.valueOf(LocalDate.now().getYear()+1),null)));}
 @Test void rejectsVinAndControlCharacters(){for(String vin:new String[]{"1HGCM82633A00435","1HGCM82633A0043522","IHGCM82633A004352","OHGCM82633A004352","QHGCM82633A004352","1HGCM826 3A004352","1HGCM826-3A004352","1HGCM826,3A004352"})assertThrows(FieldValidationException.class,()->VehicleRules.normalize(data(vin,"2020",null)));assertThrows(FieldValidationException.class,()->VehicleRules.commercial("make","Marca\nModelo",60));assertThrows(FieldValidationException.class,()->VehicleRules.commercial("make","<script>",60));}
}
