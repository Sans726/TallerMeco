package mx.tallermeco.vehicle;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
public record VehicleData(String vin,String plate,String make,String model,String trim,String color,
 @JsonDeserialize(using=UnsignedIntegerDeserializer.class) String year,
 @JsonDeserialize(using=UnsignedIntegerDeserializer.class) String odometer){}
