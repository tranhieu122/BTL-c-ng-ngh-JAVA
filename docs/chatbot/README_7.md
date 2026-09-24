# 📘 README 7: Review Chi Tiết Kiến Trúc Phân Hệ AI & RAG EduBot

Tài liệu này đánh giá toàn diện mã nguồn, cấu trúc dữ liệu, các tầng dịch vụ (Service layer), cơ chế bóc tách văn bản, mô hình RAG và cơ chế Tool Calling của trợ lý ảo **EduBot** trong hệ thống **EduRepo**.

---

## 1. Tổng Quan Kiến Trúc AI Trong EduRepo

EduBot là phân hệ AI trợ lý học thuật thông minh, được xây dựng dựa trên kiến trúc **RAG (Retrieval-Augmented Generation)** kết hợp với khả năng **Function / Tool Calling** của OpenAI. Mục tiêu của phân hệ:
1. Cho phép giảng viên và sinh viên tra cứu kho học liệu bằng ngôn ngữ tự nhiên.
2. Trả lời câu hỏi học thuật dựa trên nội dung thực tế của các tài liệu đã được kiểm duyệt (`APPROVED`/`PUBLISHED`).
3. Chống ảo giác (Anti-Hallucination) tuyệt đối bằng cách bắt buộc mô hình trích dẫn nguồn `[1]`, `[2]` kèm theo đoạn trích nguyên văn (Snippet Preview) khi di chuột.
4. Mở rộng khả năng tương tác ngoại vi thông qua bộ công cụ (Tools: Thời tiết, Giờ hiện tại, Tra cứu cơ sở dữ liệu).

---

## 2. Bản Đồ Các Thành Phần (Component Architecture)

```text
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             GIAO DIỆN NGƯỜI DÙNG                                 │
│      document-assistant.js  |  document-assistant.css  |  Hover Snippet Tooltip   │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ HTTP REST (JSON)
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                            TẦNG CONTROLLER & BẢO MẬT                             │
│   DocumentAssistantController  │  AdminRagController  │  RateLimiter (IP/User)   │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │
                    ┌────────────────────┴────────────────────┐
                    ▼                                         ▼
┌───────────────────────────────────────┐ ┌────────────────────────────────────────┐
│      LUỒNG 1: CHUẨN HÓA & INDEXING    │ │        LUỒNG 2: RAG & TOOL CALLING     │
│       (Khi tài liệu được duyệt)       │ │          (Khi người dùng hỏi)          │
├───────────────────────────────────────┤ ├────────────────────────────────────────┤
│ • TextExtractionService (PDF/Word)    │ │ • DocumentAssistantService (Điều phối) │
│ • OcrService (Tesseract OCR)          │ │ • ToolExecutorService (3 Tools)        │
│ • TextCleaningService (Lọc ký tự rác) │ │ • RetrievalService (Cosine Similarity) │
│ • SectionDetectionService (Tìm mục)   │ │ • EmbeddingService (Vector hóa Query)  │
│ • DocumentChunker (Chunking + Overlap)│ │ • ContextBuilder (Định dạng ngữ cảnh)  │
│ • EmbeddingService (text-embedding-3) │ │ • OpenAIService (GPT-4o-mini Chat)     │
└───────────────────┬───────────────────┘ └───────────────────┬────────────────────┘
                    │                                         │
                    ▼                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              TẦNG CƠ SỞ DỮ LIỆU                                  │
│   DocumentChunk Entity (Chunk text, Page, Section, Vector mảng float)            │
│   Document Entity (Metadata, Status: APPROVED/PUBLISHED)                         │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Chi Tiết Các Tầng Dịch Vụ (Service Breakdown)

### 3.1. Phân Hệ Xử Lý Văn Bản & OCR (Document Ingestion Pipeline)

* **`TextExtractionService` (`TextExtractionServiceImpl`):**
  - Trích xuất nội dung văn bản từ các định dạng file học liệu phổ biến: PDF (`PDFBox`), Word DOCX (`Apache POI`), và Text thuần.
  - Phân tích và giữ nguyên cấu trúc trang (`ExtractedPage`).
* **`OcrService` (`OcrServiceImpl`):**
  - Sử dụng engine **Tesseract OCR** để nhận diện văn bản tiếng Việt từ các trang tài liệu dạng ảnh quét (scanned PDF) hoặc ảnh nhúng.
  - Tự động kích hoạt khi trang PDF không trích xuất được text thông thường (dưới ngưỡng tối thiểu).
* **`TextCleaningService` (`TextCleaningServiceImpl`):**
  - Loại bỏ ký tự điều khiển rác, khoảng trắng thừa, chuẩn hóa dấu câu tiếng Việt và ngắt dòng hợp lý.
* **`SectionDetectionService` (`SectionDetectionServiceImpl`):**
  - Ứng dụng biểu thức chính quy (Regex) và phân tích định dạng tiêu đề để nhận diện các mục: *Chương, Bài, Phần, Mục I, II, 1.1, 1.2*.
  - Giúp gắn nhãn ngữ cảnh (`sectionTitle`) cho từng chunk dữ liệu.

---

### 3.2. Phân Hệ Phân Đoạn & Vector Hóa (Chunking & Embedding)

* **`DocumentChunker`:**
  - Thực hiện chiến lược **Semantic-Aware Chunking**: Ưu tiên cắt đoạn theo ranh giới câu và đoạn văn tự nhiên thay vì cắt ngang từ ngữ.
  - Kích thước chunk: ~`500 - 800` tokens kèm vùng gối đầu **Overlap: ~`100 - 150` tokens**. Đảm bảo thông tin ở các ranh giới cắt không bị đứt gãy.
  - Lưu trữ đầy đủ metadata: `documentId`, `pageNumber`, `sectionTitle`, `chunkIndex`.
* **`EmbeddingService` (`EmbeddingServiceImpl`):**
  - Kết nối OpenAI Embeddings API với model tiêu chuẩn `text-embedding-3-small` (1536 chiều).
  - Hỗ trợ cả `embedText(String)` và `embedBatch(List<String>)` để tối ưu số lượng request mạng khi nạp tài liệu lớn.

---

### 3.3. Phân Hệ Truy Xuất (Retrieval Engine)

* **`RetrievalService` (`RetrievalServiceImpl`):**
  - **Vector Similarity Search:** Tính toán khoảng cách **Cosine Similarity** giữa vector câu hỏi của người dùng và vector của tất cả các `DocumentChunk` trong hệ thống.
  - **Metadata Filtering:** Chỉ truy xuất các chunk thuộc tài liệu có trạng thái `PUBLISHED` hoặc `APPROVED` mà người dùng hiện tại có quyền truy cập.
  - **Fallback Lexical Search:** Khi không có mạng hoặc chưa cấu hình OpenAI API Key, hệ thống tự động chuyển đổi sang tìm kiếm đối sánh từ khóa/tần suất từ (Lexical Matching) để đảm bảo trợ lý không bao giờ bị tê liệt.
* **`ContextBuilder`:**
  - Nhận Top-K chunks phù hợp nhất từ `RetrievalService`.
  - Đóng gói thành prompt ngữ cảnh chuẩn hóa:
    ```text
    [Tài liệu 1]: Giáo trình Java Core (Trang 15, Mục 2.1)
    Nội dung: ...
    [Tài liệu 2]: Lập trình mạng (Trang 42)
    Nội dung: ...
    ```

---

### 3.4. Phân Hệ Suy Luận & Gọi Công Cụ (Agentic LLM & Tool Calling)

* **`OpenAIService` (`OpenAIServiceImpl`):**
  - Sử dụng model `gpt-4o-mini` cho tốc độ phản hồi nhanh và chi phí tối ưu.
  - Hỗ trợ gọi hàm (Tool/Function Calling) chuẩn OpenAI format.
* **`ToolExecutorService` (`ToolExecutorServiceImpl`):**
  - Điều phối và thực thi 3 công cụ độc lập:
    1. `search_documents`: Gọi RAG Retrieval để tra cứu kho tài liệu nội sinh.
    2. `get_weather`: Gọi `WeatherService` lấy thời tiết theo thời gian thực (fallback sang Open-Meteo miễn phí nếu thiếu API key).
    3. `get_current_time`: Gọi `TimeService` lấy ngày giờ chuẩn xác múi giờ `Asia/Ho_Chi_Minh`.
* **`DocumentAssistantService` (`DocumentAssistantServiceImpl`):**
  - "Bộ não" trung tâm tiếp nhận yêu cầu từ Controller, quản lý vòng lặp Tool Calling Loop (Agent Loop), tổng hợp câu trả lời cuối cùng và danh sách nguồn tham khảo (`RagSource`).

---

## 4. Cơ Chế Chống Ảo Giác & Interactive Hover Citation

Một trong những điểm sáng nhất của EduRepo là cơ chế trích dẫn tương tác:

1. **System Prompt Ràng Buộc Nghiêm Ngặt:**
   - LLM được chỉ dẫn: *"Chỉ sử dụng thông tin trong ngữ cảnh được cung cấp. Tuyệt đối không tự suy diễn nếu ngữ cảnh không đề cập. Khi khẳng định bất kỳ thông tin nào, PHẢI đánh dấu trích dẫn dạng `[1]`, `[2]` ứng với tài liệu tham khảo."*
2. **Frontend Dynamic Hover Rendering (`document-assistant.js`):**
   - Khi nhận câu trả lời có chứa `[1]`, `[2]`, mã Javascript chuyển đổi chúng thành các thẻ tương tác `<span class="citation-badge" data-citation="1">[1]</span>`.
   - Khi người dùng di chuột (hover trên PC) hoặc chạm (tap trên Mobile):
     - Popup nổi bật (Tooltip Preview) hiện ra ngay tại vị trí con trỏ.
     - Hiển thị: **Tên tài liệu**, **Số trang**, **Tiêu đề mục**, và **Đoạn trích dẫn nguyên văn** mà AI đã dùng.
     - Có nút bấm *"Xem tài liệu gốc"* đưa người dùng đến trang chi tiết tài liệu.

---

## 5. Đánh Giá Điểm Mạnh & Hạn Chế Kiến Trúc Hiện Tại

### ✅ Điểm mạnh:
* **Kiến trúc phân tầng rõ ràng:** Tách biệt rành mạch giữa Controller, Service, Ingestion, và AI Engine.
* **Độ hoàn thiện cao hơn RAG thông thường:** Đã có OCR tiếng Việt, Chunking theo mục/section, và tích hợp Tool Calling (Agentic RAG).
* **Trải nghiệm người dùng (UX) xuất sắc:** Hover citation preview đem lại tính minh bạch tuyệt đối cho môi trường học thuật.
* **Cơ chế Fallback bền vững:** Mất kết nối internet hoặc hết quota OpenAI thì hệ thống vẫn không bị sập (crash).

### ⚠️ Hạn chế kỹ thuật hiện tại:
* **Vector Search trên bộ nhớ RAM:** Vector được lưu dưới dạng mảng `float[]` trong DB quan hệ và tính Cosine Similarity bằng vòng lặp Java. Phù hợp với quy mô hàng chục ngàn chunks, nhưng khi lên hàng triệu chunks sẽ tốn RAM và chậm.
* **Thiếu Streaming (SSE):** Hiện tại trả lời toàn bộ câu sau khi LLM sinh xong (mất 2-4 giây) thay vì gõ chữ từng từ.
* **Chưa có Reranking Model:** Top-K chunks được chọn trực tiếp từ điểm Cosine mà chưa qua tầng Cross-Encoder Reranker để tinh chỉnh độ liên quan sâu.
