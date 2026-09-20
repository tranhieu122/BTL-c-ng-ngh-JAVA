package com.hieu.edurepo.config;

import com.hieu.edurepo.service.FileThreatScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình cơ chế quét mối đe dọa (mã độc, virus) cho các file tải lên hệ
 * thống.
 */
@Configuration(proxyBeanMethods = false)
public class FileThreatScanConfiguration {

    /**
     * Khởi tạo scanner mặc định (No-Op Scanner) nếu chưa có Bean FileThreatScanner
     * nào khác được định nghĩa.
     * - Môi trường Local/Dev: Luôn trả về true (hợp lệ) để việc phát triển độc lập,
     * không phụ thuộc service bên ngoài.
     * - Môi trường Production: Có thể thay thế bằng bean kết nối tới ClamAV,
     * VirusTotal hoặc dịch vụ quét chuyên dụng.
     */
    @Bean
    @ConditionalOnMissingBean(FileThreatScanner.class)
    FileThreatScanner noOpFileThreatScanner() {
        return (file, originalName) -> true;
    }
}
