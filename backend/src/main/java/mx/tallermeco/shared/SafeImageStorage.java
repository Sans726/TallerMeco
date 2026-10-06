package mx.tallermeco.shared;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.*;

/** Decode genuine PNG/JPEG, bound dimensions, re-encode without user metadata or filenames. */
public final class SafeImageStorage {
 private final Path root;
 public SafeImageStorage(String dir){root=Path.of(dir).toAbsolutePath().normalize();}
 public String store(MultipartFile file){
  if(file==null||file.isEmpty()||file.getSize()>15L*1024*1024)InputRules.fail("image","Selecciona una imagen no vacía de hasta 15 MB");
  try(var input=ImageIO.createImageInputStream(file.getInputStream())){
   if(input==null)throw new IOException();var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())InputRules.fail("image","El contenido no es una imagen JPG o PNG válida");
   ImageReader reader=readers.next();
   try{
    String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
    if(!format.equals("png")&&!format.equals("jpeg"))InputRules.fail("image","Solo se permiten imágenes JPG o PNG reales");
    reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
    if(width<1||height<1||width>12000||height>12000||(long)width*height>40000000)InputRules.fail("image","La imagen supera el límite de dimensiones (40 millones de píxeles)");
    var decoded=reader.read(0);if(decoded==null)throw new IOException();
    Files.createDirectories(root);String reference=UUID.randomUUID()+(format.equals("png")?".png":".jpg");Path target=path(reference);
    try{if(!ImageIO.write(decoded,format,target.toFile()))throw new IOException();if(Files.size(target)>15L*1024*1024){Files.deleteIfExists(target);InputRules.fail("image","La imagen procesada excede 15 MB");}}
    catch(IOException ex){Files.deleteIfExists(target);throw ex;}
    if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)delete(reference);}});
    return reference;
   }finally{reader.dispose();}
  }catch(IOException|IllegalArgumentException ex){InputRules.fail("image","No se pudo leer la imagen. Usa un archivo JPG o PNG válido");return null;}
 }
 public ResponseEntity<Resource> read(String reference){
  try{Path file=path(reference);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(reference.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG).body(new UrlResource(file.toUri()));}
  catch(IOException|IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Imagen no encontrada");}
 }
 private Path path(String reference){
  if(reference==null||!reference.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpg)"))throw new IllegalArgumentException("Referencia inválida");
  Path target=root.resolve(reference).normalize();if(!target.getParent().equals(root))throw new IllegalArgumentException("Ruta inválida");return target;
 }
 public void replaceAfterCommit(String old){if(old==null)return;if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){delete(old);}});}
 private void delete(String reference){try{Files.deleteIfExists(path(reference));}catch(IOException|IllegalArgumentException ignored){}}
}
