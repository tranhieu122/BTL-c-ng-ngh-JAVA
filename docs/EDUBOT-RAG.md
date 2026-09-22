# Tài Liệu Kỹ Thuật: EduBot - Chatbot RAG Thông Minh (EduRepo)

Tài liệu này mô tả chi tiết kiến trúc, luồng hoạt động, cơ chế RAG (Retrieval-Augmented Generation), mô hình dữ liệu và tính năng **Interactive Citation & Snippet Preview khi Hover** của trợ lý tài liệu EduBot trong hệ thống **EduRepo**.

---

## 1. Tổng Quan về EduBot

EduBot là trợ lý ảo AI được tích hợp trực tiếp vào EduRepo nhằm hỗ trợ người dùng:
1. **Tìm kiếm ngữ nghĩa (Semantic Search):** Tìm tài liệu theo ngữ cảnh, chủ đề, tên tác giả, năm xuất bản ngay cả khi không gõ đúng từ khóa chính xác.
2. **Hỏi đáp học liệu chuyên sâu (RAG Q&A):** Trực tiếp đọc và tổng hợp câu trả lời từ nội dung thực tế của các tài liệu giáo trình, bài giảng, đề cương (PDF/Text) đã được công bố trên hệ thống.
3. **Trích dẫn minh bạch & Xem trước (Interactive Citations & Snippet Hover):** Mỗi kết luận hoặc câu trả lời của AI đều đánh dấu nguồn trích dẫn `[1]`, `[2]`. Người dùng có thể di chuột (hover trên Desktop) hoặc chạm (tap trên Mobile) vào số trích dẫn để đọc ngay đoạn nội dung mà AI đã sử dụng, kèm nút liên kết mở trực tiếp đến tài liệu gốc.

---

## 2. Kiến Trúc Luồng Dữ Liệu (RAG Pipeline)

EduBot tuân thủ nghiêm ngặt nguyên tắc **Chống ảo giác (Anti-Hallucination)** và bảo mật dữ liệu học thuật:

```text
               +-------------------------------------------+
               |             Câu hỏi người dùng            |
               +-------------------------------------------+
                                     |
                                     v
                        [Phân tích ý định & Query]
                                     |
              +----------------------+----------------------+
              |                                             |
              v (Ý định lọc danh sách)                      v (Ý định hỏi nội dung/kiến thức)
      [Structured Database Search]               [RAG Vector Semantic Retrieval]
              |                                             |
              |                               +-------------+-------------+
              |                               | Query Embedding (OpenAI)  |
              |                               | Cosine Vector Similarity  |
              |                               | Fallback: Lexical Search  |
              |                               +-------------+-------------+
              |                                             |
              |                                             v
              |                                 [Top-K Document Chunks]
              |                                             |
              |                                             v
              |                                  [Xây dựng ContextBuilder]
              |                                  - Gắn ID: [Document N]
              |                                  - Ràng buộc trích dẫn [1]
              |                                             |
              |                                             v
              |                                    [OpenAI Chat API]
              |                                             |
              v                                             v
     DocumentAssistantResponse                     DocumentAssistantResponse
  (type: RESULTS, documents: [...])             (type: RAG_ANSWER, sources: [...])
              |                                             |
              +----------------------+----------------------+
                                     |
                                     v
                  [Frontend: modules/document-assistant.js]
                  - Parse Regex `\[(\d+)\]` thành Interactive Citation Node
                  - Hover/Tap Event: Render Tooltip Preview ngay lập tức
```

---

## 3. Các Thành Phần Mã Nguồn Cốt Lõi

### 3.1. Backend (Spring Boot / Java)

| Thành phần | Đường dẫn file | Vai trò |
| :--- | :--- | :--- |
| **Controller** | `com/hieu/edurepo/controller/DocumentAssistantController.java` | Tiếp nhận request `GET /api/document-assistant`, nhận query và context hội thoại, xử lý ngoại lệ an toàn. |
| **Service** | `com/hieu/edurepo/service/impl/DocumentAssistantServiceImpl.java` | Điều phối logic: Nhận diện ý định (`isContentQuestion`), gọi RAG retrieval, fallback structured search, kiểm soát quyền riêng tư tài liệu công khai (`PUBLISHED`). |
| **Indexing Service** | `com/hieu/edurepo/service/impl/DocumentIndexingServiceImpl.java` | Tự động đọc file PDF, phân đoạn nội dung văn bản thành các chunks và tính vector embedding lưu vào DB. |
| **Chunking Engine** | `com/hieu/edurepo/service/DocumentChunker.java` | Tách văn bản tài liệu thành các khối (chunk) có độ dài tối ưu (500–800 ký tự) giữ nguyên ranh giới câu tiếng Việt/tiếng Anh. |
| **Retrieval Engine** | `com/hieu/edurepo/service/impl/RetrievalServiceImpl.java` | Tìm kiếm Top-K chunks tương đồng nhất bằng Cosine Similarity trên vector embedding kết hợp cache bộ nhớ; có fallback text match. |
| **Context Builder** | `com/hieu/edurepo/service/ContextBuilder.java` | Đóng gói Prompt ngữ cảnh gửi đến LLM, quy định quy tắc trích dẫn `[1]`, `[2]`, tạo `RagSource` chứa `snippet` và `chunkId` thật. |
| **LLM Service** | `com/hieu/edurepo/service/impl/OpenAIServiceImpl.java` | Giao tiếp bảo mật với OpenAI Chat Completion API qua REST client, xử lý timeout và retry. |
| **Entity** | `com/hieu/edurepo/entity/DocumentChunk.java` | Ánh xạ bảng `document_chunks`: lưu trữ `content`, `embedding_json`, `chunk_index`, `page_number`, `document_id`. |
| **DTOs** | `com/hieu/edurepo/dto/RagSource.java`<br>`com/hieu/edurepo/dto/DocumentAssistantResponse.java` | Cấu trúc dữ liệu JSON trả về frontend, chứa `answer` cùng danh sách `sources` chi tiết. |

### 3.2. Frontend (Thymeleaf / Vanilla JS / CSS)

| File | Chức năng |
| :--- | :--- |
| `src/main/resources/templates/fragments/document-assistant.html` | Widget giao diện popup của chatbot (avatar EduBot, khung chat, gợi ý câu hỏi nhanh, ô nhập liệu). |
| `src/main/resources/static/js/modules/document-assistant.js` | Logic quản lý hội thoại, gửi request API, parse regex citation `[1]`, và kích hoạt popover xem trước khi hover hoặc chạm. |
| `src/main/resources/static/css/components/document-assistant.css` | Phong cách thiết kế hiện đại: hiệu ứng trượt, bubble chat, badge citation, popover xem trước tài liệu có bóng đổ và bo góc tinh tế. |

---

## 4. Chi Tiết Tính Năng Citation & Hover Snippet Preview

### 4.1. Cấu Trúc Dữ Liệu Source Trả Về

Thay vì chỉ trả về liên kết chung chung, API trả về nguồn trích dẫn đầy đủ:

```json
{
  "type": "RAG_ANSWER",
  "answer": "Information Gathering là giai đoạn thu thập thông tin về mục tiêu trước khi tiến hành kiểm thử tiếp theo [1]. Trong giai đoạn này có thể phân tích domain, endpoint và cấu trúc hệ thống [2].",
  "sources": [
    {
      "sourceId": 1,
      "documentId": 21,
      "chunkId": 18291,
      "title": "2_AI_Russell_Norvig.pdf",
      "detailUrl": "/repository/21",
      "snippet": "Information Gathering is the process of collecting information about the target before performing further security testing. It includes reconnaissance, passive DNS queries, and service enumeration...",
      "chunkIndex": 0,
      "pageNumber": 14,
      "relevance": 0.89
    },
    {
      "sourceId": 2,
      "documentId": 45,
      "chunkId": 18340,
      "title": "Web Security Testing Guide.pdf",
      "detailUrl": "/repository/45",
      "snippet": "The information gathering phase involves enumerating web application endpoints, examining application architecture and identifying software frameworks...",
      "chunkIndex": 3,
      "pageNumber": null,
      "relevance": 0.84
    }
  ]
}
```

### 4.2. Giao Diện Hover Popover

Khi người dùng hover chuột vào badge `[1]`, một popover nổi bật xuất hiện ngay cạnh vị trí con trỏ:

```text
┌─────────────────────────────────────────────────────────────┐
│ 📄 2_AI_Russell_Norvig.pdf                                  │
├─────────────────────────────────────────────────────────────┤
│ ❝ Information Gathering is the process of collecting        │
│ information about the target before performing further     │
│ security testing. It includes reconnaissance, passive DNS  │
│ queries, and service enumeration... ❞                       │
├─────────────────────────────────────────────────────────────┤
│                                             Xem tài liệu →  │
└─────────────────────────────────────────────────────────────┘
```

- **Tự động định vị thông minh:** Popover tự tính toán vị trí phía trên hoặc phía dưới citation, đảm bảo không tràn ra ngoài màn hình và không che khuất đoạn hội thoại.
- **In-Memory Cache:** Nội dung snippet lấy ngay từ payload đã nhận, không gửi thêm HTTP request nào khi hover, mang lại trải nghiệm mượt mà tức thì.
- **Hỗ trợ đa nền tảng:**
  - **Desktop:** Hover chuột vào để xem, di chuột vào bên trong popover để đọc đoạn dài.
  - **Mobile:** Nhấp chạm (tap) trực tiếp vào `[1]` để bật/tắt popover.
- **Xem tài liệu:** Nút `Xem tài liệu →` liên kết trực tiếp tới `/repository/{documentId}`.

---

## 5. Cấu Hình Ứng Dụng (Application Properties)

Các thông số điều khiển hoạt động RAG được đặt trong `application.properties`:

```properties
# Bật/tắt tính năng RAG
app.rag.enabled=true

# Số lượng chunk tối đa lấy từ cơ sở dữ liệu
app.rag.top-k=4

# Ngưỡng tương đồng tối thiểu (0.0 đến 1.0)
app.rag.similarity-threshold=0.65

# Giới hạn token ngữ cảnh gửi sang OpenAI
app.rag.max-context-tokens=3000

# Cấu hình OpenAI API
app.openai.api-key=${OPENAI_API_KEY}
app.openai.model=gpt-4o-mini
app.openai.embedding-model=text-embedding-3-small
```

---

## 6. Hướng Dẫn Kiểm Thử (Testing)

Dự án có sẵn bộ test tự động đầy đủ để kiểm tra RAG và Assistant:

```bash
# Chạy bộ test RAG Pipeline, ContextBuilder và Trợ lý ảo
./mvnw test "-Dtest=DocumentAssistantControllerTest,DocumentAssistantServiceTest,ContextBuilderTest,RagPipelineTest,OwaspRagEndToEndTest"
```

### Các kịch bản kiểm thử quan trọng:
1. **Kiểm thử trích dẫn đơn:** Câu hỏi kiểm thử định nghĩa hoặc khái niệm trong tài liệu cụ thể -> Kiểm tra câu trả lời có chứa `[1]`, hover `[1]` ra đúng tên file và snippet.
2. **Kiểm thử đa trích dẫn:** Câu hỏi tổng hợp -> Kiểm tra sự xuất hiện của nhiều mốc trích dẫn `[1]`, `[2]`, mỗi mốc liên kết đúng nguồn tương ứng.
3. **Kiểm thử an toàn dữ liệu:** Tài liệu chưa được duyệt (`PENDING` hoặc `REJECTED`) sẽ không xuất hiện trong kết quả RAG và không thể trích dẫn.
4. **Kiểm thử fallback khi không có OpenAI:** Hệ thống tự động chuyển sang Semantic/Lexical Matching và trả về danh sách tài liệu gợi ý thay vì báo lỗi gián đoạn.

---

## 7. Nâng Cấp Kiến Trúc: RAG + Tool Calling AI Assistant

EduBot đã được nâng cấp từ một RAG Chatbot thông thường lên **RAG + Tool Calling AI Assistant**, kết hợp linh hoạt khả năng gọi công cụ ngoại vi (Function/Tool Calling) của mô hình OpenAI (GPT-5.6 Luna / GPT-4o) mà **không phá vỡ hay thay đổi kiến trúc RAG hiện tại**.

```text
                                 [Câu hỏi người dùng]
                                          │
                        ┌─────────────────┴─────────────────┐
                        │                                   │
              (Hỏi thời tiết, giờ giấc,              (Duyệt danh mục,
               kiến thức, multi-tool)                 tài liệu mới nhất)
                        │                                   │
                        ▼                                   ▼
             [OpenAI Tool Calling Loop]            [Structured DB Search]
             - System Prompt hướng dẫn              - Tìm kiếm phân trang
             - Cung cấp danh sách 3 Tools           - Sắp xếp theo Rating/Date
                        │
       ┌────────────────┼────────────────┐
       ▼                ▼                ▼
[search_documents]  [get_weather]   [get_current_time]
 (RAG Retrieval)     (Weather API     (Java Time API
 (Cosine Vectors)   + Open-Meteo)     Asia/Ho_Chi_Minh)
       │                │                │
       └────────────────┼────────────────┘
                        │
                        ▼
           [Gửi kết quả Tool về OpenAI]
                        │
                        ▼
      [Câu trả lời tổng hợp cuối cùng của AI]
      - Trả lời đầy đủ mọi ý trong câu hỏi
      - Giữ nguyên trích dẫn [1], [2] từ search_documents
      - Tích lũy RagSource để hiển thị Hover Snippet Preview
```

### 7.1. Danh Sách Công Cụ Được Hỗ Trợ (Tools)

| Tên Tool | Mô tả | Tham số | Dịch vụ phụ trách |
| :--- | :--- | :--- | :--- |
| `search_documents` | Tra cứu giáo trình, bài giảng, tài liệu học tập và các đoạn trích nội dung (chunks) trong EduRepo. | `query` (String): Từ khóa/câu hỏi tra cứu | `ToolExecutorServiceImpl` phối hợp `RetrievalService` & `ContextBuilder`. Giữ nguyên `RagSource` và `Document`. |
| `get_weather` | Lấy thông tin thời tiết thời gian thực (nhiệt độ, độ ẩm, tình trạng mây mưa, sức gió). | `city` (String): Tên thành phố (mặc định: `'Hanoi'`) | `WeatherServiceImpl` (hỗ trợ WeatherAPI hoặc tự động fallback sang Open-Meteo miễn phí). |
| `get_current_time` | Lấy ngày và giờ hiện tại đã được định dạng tiếng Việt chuẩn mực. | `timezone` (String): Múi giờ IANA (mặc định `'Asia/Ho_Chi_Minh'`) | `TimeServiceImpl` (sử dụng Java Time API `ZonedDateTime`). |

### 7.2. Điểm Nổi Bật Kỹ Thuật

1. **Không phá vỡ RAG & Citations:** Tool `search_documents` trả về cả JSON ngữ cảnh cho LLM lẫn danh sách `RagSource` và `Document` thực tế. Do đó, popup hover tooltip `[1]`, `[2]` trên frontend hoạt động hoàn hảo 100%.
2. **Hỗ trợ câu hỏi phức hợp (Multi-Tool Calling):** Người dùng có thể hỏi một câu kết hợp nhiều ý: *"Thời tiết Hà Nội hôm nay thế nào và tìm cho tôi tài liệu về Spring Security"*. AI sẽ tự động kích hoạt cả `get_weather` và `search_documents` trong cùng một lượt xử lý.
3. **Cơ chế Fallback thông minh:** Khi chưa cấu hình OpenAI API key hoặc khi mất mạng, hệ thống tự động fallback:
   - Các câu hỏi thời tiết/thời gian: Gọi trực tiếp `WeatherService` / `TimeService`.
   - Các câu hỏi học liệu: Chuyển sang Semantic Retrieval hoặc Structured Search chống gián đoạn.
4. **Bảo mật tuyệt đối:** API key được nạp từ biến môi trường (`.env` / `application.properties`), hoàn toàn không lộ ra frontend.

### 7.3. Lệnh Kiểm Thử Tự Động

```bash
# Kiểm thử toàn diện RAG + Tool Calling Assistant
./mvnw.cmd test "-Dtest=ToolCallingAssistantTest,DocumentAssistantServiceTest,RagPipelineTest,ContextBuilderTest"
```

