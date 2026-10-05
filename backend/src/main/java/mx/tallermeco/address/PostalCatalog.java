package mx.tallermeco.address;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Immutable, local SEPOMEX snapshot. No customer data leaves the application. */
@Service
public class PostalCatalog {
    public record Settlement(String state, String municipality, String neighborhood) {}
    public record Address(String postalCode, List<Settlement> settlements) {}
    private final Map<String, List<Settlement>> catalog;

    public PostalCatalog() throws IOException {
        Map<String, List<Settlement>> loaded = new HashMap<>();
        InputStream resource = getClass().getResourceAsStream("/postal/mexico.tsv.gz");
        if (resource == null) throw new IOException("Falta el catálogo postal de México");
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(resource), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", -1);
                if (columns.length != 4 || !columns[0].matches("[0-9]{5}")
                        || columns[1].isBlank() || columns[2].isBlank() || columns[3].isBlank()) {
                    throw new IOException("Catálogo postal inválido");
                }
                loaded.computeIfAbsent(columns[0], ignored -> new ArrayList<>())
                        .add(new Settlement(columns[1], columns[2], columns[3]));
            }
        }
        if (loaded.size() < 30000) throw new IOException("Catálogo postal incompleto");
        loaded.replaceAll((code, settlements) -> List.copyOf(settlements));
        catalog = Map.copyOf(loaded);
    }

    public Address lookup(String code) {
        if (!code.matches("[0-9]{5}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código postal debe tener cinco dígitos");
        }
        List<Settlement> settlements = catalog.get(code);
        if (settlements == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Código postal no encontrado. Puedes capturar la dirección manualmente.");
        }
        return new Address(code, settlements);
    }
}
