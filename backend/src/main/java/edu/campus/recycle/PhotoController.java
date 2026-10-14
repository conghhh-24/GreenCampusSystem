package edu.campus.recycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    private final Path directory = Path.of("uploads", "photos").toAbsolutePath();

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择不超过5MB的图片");
        byte[] bytes = file.getBytes();
        boolean png = bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255;
        if ((!png && !jpeg) || ImageIO.read(new java.io.ByteArrayInputStream(bytes)) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传有效的JPG或PNG图片");
        String name = UUID.randomUUID() + (png ? ".png" : ".jpg");
        Files.createDirectories(directory);
        Files.write(directory.resolve(name), bytes);
        return Map.of("url", "/api/photos/" + name);
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> read(@PathVariable String name) throws IOException {
        if (!name.matches("[0-9a-f-]{36}\\.(png|jpg)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path path = directory.resolve(name);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
            .header("X-Content-Type-Options", "nosniff").body(Files.readAllBytes(path));
    }
}
