package mx.tallermeco.identity;

import mx.tallermeco.shared.Audit;
import mx.tallermeco.shared.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class ProfilePhotoService {
    private final Store db;
    private final Audit audit;
    private final Path root;
    public ProfilePhotoService(Store db, Audit audit,
            @Value("${app.profile-photo-dir:.local/uploads/profile-photos}") String directory) {
        this.db = db; this.audit = audit; root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public void store(long userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 15L * 1024 * 1024)
            throw invalid("Selecciona una imagen de hasta 15 MB");
        BufferedImage image;
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Solo se permiten imágenes JPG o PNG válidas");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!Set.of("JPEG", "PNG").contains(reader.getFormatName().toUpperCase(Locale.ROOT)))
                    throw invalid("Solo se permiten imágenes JPG o PNG");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 16_000_000)
                    throw invalid("La imagen debe tener como máximo 16 megapíxeles");
                image = reader.read(0);
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw invalid("No se pudo leer la imagen"); }
        // Decode and re-encode a bounded image: never serve the uploaded original or its metadata.
        double scale = Math.min(1d, 1024d / Math.max(image.getWidth(), image.getHeight()));
        var output = new BufferedImage(Math.max(1, (int)(image.getWidth()*scale)),
            Math.max(1, (int)(image.getHeight()*scale)), BufferedImage.TYPE_INT_ARGB);
        var graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, output.getWidth(), output.getHeight(), null);
        } finally { graphics.dispose(); }
        String reference = UUID.randomUUID() + ".png";
        Path target = root.resolve(reference);
        try {
            Files.createDirectories(root);
            ImageIO.write(output, "png", target.toFile());
        } catch (IOException ex) {
            erase(reference);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la fotografía");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) erase(reference); }
        });
        String old = reference(userId, true);
        db.update("UPDATE app_user SET photo_reference=? WHERE id=?", reference, userId);
        deleteAfterCommit(old);
        audit.record(userId, "PROFILE_PHOTO_UPDATED", "user", userId, Map.of());
    }

    @Transactional
    public void remove(long userId) {
        String old = reference(userId, true);
        db.update("UPDATE app_user SET photo_reference=NULL WHERE id=?", userId);
        deleteAfterCommit(old);
        audit.record(userId, "PROFILE_PHOTO_REMOVED", "user", userId, Map.of());
    }

    public Resource read(long userId) {
        String reference = reference(userId, false);
        if (reference == null || !reference.matches("[a-f0-9-]{36}\\.png"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sin fotografía de perfil");
        try {
            var resource = new UrlResource(root.resolve(reference).toUri());
            if (!resource.isReadable()) throw new IOException();
            return resource;
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no encontrada"); }
    }
    private String reference(long userId, boolean lock) {
        return (String) db.one("SELECT photo_reference FROM app_user WHERE id=?" + (lock ? " FOR UPDATE" : ""), userId).get("photo_reference");
    }
    private void deleteAfterCommit(String reference) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { erase(reference); }
        });
    }
    private void erase(String reference) {
        if (reference == null || !reference.matches("[a-f0-9-]{36}\\.png")) return;
        try { Files.deleteIfExists(root.resolve(reference)); }
        catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo eliminar una foto de perfil anterior", ex); }
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
