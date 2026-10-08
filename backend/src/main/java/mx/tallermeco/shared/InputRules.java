package mx.tallermeco.shared;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Locale;

/** Mexican customer/workshop input rules. Validate raw input before canonicalization. */
public final class InputRules {
    private InputRules() {}
    public static String text(String field,String raw,int max,boolean required) {
        if(raw!=null && raw.codePoints().anyMatch(Character::isISOControl)) fail(field,"No se permiten caracteres de control");
        if (raw==null || raw.isBlank()) { if(required) fail(field,"Este campo es obligatorio"); return null; }
        String value=Normalizer.normalize(raw,Normalizer.Form.NFC).strip().replaceAll(" +"," ").toLowerCase(Locale.ROOT).replace('\u2019','\'');
        if(value.length()>max) fail(field,"Máximo "+max+" caracteres");
        return value;
    }
    public static String name(String field,String raw,int max,boolean required) {
        String value=text(field,raw,max,required);
        if(value!=null && (!value.matches("[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*")))
            fail(field,"Usa letras, acentos, espacios, apóstrofes o guiones entre palabras");
        return value;
    }
    public static String address(String field,String raw,int max) {
        String value=text(field,raw,max,true);
        if (!value.matches("[\\p{L}\\p{M}0-9 .,#º°'\u2019/&()\\-]+") || !value.matches(".*[\\p{L}0-9].*"))
            fail(field,"La dirección contiene caracteres inválidos");
        return value;
    }
    public static String business(String field,String raw) {
        String value=text(field,raw,160,true);
        if(value.length()<2 || !value.matches("[\\p{L}\\p{M}0-9 .,&'\u2019()/\\-]+") || !value.matches(".*[\\p{L}0-9].*")) fail(field,"Nombre o razón social inválidos");
        return value;
    }
    public static String postal(String raw) {
        if(raw==null || !raw.matches("[0-9]{5}")) fail("postalCode","El código postal debe contener exactamente 5 dígitos, sin espacios ni signos");
        return raw;
    }
    public static String email(String field,String raw,boolean required) {
        String value=text(field,raw,254,required);
        if(value!=null && (!value.matches("[a-z0-9!#$%&'*+/=?^_`{|}~.-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
                || value.indexOf('@')>64 || value.startsWith(".") || value.contains("..") || value.contains(".@")))
            fail(field,"Email inválido; no se permiten espacios internos");
        return value;
    }
    public static String phone(String field,String raw,boolean required) {
        if(raw!=null && raw.codePoints().anyMatch(Character::isISOControl))fail(field,"No se permiten caracteres de control");
        if(raw==null || raw.isBlank()) { if(required) fail(field,"Este teléfono es obligatorio"); return null; }
        raw=raw.strip();
        // Only the documented formats are accepted; letters/signs are never silently removed.
        if(raw.length()>24 || !raw.matches("(?:\\+52[ -]?)?(?:[0-9]{10}|[0-9]{2}[ -][0-9]{4}[ -][0-9]{4}|\\([0-9]{2}\\)[ ]?[0-9]{4}[ -][0-9]{4})"))
            fail(field,"Usa 10 dígitos mexicanos, por ejemplo 5512345678 o +52 55 1234 5678");
        String value=raw.replaceAll("[^0-9]","");
        return value.length()==12 ? value.substring(2) : value;
    }
    public static String curp(String raw,LocalDate birth) {
        String value=identifier("curp",raw,18,false);
        if(value==null)return null;
        if(!value.toLowerCase(Locale.ROOT).matches("[a-z][aeioux][a-z]{2}[0-9]{6}[hm](?:as|bc|bs|cc|cl|cm|cs|ch|df|dg|gt|gr|hg|jc|mc|mn|ms|nt|nl|oc|pl|qt|qr|sp|sl|sr|tc|ts|tl|vz|yn|zs|ne)[b-df-hj-np-tv-z]{3}[a-z0-9][0-9]")) fail("curp","CURP inválida: verifica sus 18 caracteres y estructura mexicana");
        dateCode("curp",value.substring(4,10),birth,Character.isLetter(value.charAt(16))?2000:1900);
        return value;
    }
    public static String rfc(String field,String raw,boolean required,LocalDate birth) {
        String value=identifier(field,raw,13,required);
        if(value==null)return null;
        if(!value.toLowerCase(Locale.ROOT).matches("[a-zñ&]{3,4}[0-9]{6}[a-z0-9]{3}"))fail(field,"RFC inválido: usa 12 o 13 caracteres con estructura mexicana");
        dateCode(field,value.substring(value.length()-9,value.length()-3),birth,null);
        return value;
    }
    private static String identifier(String field,String raw,int max,boolean required) {
        if(raw==null || raw.isEmpty()) {if(required)fail(field,"Este campo es obligatorio");return null;}
        if(!raw.equals(raw.strip()) || raw.contains(" "))fail(field,"No se permiten espacios");
        String value=text(field,raw,max,required);return value==null?null:value.toUpperCase(Locale.ROOT);
    }
    private static void dateCode(String field,String value,LocalDate birth,Integer century) {
        try {
            int year=Integer.parseInt(value.substring(0,2)),month=Integer.parseInt(value.substring(2,4)),day=Integer.parseInt(value.substring(4,6));
            int full=century!=null?century+year:2000+year;
            if(century==null && full>LocalDate.now().getYear())full-=100;
            LocalDate encoded=LocalDate.of(full,month,day);
            if(encoded.isAfter(LocalDate.now()))fail(field,"La fecha incluida no puede ser futura");
            if(birth!=null && ((century!=null && !birth.equals(encoded)) || birth.getYear()%100!=year || birth.getMonthValue()!=month || birth.getDayOfMonth()!=day))fail(field,"La fecha incluida no coincide con el nacimiento");
        }catch(DateTimeException|NumberFormatException ex){fail(field,"La fecha incluida es inválida");}
    }
    public static LocalDate birth(LocalDate date) {
        if(date!=null && (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusYears(130))))fail("birthDate","Usa una fecha de nacimiento válida, no futura y de hasta 130 años");
        return date;
    }
    public static void fail(String field,String message) {throw new FieldValidationException(field,message);}
}
