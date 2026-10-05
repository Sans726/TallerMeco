package mx.tallermeco.customer;

import mx.tallermeco.customer.repository.CustomerRepository;
import mx.tallermeco.identity.Actor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class CustomerPhotoService {
    private final CustomerRepository repository; private final Actor actor; private final Path root;
    public CustomerPhotoService(CustomerRepository repository, Actor actor, @Value("${app.customer-photo-dir:.local/uploads/customer-photos}") String directory) {
        this.repository = repository; this.actor = actor; this.root = Path.of(directory).toAbsolutePath().normalize();
    }
    public String store(long customerId, MultipartFile file) {
        if (!(actor.is("ADMIN") || actor.is("RECEPTIONIST"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración o recepción");
        if (file == null || file.isEmpty() || file.getSize() > 15L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fotografía vacía o mayor de 15 MB");
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            String type = file.getContentType();
            if (image == null || !("image/jpeg".equals(type) || "image/png".equals(type))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten imágenes JPG, JPEG o PNG");
            Files.createDirectories(root);
            String extension = "image/png".equals(type) ? ".png" : ".jpg";
            String reference = UUID.randomUUID() + extension;
            Path target = root.resolve(reference).normalize();
            if (!target.getParent().equals(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta inválida");
            file.transferTo(target);
            repository.updatePhotoReference(customerId, reference);
            return reference;
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la fotografía"); }
    }
    public Resource read(String reference) {
        try { Path path = root.resolve(reference).normalize(); if (!path.getParent().equals(root)) throw new IOException(); Resource resource = new UrlResource(path.toUri()); if (!resource.exists()) throw new IOException(); return resource; }
        catch (IOException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no encontrada"); }
    }
}
