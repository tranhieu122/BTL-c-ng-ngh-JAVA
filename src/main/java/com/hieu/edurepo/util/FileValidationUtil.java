package com.hieu.edurepo.util;

import com.hieu.edurepo.exception.FileStorageException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lớp tiện ích xác thực và kiểm tra an toàn tệp tin được tải lên.
 *
 * <p>Thực hiện 3 lớp kiểm tra theo chiều sâu (defense-in-depth):</p>
 * <ol>
 *   <li><strong>Extension</strong>: Chỉ chấp nhận {@code pdf}, {@code doc}, {@code docx}.</li>
 *   <li><strong>Content-Type</strong>: So sánh MIME type từ browser với extension (tránh giả mạo).</li>
 *   <li><strong>Magic bytes</strong>: Đọc signature byte đầu file để xác nhận định dạng thực tế.</li>
 * </ol>
 *
 * <p>Bảo vệ Path Traversal trong tên file: kiểm tra các ký tự nguy hiểm
 * ({@code /}, {@code \}, {@code :}, null byte, Unicode bidi override characters)
 * và tên reserved trên Windows (CON, PRN, AUX, NUL, COM1-9, LPT1-9).</p>
 *
 * <p>Giới hạn kích thước mặc định: 200 MB.</p>
 *
 * <p>Đây là lớp utility tĩnh, không thể khởi tạo trực tiếp.</p>
 */
public final class FileValidationUtil {

    /** Tập hợp phần mở rộng file được phép tải lên. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    /** Kích thước file tối đa mặc định: 200 MB. */
    private static final long DEFAULT_MAX_FILE_SIZE = 200L * 1024 * 1024;

    /**
     * Ánh xạ phần mở rộng đến các MIME type hợp lệ.
     * {@code application/octet-stream} được cho phép vì một số browser gửi type này
     * thay vì MIME type cụ thể.
     */
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf", "application/octet-stream"),
            "doc", Set.of("application/msword", "application/octet-stream"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip", "application/octet-stream"));

    /** Private constructor: lớp utility không được khởi tạo. */
    private FileValidationUtil() {
    }

    /**
     * Xác thực file tải lên với giới hạn kích thước mặc định (200 MB).
     *
     * @param file File cần xác thực.
     * @return Phần mở rộng của file (chữ thường, không có dấu chấm).
     * @throws FileStorageException Nếu file không hợp lệ.
     */
    public static String validateAndGetExtension(MultipartFile file) {
        return validateAndGetExtension(file, DEFAULT_MAX_FILE_SIZE);
    }

    /**
     * Xác thực file tải lên với giới hạn kích thước tùy chỉnh.
     *
     * @param file        File cần xác thực.
     * @param maxFileSize Kích thước tối đa cho phép (byte).
     * @return Phần mở rộng của file (chữ thường, không có dấu chấm).
     * @throws FileStorageException Nếu file rỗng, quá lớn, sai định dạng hoặc MIME không khớp.
     */
    public static String validateAndGetExtension(MultipartFile file, long maxFileSize) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Tệp tải lên không được để trống");
        }
        if (maxFileSize <= 0 || file.getSize() > maxFileSize) {
            long maxMb = Math.max(1, maxFileSize / (1024 * 1024));
            throw new FileStorageException("Tệp tải lên vượt quá dung lượng tối đa " + maxMb + " MB");
        }

        String originalName = getSafeOriginalFileName(file);

        String extension = originalName.substring(originalName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Chỉ chấp nhận tệp PDF, DOC hoặc DOCX");
        }
        // Kiểm tra MIME type khai báo có khớp với extension không
        validateDeclaredContentType(file, extension);
        return extension;
    }

    /**
     * Kiểm tra MIME type khai báo (Content-Type header) có nằm trong danh sách
     * MIME type hợp lệ cho extension tương ứng hay không.
     *
     * @param file      File cần kiểm tra.
     * @param extension Phần mở rộng đã xác định.
     * @throws FileStorageException Nếu MIME type không khớp.
     */
    private static void validateDeclaredContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) return;
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.get(extension).contains(normalized)) {
            throw new FileStorageException("Loại nội dung tệp không khớp với phần mở rộng ." + extension);
        }
    }

    /**
     * Kiểm tra chữ ký (magic bytes) của file để xác nhận định dạng thực sự.
     * Đây không phải parser đầy đủ, chỉ kiểm tra container signature.
     *
     * <ul>
     *   <li>PDF: byte đầu là {@code %PDF-}.</li>
     *   <li>DOC: magic bytes OLE2 ({@code D0 CF 11 E0 A1 B1 1A E1}).</li>
     *   <li>DOCX: ZIP archive chứa {@code [Content_Types].xml} và {@code word/document.xml}.</li>
     * </ul>
     *
     * @param file      File cần kiểm tra nội dung.
     * @param extension Phần mở rộng đã xác định.
     * @throws FileStorageException Nếu nội dung không khớp định dạng hoặc lỗi đọc file.
     */
    public static void validateContent(MultipartFile file, String extension) {
        try (var input = new java.io.BufferedInputStream(file.getInputStream())) {
            input.mark(8);
            byte[] header = input.readNBytes(8);
            input.reset();
            boolean valid = switch (extension) {
                case "pdf" -> header.length >= 5 && new String(header, 0, 5,
                        java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
                case "doc" -> java.util.Arrays.equals(header,
                        new byte[]{(byte)0xd0, (byte)0xcf, 0x11, (byte)0xe0, (byte)0xa1, (byte)0xb1, 0x1a, (byte)0xe1});
                case "docx" -> isWordArchive(input);
                default -> false;
            };
            if (!valid) throw new FileStorageException("Nội dung tệp không khớp định dạng PDF, DOC hoặc DOCX đã chọn");
        } catch (java.io.IOException exception) {
            throw new FileStorageException("Không thể kiểm tra nội dung tệp", exception);
        }
    }

    /**
     * Kiểm tra ZIP archive có chứa các entry bắt buộc của DOCX không.
     * Giới hạn tối đa 2048 entries và 400 MB tổng dữ liệu giải nén để tránh Zip Bomb.
     *
     * @param input InputStream đã được reset về đầu file.
     * @return {@code true} nếu là DOCX hợp lệ.
     * @throws java.io.IOException Nếu lỗi đọc stream.
     */
    private static boolean isWordArchive(java.io.InputStream input) throws java.io.IOException {
        boolean contentTypes = false;
        boolean document = false;
        long totalBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new java.util.zip.ZipInputStream(input)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                // Giới hạn số entry để tránh Zip Bomb dạng nhiều file nhỏ
                if (++entries > 2048 || unsafeZipEntry(entry.getName())) return false;
                boolean hasContent = false;
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalBytes += read;
                    entryBytes += read;
                    hasContent |= read > 0;
                    // Giới hạn dung lượng giải nén để tránh Zip Bomb dạng ratio cao
                    if (entryBytes > 200L * 1024 * 1024 || totalBytes > 400L * 1024 * 1024) return false;
                }
                if (hasContent && entry.getName().equals("[Content_Types].xml")) contentTypes = true;
                if (hasContent && entry.getName().equals("word/document.xml")) document = true;
            }
        }
        return contentTypes && document;
    }

    /**
     * Kiểm tra entry name trong ZIP có chứa path traversal hoặc ký tự nguy hiểm không.
     *
     * @param name Tên entry trong ZIP.
     * @return {@code true} nếu entry name không an toàn.
     */
    private static boolean unsafeZipEntry(String name) {
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.indexOf('\u0000') >= 0
                || name.startsWith("/")) return true;
        return java.util.Arrays.stream(name.split("/", -1)).anyMatch(".."::equals);
    }

    /**
     * Lấy tên file gốc an toàn, kiểm tra path traversal và ký tự nguy hiểm.
     *
     * <p>Từ chối các tên file chứa: {@code /}, {@code \}, {@code :}, null byte,
     * Unicode Bidi override characters, tên reserved Windows (CON, PRN, ...).</p>
     *
     * @param file File tải lên.
     * @return Tên file gốc đã được xác nhận an toàn.
     * @throws FileStorageException Nếu tên file không an toàn hoặc không hợp lệ.
     */
    public static String getSafeOriginalFileName(MultipartFile file) {
        if (file == null) {
            throw new FileStorageException("Tệp tải lên không hợp lệ");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.indexOf('/') >= 0 || originalName.indexOf('\\') >= 0
                || originalName.indexOf(':') >= 0
                || originalName.indexOf('\u0000') >= 0
                || originalName.chars().anyMatch(FileValidationUtil::isUnsafeNameCharacter)
                || !originalName.equals(originalName.strip())) {
            throw new FileStorageException("Tên tệp không an toàn hoặc không hợp lệ");
        }
        if (originalName.length() > 255) {
            throw new FileStorageException("Tên tệp không được vượt quá 255 ký tự");
        }
        if (!originalName.contains(".") || originalName.endsWith(".")) {
            throw new FileStorageException("Tên tệp không hợp lệ");
        }
        // Từ chối tên reserved trên Windows (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
        String baseName = originalName.substring(0, originalName.indexOf('.')).toUpperCase(Locale.ROOT);
        if (baseName.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new FileStorageException("Tên tệp không an toàn hoặc không hợp lệ");
        }
        return originalName;
    }

    /**
     * Kiểm tra một ký tự có phải là ký tự nguy hiểm trong tên file không.
     * Bao gồm: ký tự điều khiển ISO và Unicode Bidi override characters
     * (dùng để đổi hướng hiển thị văn bản, có thể dùng để giả mạo phần mở rộng file).
     *
     * @param character Mã điểm Unicode của ký tự.
     * @return {@code true} nếu ký tự không an toàn.
     */
    private static boolean isUnsafeNameCharacter(int character) {
        return Character.isISOControl(character)
                || character == 0x202A || character == 0x202B || character == 0x202D
                || character == 0x202E || character == 0x202C
                || character == 0x2066 || character == 0x2067 || character == 0x2068
                || character == 0x2069;
    }
}
