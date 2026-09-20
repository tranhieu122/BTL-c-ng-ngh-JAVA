package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.AuditLogFilter;
import com.hieu.edurepo.entity.AuditLog;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

/**
 * Service interface ghi và truy vấn nhật ký kiểm toán (audit log).
 *
 * <p>Cung cấp nhiều overload của phương thức {@code record()} để phù hợp với
 * các ngữ cảnh khác nhau: có/không có Spring Security context, có/không có
 * thông tin người dùng cụ thể, hoặc hành động ẩn danh.</p>
 *
 * <p>Implementation: {@code AuditLogServiceImpl}.</p>
 *
 * <h3>Các ngữ cảnh ghi log:</h3>
 * <ul>
 *   <li>{@code record(...)} – Dùng {@code SecurityContextHolder} để lấy người dùng hiện tại.</li>
 *   <li>{@code recordAsUser(...)} – Ghi log với {@link User} được truyền vào tường minh.</li>
 *   <li>{@code recordTransactionalAsUser(...)} – Như trên nhưng ghi trong transaction mới (dùng khi nằm trong transaction đã rollback).</li>
 *   <li>{@code recordAnonymous(...)} – Ghi log cho hành động không có người dùng đăng nhập.</li>
 * </ul>
 */
public interface AuditLogService {

    /**
     * Ghi log với thông tin người dùng từ SecurityContext hiện tại (không có targetName).
     *
     * @param action     Loại hành động.
     * @param targetType Loại đối tượng bị tác động.
     * @param targetId   ID đối tượng bị tác động.
     * @param description Mô tả ngữ cảnh hành động.
     * @param result     Kết quả: SUCCESS hoặc FAILURE.
     */
    void record(AuditAction action, AuditTargetType targetType, Long targetId,
                String description, AuditResult result);

    /**
     * Ghi log với thông tin người dùng từ SecurityContext hiện tại (có targetName).
     *
     * @param action     Loại hành động.
     * @param targetType Loại đối tượng bị tác động.
     * @param targetId   ID đối tượng bị tác động.
     * @param targetName Tên/tiêu đề đối tượng bị tác động.
     * @param description Mô tả ngữ cảnh hành động.
     * @param result     Kết quả: SUCCESS hoặc FAILURE.
     */
    void record(AuditAction action, AuditTargetType targetType, Long targetId, String targetName,
                String description, AuditResult result);

    /**
     * Ghi log với thông tin từ đối tượng {@link Authentication} được truyền vào.
     * Dùng khi không muốn phụ thuộc vào SecurityContextHolder (ví dụ trong async thread).
     *
     * @param authentication Thông tin xác thực người dùng thực hiện hành động.
     * @param action         Loại hành động.
     * @param targetType     Loại đối tượng bị tác động.
     * @param targetId       ID đối tượng bị tác động.
     * @param description    Mô tả ngữ cảnh hành động.
     * @param result         Kết quả.
     */
    void record(Authentication authentication, AuditAction action, AuditTargetType targetType, Long targetId,
                String description, AuditResult result);

    /**
     * Ghi log với {@link User} được truyền tường minh.
     * Dùng khi người dùng vừa được tạo (chưa có trong SecurityContext).
     *
     * @param actor      Người thực hiện hành động.
     * @param action     Loại hành động.
     * @param targetType Loại đối tượng bị tác động.
     * @param targetId   ID đối tượng.
     * @param description Mô tả.
     * @param result     Kết quả.
     */
    void recordAsUser(User actor, AuditAction action, AuditTargetType targetType, Long targetId,
                      String description, AuditResult result);

    /**
     * Ghi log trong một transaction mới (REQUIRES_NEW), kể cả khi transaction gốc bị rollback.
     * Đảm bảo audit log luôn được ghi dù nghiệp vụ chính thất bại.
     *
     * @param actor      Người thực hiện.
     * @param action     Loại hành động.
     * @param targetType Loại đối tượng.
     * @param targetId   ID đối tượng.
     * @param targetName Tên đối tượng.
     * @param description Mô tả.
     * @param result     Kết quả.
     */
    void recordTransactionalAsUser(User actor, AuditAction action, AuditTargetType targetType, Long targetId,
                                   String targetName, String description, AuditResult result);

    /**
     * Ghi log cho hành động ẩn danh (chưa đăng nhập), ví dụ đăng nhập thất bại.
     *
     * @param identifier Định danh người dùng (username/email nhập vào form).
     * @param action     Loại hành động.
     * @param targetType Loại đối tượng.
     * @param targetId   ID đối tượng.
     * @param description Mô tả.
     * @param result     Kết quả.
     */
    void recordAnonymous(String identifier, AuditAction action, AuditTargetType targetType, Long targetId,
                         String description, AuditResult result);

    /**
     * Tìm kiếm audit log theo bộ lọc đa tiêu chí (phân trang).
     *
     * @param filter   Bộ lọc: hành động, loại đối tượng, thời gian, kết quả, v.v.
     * @param pageable Thông tin phân trang.
     * @return Trang kết quả audit log.
     */
    Page<AuditLog> search(AuditLogFilter filter, Pageable pageable);

    /**
     * Tìm một bản ghi audit log theo ID.
     *
     * @param id ID bản ghi.
     * @return Đối tượng {@link AuditLog}.
     */
    AuditLog findById(Long id);
}
