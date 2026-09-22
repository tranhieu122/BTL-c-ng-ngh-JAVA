package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentReportForm;
import com.hieu.edurepo.entity.DocumentReport;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentReportStatus;

import java.util.List;
import java.util.Set;

/**
 * Dịch vụ tiếp nhận và xử lý báo cáo vi phạm nội dung học liệu từ người học.
 */
public interface DocumentReportService {

    /**
     * Tạo mới một phiếu báo cáo vi phạm cho một tài liệu cụ thể.
     *
     * @param documentId Mã tài liệu bị báo cáo
     * @param reporter Người dùng thực hiện gửi báo cáo
     * @param form Dữ liệu form chứa lý do và mô tả chi tiết vi phạm
     * @return Phiếu báo cáo vừa được tạo
     */
    DocumentReport create(Long documentId, User reporter, DocumentReportForm form);

    /**
     * Lấy danh sách tất cả các phiếu báo cáo vi phạm phục vụ Quản trị viên kiểm duyệt.
     * @return Danh sách phiếu báo cáo mới nhất
     */
    List<DocumentReport> findAllForModeration();

    /**
     * Cập nhật trạng thái xử lý phiếu báo cáo (RESOLVED, IGNORED) và lưu vết người xử lý.
     *
     * @param reportId Mã phiếu báo cáo
     * @param status Trạng thái xử lý mới
     * @param handler Quản trị viên phụ trách giải quyết
     * @return Phiếu báo cáo sau khi cập nhật
     */
    DocumentReport updateStatus(Long reportId, DocumentReportStatus status, User handler);

    /**
     * Lấy danh sách ID các tài liệu bị báo cáo mức độ nghiêm trọng (bản quyền, nội dung độc hại).
     * @return Tập hợp các mã tài liệu cần xem xét đặc biệt
     */
    Set<Long> seriouslyReportedDocumentIds();
}
