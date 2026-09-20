package com.hieu.edurepo.service;

public interface SampleDataService {
    /**
     * Nạp dữ liệu tài liệu học thuật mẫu vào hệ thống nếu kho học liệu công khai chưa có tài liệu.
     *
     * @param force Nếu true, bỏ qua kiểm tra số lượng và tiến hành bổ sung dữ liệu mẫu.
     * @return Số lượng tài liệu mẫu đã được tạo thành công.
     */
    int seedSampleDocuments(boolean force);
}
