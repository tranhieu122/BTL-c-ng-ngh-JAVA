package com.hieu.edurepo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class AvatarStorageService {
    public static final long MAX_BYTES = 2 * 1024 * 1024;
    private final Path root;
    public AvatarStorageService(@Value("${app.upload.dir}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize().resolve("avatars");
    }
    public String store(Long ownerId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (file.isEmpty() || file.getSize() > MAX_BYTES || !(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                || !("image/png".equals(file.getContentType()) || "image/jpeg".equals(file.getContentType()))) {
            throw new IllegalArgumentException("Chọn ảnh PNG hoặc JPEG, dung lượng tối đa 2 MB.");
        }
        Path output = null;
        try (var stream = file.getInputStream(); var input = ImageIO.createImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Nội dung tệp không phải ảnh hợp lệ.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!("png".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format))
                        || width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_000_000) {
                    throw new IllegalArgumentException("Ảnh phải là PNG/JPEG, mỗi chiều tối đa 4096 px và tối đa 16 triệu điểm ảnh.");
                }
                BufferedImage source = reader.read(0);
                double ratio = Math.min(1d, 512d / Math.max(width, height));
                var clean = new BufferedImage(Math.max(1, (int)(width * ratio)), Math.max(1, (int)(height * ratio)), BufferedImage.TYPE_INT_ARGB);
                var graphics = clean.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, clean.getWidth(), clean.getHeight(), null);
                } finally { graphics.dispose(); }
                Files.createDirectories(root);
                String key = ownerId + "-" + UUID.randomUUID() + ".png";
                output = root.resolve(key);
                if (!ImageIO.write(clean, "png", output.toFile())) throw new IOException("PNG encoder unavailable");
                return key;
            } finally { reader.dispose(); }
        } catch (IOException e) {
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            throw new IllegalArgumentException("Không thể đọc hoặc lưu ảnh. Vui lòng thử ảnh khác.");
        }
    }
    private Path ownedPath(Long ownerId, String key) {
        if (key == null || !key.matches(java.util.regex.Pattern.quote(ownerId + "-") + "[0-9a-f-]{36}\\.png")) return null;
        Path path = root.resolve(key).normalize();
        return path.getParent().equals(root) ? path : null;
    }
    public Resource load(Long ownerId, String key) {
        Path path = ownedPath(ownerId, key);
        return path != null && Files.isRegularFile(path) ? new FileSystemResource(path) : new ClassPathResource("static/images/default-avatar.svg");
    }
    public void delete(Long ownerId, String key) {
        Path path = ownedPath(ownerId, key);
        if (path != null) try { Files.deleteIfExists(path); }
        catch (IOException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not remove obsolete avatar for account {}", ownerId); }
    }
}
