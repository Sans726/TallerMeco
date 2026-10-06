package mx.tallermeco.shared;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class SafeImageStorageTest {
 @TempDir Path root;
 @Test void genuineContentIsReencodedWithSafeName()throws Exception{
  var store=new SafeImageStorage(root.toString());var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
  String name=store.store(new MockMultipartFile("file","../../evil.exe","application/octet-stream",bytes.toByteArray()));
  assertTrue(name.matches("[a-f0-9-]{36}\\.png"));assertTrue(Files.isRegularFile(root.resolve(name)));assertEquals("image/png",store.read(name).getHeaders().getContentType().toString());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->store.read("../evil.png"));
 }
 @Test void fakeEmptyOversizeAndUnsupportedContentAreRejected()throws Exception{
  var store=new SafeImageStorage(root.toString());
  assertThrows(FieldValidationException.class,()->store.store(new MockMultipartFile("file","x.png","image/png","not image".getBytes())));
  assertThrows(FieldValidationException.class,()->store.store(new MockMultipartFile("file",new byte[0])));
  assertThrows(FieldValidationException.class,()->store.store(new MockMultipartFile("file",new byte[15*1024*1024+1])));
  var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"gif",bytes);
  assertThrows(FieldValidationException.class,()->store.store(new MockMultipartFile("file","x.png","image/png",bytes.toByteArray())));
  assertEquals(0,Files.list(root).count());
 }
}
