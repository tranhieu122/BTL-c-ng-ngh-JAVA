# 🤖 EduBot Chatbot & RAG System - AI Knowledge Base

Thư mục này chứa bộ 3 tài liệu phân tích, đánh giá và lộ trình phát triển toàn diện cho **Phân hệ 3: AI Chatbot & RAG** của hệ thống **EduRepo**.

---

## 📑 Mục Lục Tài Liệu

| Tài liệu | Tiêu đề | Mục đích & Nội dung chính |
| :--- | :--- | :--- |
| **[README_7.md](./README_7.md)** | **Review Chi Tiết Kiến Trúc AI & RAG Pipeline** | Toàn bộ kiến trúc kỹ thuật hiện tại: Text Extraction, OCR Tesseract, Chunking thông minh, Vector Embedding, Cosine Similarity, Tool Calling, Interactive Citation Hover Preview. |
| **[README_8.md](./README_8.md)** | **Đánh Giá Định Vị Cấp Độ RAG (Maturity Assessment)** | Đối chiếu hệ thống với thang đo 5 cấp độ RAG (🟢 Biết RAG $\rightarrow$ 🟡 RAG Cơ bản $\rightarrow$ 🟠 RAG Tốt $\rightarrow$ 🔴 RAG Production $\rightarrow$ 🧠 RAG + Agent). Xác định vị trí hiện tại của EduRepo. |
| **[README_9.md](./README_9.md)** | **Lộ Trình Hoàn Thiện 100% (Production-Grade Roadmap)** | Danh sách những thành phần còn thiếu để đạt 100% chuẩn doanh nghiệp: pgvector/Vector DB, Reranking BGE, Streaming SSE, Semantic Caching, RAGAS Evaluation, Guardrails. |

---

## 🧭 Tóm Tắt Nhanh Vị Trí Hiện Tại Của EduRepo

```text
[🟢 Biết RAG] ────────► [🟡 RAG Cơ Bản] ────────► [🟠 RAG Tốt] ────[ EduRepo: 85% ]───► [🔴 RAG Production] ────────► [🧠 RAG + Agent]
 (2-3 ngày)               (1-2 tuần)               (3-5 tuần)                               (2-3 tháng)              (3-6 tháng)
                                                                                        (Đã có Tool Calling!)
```

* **Điểm mạnh nổi bật:** 
  - Pipeline xử lý PDF/Word kèm OCR tiếng Việt tự động khi duyệt tài liệu.
  - Chunking có cấu trúc theo từng mục/section kèm Overlap chống gãy ngữ cảnh.
  - Tích hợp **Interactive Hover Citation** (di chuột xem trước đoạn trích `[1]`, `[2]`).
  - Hỗ trợ **OpenAI Function/Tool Calling** (tra cứu học liệu, thời tiết thực tế, thời gian thực).
* **Trọng tâm cần nâng cấp để đạt 100%:** 
  - Streaming SSE (hiệu ứng chữ chạy từng từ).
  - Tích hợp pgvector thay vì so khớp vector trong bộ nhớ Java.
  - Reranker model để tăng độ chính xác của Top-K tài liệu.
