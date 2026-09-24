# 📕 README 9: Lộ Trình Hoàn Thiện 100% (Production-Grade Roadmap)

Tài liệu này giải đáp chi tiết câu hỏi: **"Để đưa phân hệ AI của EduRepo lên chuẩn 100% Doanh nghiệp (Enterprise Production-Ready), hệ thống còn thiếu những gì và cần làm gì tiếp theo?"**

---

## 1. Bảng Tổng Hợp Khoảng Trống Kỹ Thuật (Gap Analysis)

| Hạng mục kỹ thuật | Hiện trạng EduRepo | Chuẩn 100% Production | Mức độ ưu tiên |
| :--- | :--- | :--- | :---: |
| **1. Vector Database** | Mảng `float[]` lưu trong DB quan hệ, tính Cosine trong RAM bằng Java | Sử dụng **pgvector** (PostgreSQL) hoặc Vector DB chuyên dụng (**Qdrant / Milvus**) với chỉ mục HNSW | 🔴 **Cao** |
| **2. Tái xếp hạng (Reranking)** | Lấy trực tiếp Top-K từ điểm Cosine | Kết hợp **Retrieve Top-20 $\rightarrow$ Rerank Top-5** bằng Cross-Encoder (`bge-reranker` hoặc Cohere) | 🔴 **Cao** |
| **3. Trả lời dạng dòng (Streaming)** | Chờ LLM hoàn thành toàn bộ (mất 2–5s) mới gửi JSON về client | **Server-Sent Events (SSE)** hoặc WebSocket, truyền từng token (hiệu ứng gõ chữ thời gian thực) | 🔴 **Cao** |
| **4. Bộ nhớ hội thoại (Memory)** | Mỗi câu hỏi xử lý độc lập (Single-turn Q&A) | **Multi-turn Memory** (lưu ngữ cảnh 3–5 lượt chat gần nhất để hỏi tiếp nối) | 🟡 **Trung bình** |
| **5. Bộ đệm ngữ nghĩa (Cache)** | Mọi câu hỏi đều gọi OpenAI Embeddings & Chat Completion | **Semantic Cache** (Redis + Vector Cache) trả kết quả tức thì cho các câu hỏi tương đồng | 🟡 **Trung bình** |
| **6. Đánh giá chất lượng (Eval)** | Kiểm thử thủ công và Unit test cơ bản | Khung đánh giá tự động **RAGAS / TruLens** đo lường Faithfulness & Precision | 🟡 **Trung bình** |
| **7. Bảo mật AI (Guardrails)** | System Prompt ràng buộc cơ bản | Bộ lọc **Prompt Injection Defense** & **PII Masking** (che thông tin cá nhân) | 🟢 **Nâng cao** |

---

## 2. Chi Tiết Các Hạng Mục Cần Nâng Cấp Để Đạt 100%

```text
                                LỘ TRÌNH NÂNG CẤP 100%
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ BƯỚC 1: NÂNG CẤP HẠ TẦNG VECTOR & TÌM KIẾM (Độ trễ < 50ms cho 100.000+ tài liệu)       │
│  [PostgreSQL pgvector (HNSW Index)]  ──►  [Hybrid Search: BM25 + Vector (RRF Fusion)]   │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ BƯỚC 2: TỐI ƯU ĐỘ CHÍNH XÁC & TRẢI NGHIỆM (Chống ảo giác tuyệt đối & Stream từng chữ) │
│  [Cross-Encoder Reranker (bge-reranker)]  ──►  [Streaming Response SSE (Realtime typing)]│
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ BƯỚC 3: TIẾT KIỆM CHI PHÍ & ĐO LƯỜNG TỰ ĐỘNG (Scale doanh nghiệp)                      │
│  [Semantic Caching với Redis]  ──►  [Đánh giá tự động định kỳ với Framework RAGAS]     │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.1. Nâng Cấp Tầng Lưu Trữ: Chuyển Sang Vector Database (pgvector / Qdrant)

* **Vấn đề hiện tại:**
  - `RetrievalServiceImpl` tải danh sách chunks từ DB lên RAM rồi chạy vòng lặp `calculateCosineSimilarity`. Với 500 tài liệu (~50.000 chunks), RAM server sẽ bị quá tải và thời gian tìm kiếm tăng tuyến tính.
* **Giải pháp 100%:**
  - Kích hoạt extension `vector` trong PostgreSQL:
    ```sql
    CREATE EXTENSION IF NOT EXISTS vector;
    ALTER TABLE document_chunks ADD COLUMN embedding vector(1536);
    CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);
    ```
  - Thực hiện truy vấn trực tiếp dưới tầng Database:
    ```sql
    SELECT id, chunk_text, 1 - (embedding <=> :queryVector) AS similarity
    FROM document_chunks
    WHERE document_id IN (:allowedDocIds)
    ORDER BY embedding <=> :queryVector ASC
    LIMIT 10;
    ```
  - **Kết quả:** Tốc độ tìm kiếm giảm từ ~800ms xuống dưới **15ms** ngay cả khi có hàng triệu chunks.

---

### 2.2. Bổ Sung Tầng Tái Xếp Hạng Ngữ Nghĩa (Reranking Layer)

* **Vấn đề hiện tại:**
  - Mô hình Embedding (Bi-Encoder) mã hóa câu hỏi và văn bản độc lập nên dễ bỏ sót các sắc thái logic phức tạp hoặc từ phủ định.
* **Giải pháp 100%:**
  - Triển khai chiến lược 2 giai đoạn (Two-stage Retrieval):
    1. **Giai đoạn 1 (Fast Retrieval):** Lấy ra **Top-20** chunks có điểm tương đồng vector cao nhất.
    2. **Giai đoạn 2 (Deep Rerank):** Đưa câu hỏi và từng chunk vào mô hình **Cross-Encoder** (ví dụ `BAAI/bge-reranker-v2-m3` hoặc `Cohere Rerank API`) để tính điểm tương quan sâu.
    3. Chọn ra **Top-5** có điểm cao nhất để đưa vào `ContextBuilder`.
  - **Kết quả:** Tăng độ chính xác của đoạn trích dẫn thêm **25% – 35%**, loại bỏ triệt để các đoạn trích bề nổi nhưng không khớp nội dung.

---

### 2.3. Trả Lời Thời Gian Thực Với Streaming SSE (Server-Sent Events)

* **Vấn đề hiện tại:**
  - Người dùng bấm "Gửi" và phải nhìn biểu tượng loading quay trong 3–5 giây trước khi toàn bộ câu trả lời dài hiện ra một lần.
* **Giải pháp 100%:**
  - Chuyển đổi endpoint Controller sang trả về `SseEmitter` hoặc `Flux<String>` (Reactive Stream):
    ```java
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssistantAnswer(@RequestParam String question) { ... }
    ```
  - Cấu hình OpenAI Chat Completion với `stream: true`.
  - Frontend nhận từng gói tin event qua `EventSource` và render ngay lập tức từng ký tự lên màn hình.
  - **Kết quả:** Thời gian phản hồi đầu tiên (Time To First Token - TTFT) giảm xuống chỉ còn **~300ms**, mang lại cảm giác phản hồi tức thì như ChatGPT.

---

### 2.4. Lưu Trữ Bộ Nhớ Hội Thoại (Multi-turn Conversational Memory)

* **Vấn đề hiện tại:**
  - Mỗi request hiện tại là độc lập. Nếu người dùng hỏi:
    - *Câu 1:* "Ai là tác giả của tài liệu Lập trình Java Core?"
    - *Câu 2:* "Ông ấy còn viết cuốn sách nào khác không?"
    - $\rightarrow$ Ở câu 2, hệ thống sẽ không hiểu "ông ấy" là ai.
* **Giải pháp 100%:**
  - Lưu trữ lịch sử `ChatHistory` theo `sessionId` của người dùng (tối đa 4–6 lượt hội thoại gần nhất).
  - Trước khi RAG, sử dụng một bước **Query Rewriting (Tái cấu trúc câu hỏi)**:
    - Input: Câu hỏi mới + Lịch sử chat.
    - Prompt: *"Dựa vào lịch sử hội thoại, hãy viết lại câu hỏi sau thành một câu độc lập, đầy đủ chủ ngữ vị ngữ."*
    - Câu viết lại: *"Tác giả Nguyễn Văn A còn viết cuốn sách nào khác trong EduRepo không?"* $\rightarrow$ Đưa câu này đi RAG Retrieval.

---

### 2.5. Tích Hợp Bộ Đệm Ngữ Nghĩa (Semantic Cache với Redis)

* **Vấn đề hiện tại:**
  - Trong môi trường trường học, hàng trăm sinh viên thường hỏi các câu tương tự nhau về lịch thi, đề cương, giáo trình môn học. Hiện tại mỗi câu hỏi đều tốn tiền gọi OpenAI API và tiêu tốn tài nguyên server.
* **Giải pháp 100%:**
  - Cài đặt Redis Vector Cache (hoặc GPTCache):
    - Khi có câu hỏi mới, embed câu hỏi thành vector.
    - Tìm trong cache xem có câu hỏi nào trước đó có độ tương đồng cosine $> 0.96$ không.
    - Nếu có (Cache Hit): Trả về câu trả lời đã lưu kèm danh sách trích dẫn ngay lập tức (**0ms latency, $0 chi phí OpenAI**).
    - Nếu không (Cache Miss): Thực hiện RAG bình thường rồi lưu câu trả lời vào cache với thời gian hết hạn (TTL: 24h).

---

### 2.6. Khung Đánh Giá Tự Động RAGAS (Automated Evaluation)

* **Vấn đề hiện tại:**
  - Việc đánh giá AI trả lời có tốt không hiện đang phụ thuộc vào cảm tính của người kiểm thử.
* **Giải pháp 100%:**
  - Thiết lập bộ dữ liệu kiểm thử chuẩn (Ground Truth Testset) gồm 50 câu hỏi mẫu kèm tài liệu chuẩn.
  - Chạy công cụ đánh giá tự động định kỳ qua 4 chỉ số vàng của **RAGAS**:
    1. **Faithfulness (Độ trung thực):** Đo lường tỷ lệ câu trả lời hoàn toàn xuất phát từ tài liệu, không bịa đặt (Mục tiêu: $> 95\%$).
    2. **Answer Relevance (Độ liên quan):** Đo lường câu trả lời có giải quyết đúng trọng tâm câu hỏi không (Mục tiêu: $> 90\%$).
    3. **Context Precision (Độ chuẩn xác của trích xuất):** Các chunk tài liệu tìm được có đúng là phần chứa đáp án không (Mục tiêu: $> 85\%$).
    4. **Context Recall (Độ bao phủ):** Đã tìm đủ mọi đoạn thông tin cần thiết trong kho chưa (Mục tiêu: $> 90\%$).

---

## 3. Bản Kế Hoạch Triển Khai Cụ Thể (Actionable Execution Plan)

| Giai đoạn | Nội dung công việc | Kết quả đầu ra |
| :---: | :--- | :--- |
| **Tuần 1** | Kích hoạt extension `pgvector` trên PostgreSQL, cập nhật `DocumentChunkRepository` sử dụng Native Query HNSW. | Tốc độ tìm kiếm tăng gấp 20 lần, giảm 80% RAM server. |
| **Tuần 2** | Nâng cấp Controller hỗ trợ Server-Sent Events (SSE) và cập nhật `document-assistant.js` nhận stream token. | Giao diện gõ chữ thời gian thực, thời gian phản hồi đầu tiên < 400ms. |
| **Tuần 3** | Bổ sung Query Rewriter và bộ nhớ `ChatSessionMemory` cho phép đàm thoại nhiều lượt. | Người dùng có thể hỏi đào sâu liên tục các câu hỏi trước đó. |
| **Tuần 4** | Tích hợp Redis Semantic Cache và cấu hình bộ lọc bảo mật Prompt Injection Defense. | Giảm 40% chi phí OpenAI API hàng tháng, bảo vệ hệ thống khỏi tấn công jailbreak. |
