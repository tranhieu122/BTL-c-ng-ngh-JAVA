# HƯỚNG DẪN VÀ ĐỀ XUẤT CÔNG CỤ AUTOMATION TEST CHO DỰ ÁN EDUREPO

> **Dự án:** EduRepo - Hệ thống Quản lý và Lưu trữ Học liệu Trực tuyến tích hợp Trợ lý Trí tuệ Nhân tạo (RAG Chatbot)  
> **Công nghệ lõi:** Java 25, Spring Boot 3.5.x, Spring Security 6, Thymeleaf, MySQL 8, Flyway, Server-Sent Events (SSE), AI RAG (LangChain4j / PDFBox / Gemini / Ollama).  
> **Tài liệu tham khảo:** [docs/EDUBOT-RAG.md](file:///c:/Users/thesh/BLTcongngheJAVA/EduRepo/docs/EDUBOT-RAG.md), [docs/Ke-hoach-kiem-thu-EduRepo.docx](file:///c:/Users/thesh/BLTcongngheJAVA/EduRepo/docs/Ke-hoach-kiem-thu-EduRepo.docx).

---

## 1. ĐẶC THÙ HỆ THỐNG VÀ BÀI TOÁN KIỂM THỬ CỦA EDUREPO

Hệ thống **EduRepo** không chỉ là một ứng dụng Web CRUD thông thường mà sở hữu nhiều cơ chế kỹ thuật phức tạp:
1. **Kiến trúc Server-Side Rendering (Thymeleaf) kết hợp AJAX/SSE Client:** Giao diện rendered từ server nhưng có các module tương tác cao như Chatbot widget thu nhỏ, trung tâm thông báo thời gian thực, upload/preview ảnh đại diện.
2. **Luồng dữ liệu thời gian thực (SSE & WebSocket):** Endpoint `/api/assistant/chat` phát phản hồi streaming từng token (`text/event-stream`), các kênh thông báo đẩy trực tiếp không tải lại trang.
3. **Quy trình nghiệp vụ tài liệu đa tầng:** Tải lên tệp đa định dạng (PDF/DOCX/PPTX, dung lượng tới 200MB) -> Phân mảnh (Chunking) -> Tạo vector nhúng -> Phê duyệt Rubric nhiều tiêu chí -> Cấp quyền Submitter -> Xuất bản công khai -> Tải về bảo mật.
4. **Bảo mật và Kiểm toán (OWASP Security):** Chống brute-force OTP, chống Path Traversal khi upload, Rate Limiting phân cấp, phân quyền 5 vai trò (Guest, User, Submitter, Reviewer, Admin), Audit Log truy vết.

Để đảm bảo hệ thống vận hành bền bỉ và đạt điểm tối đa khi báo cáo bài tập lớn / đồ án tốt nghiệp, chiến lược kiểm thử tự động cần được xây dựng theo mô hình **Kim tự tháp kiểm thử (Test Pyramid)**.

---

## 2. MÔ HÌNH KIM TỰ THÁP KIỂM THỬ CHO EDUREPO

```
                     / \
                    /   \
                   / E2E \           <-- Playwright / Selenium (UI, Cross-browser)
                  /-------\
                 /  API &  \         <-- REST Assured, Testcontainers MySQL
                / Perf/Sec  \        <-- k6 (Load test), OWASP ZAP (Security)
               /-------------\
              /  Integration  \      <-- Spring Boot Test, MockMvc, @DataJpaTest
             /-----------------\
            /     Unit Test     \    <-- JUnit 5, Mockito, AssertJ (Fast, Isolated)
           /---------------------\
```

---

## 3. CÁC CÔNG CỤ AUTOMATION TEST PHÙ HỢP NHẤT CHO EDUREPO

### 3.1. Tầng Unit Test & Mocking (Đang có sẵn và tối ưu)

| Thành phần | Công nghệ đề xuất | Trạng thái hiện tại | Đánh giá & Ứng dụng |
| :--- | :--- | :---: | :--- |
| **Test Runner** | **JUnit 5 (Jupiter)** | Đã tích hợp | Chuẩn công nghiệp cho Java hiện đại, hỗ trợ `@ParameterizedTest`, dynamic test, lifecycle hooks. |
| **Mocking Framework** | **Mockito 5** | Đã tích hợp | Giả lập các service ngoài (Ollama/Gemini API, JavaMailSender, File System) để test logic cô lập. |
| **Fluent Assertions** | **AssertJ** | Đã tích hợp | Cung cấp cú pháp assertion tự nhiên (`assertThat(result).isNotNull().hasSize(3)`), thông báo lỗi rõ ràng. |

#### Ví dụ kiểm thử đơn vị logic Chunking:
```java
@Test
@DisplayName("Đảm bảo DocumentChunker chia đoạn chuẩn theo kích thước và overlap")
void shouldChunkTextCorrectly() {
    DocumentChunker chunker = new DocumentChunker(500, 50);
    List<String> chunks = chunker.chunk("Nội dung tài liệu mẫu rất dài...");
    
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).length()).isLessThanOrEqualTo(500);
}
```

---

### 3.2. Tầng Integration & Database Test (Tích hợp dịch vụ)

#### A. Spring Boot Test + MockMvc (Mock Controller)
- **Ưu điểm:** Khởi động Web Application Context mà không cần bật server HTTP thật; chạy siêu nhanh; kiểm tra phân quyền Spring Security (`@WithMockUser`), CSRF token, validation annotations `@Valid`.
- **Ứng dụng trong EduRepo:** Đã triển khai rất tốt trong [BackendRegressionTest.java](file:///c:/Users/thesh/BLTcongngheJAVA/EduRepo/src/test/java/com/hieu/edurepo/controller/BackendRegressionTest.java) và [AuditFixRegressionTest.java](file:///c:/Users/thesh/BLTcongngheJAVA/EduRepo/src/test/java/com/hieu/edurepo/controller/AuditFixRegressionTest.java).

#### B. Testcontainers (Chạy MySQL thật trong Docker Container)
- **Vấn đề giải quyết:** Hiện tại test dùng database H2 in-memory. H2 có cú pháp SQL và dialect khác MySQL 8 (đặc biệt là Fulltext search, các hàm JSON, Flyway migration script V1-V8).
- **Giải pháp:** Sử dụng thư viện `testcontainers-mysql` (đã có trong `pom.xml`).
- **Ưu điểm:** Tự động kéo container Docker MySQL 8 lên khi chạy test và tự dọn dẹp khi test xong. Đảm bảo 100% môi trường test đồng nhất với Production.

```java
@Testcontainers
@SpringBootTest
class MysqlRealIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("edurepo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    void shouldRunFlywayMigrationCleanly() {
        // Kiểm tra chạy sạch 8 file Flyway trên MySQL thật
    }
}
```

---

### 3.3. Tầng End-to-End (E2E) & UI Browser Testing: 👑 **MICROSOFT PLAYWRIGHT**

Đối với một website có Server-Side Rendering (Thymeleaf) kết hợp AJAX, Modal Popup, Widget thu nhỏ và **đặc biệt là Server-Sent Events (SSE)** như EduRepo, **Playwright là lựa chọn số 1 vượt trội hoàn toàn so với Selenium hay Cypress**.

#### So sánh Playwright với Selenium & Cypress:

| Tiêu chí | **Microsoft Playwright (Khuyên dùng)** | Selenium WebDriver | Cypress |
| :--- | :--- | :--- | :--- |
| **Ngôn ngữ hỗ trợ** | **Java**, TypeScript, JavaScript, Python | Java, C#, Python... | Chỉ JavaScript / TypeScript |
| **Cơ chế chờ (Auto-wait)** | **Tự động chờ element sẵn sàng**, không bao giờ bị lỗi `ElementNotFound` hay `StaleElement` | Phải viết `Thread.sleep()` hoặc `WebDriverWait` thủ công rườm rà | Tự động chờ |
| **Hỗ trợ SSE & WebSocket** | **Native**, lắng nghe và intercept trực tiếp luồng streaming mạng | Cực kỳ khó bắt gói tin SSE | Hạn chế đa tab / streaming |
| **Kiểm thử đa phiên (Multi-context)** | Tạo 2 browser context độc lập trong 1s (Test Chat giữa Submitter & Reviewer cùng lúc) | Phải bật 2 trình duyệt riêng biệt rất nặng | Không hỗ trợ nhiều trình duyệt cùng lúc |
| **Tốc độ thực thi** | Nhanh hơn 3 - 5 lần nhờ CDP (Chrome DevTools Protocol) | Chậm do qua WebDriver bridge trung gian | Trung bình |
| **Báo cáo (Tracing & Video)** | Tự động quay video, chụp ảnh từng step, ghi lại timeline DOM | Cần cài thêm plugin bên ngoài | Có video |

#### Kịch bản kiểm thử mẫu bằng Playwright Java cho EduRepo:

Thêm dependency vào `pom.xml`:
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.49.0</version>
    <scope>test</scope>
</dependency>
```

Kịch bản E2E: Đăng nhập -> Mở widget Chatbot -> Hỏi câu hỏi -> Kiểm tra phản hồi streaming:
```java
public class PlaywrightChatbotE2ETest {
    @Test
    void testChatbotStreamingFlow() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            // 1. Truy cập trang chủ
            page.navigate("http://localhost:8080/login");

            // 2. Đăng nhập người dùng
            page.fill("input[name='username']", "sinhvien@edurepo.edu.vn");
            page.fill("input[name='password']", "EduRepo@2026");
            page.click("button[type='submit']");

            // Chờ chuyển hướng vào trang tài liệu
            page.waitForURL("**/documents**");

            // 3. Bấm mở widget Chatbot AI ở góc màn hình
            page.click("#btn-toggle-assistant");
            assertThat(page.isVisible("#assistant-chat-window")).isTrue();

            // 4. Nhập câu hỏi và gửi
            page.fill("#assistant-input", "Tài liệu này giới thiệu về những nội dung gì?");
            page.click("#btn-assistant-send");

            // 5. Chờ phản hồi streaming (tự động đợi bóng thoại trợ lý xuất hiện)
            Locator botMessage = page.locator(".message.assistant:last-child .message-content");
            botMessage.waitFor(new Locator.WaitForOptions().setTimeout(10000));

            // Kiểm tra nội dung nhận được không rỗng
            assertThat(botMessage.innerText().length()).isGreaterThan(20);

            // Chụp ảnh bằng chứng
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("target/chatbot-success.png")));
        }
    }
}
```

---

### 3.4. Tầng API Automation Testing: **REST ASSURED**

Trong khi MockMvc chỉ giả lập nội bộ Spring, **REST Assured** là công cụ kiểm thử black-box gửi request HTTP thật tới máy chủ đang chạy.

- **Thế mạnh:**
  - Kiểm thử upload tệp tin multipart lớn (PDF 50MB - 200MB).
  - Kiểm thử tải tệp tin nhị phân và kiểm tra header `Content-Disposition`.
  - Kiểm thử chuỗi API: `Login -> Lấy Cookie/Session -> Gọi API upload -> Lấy ID -> Gọi API Duyệt`.
  - Cú pháp chuẩn `given() - when() - then()`.

#### Ví dụ kiểm tra API tải tài liệu có token bảo vệ:
```java
@Test
void testSecureDownloadEndpoint() {
    given()
        .cookie("JSESSIONID", authenticatedSessionId)
        .queryParam("token", "valid-one-time-token-123")
    .when()
        .get("/documents/15/download")
    .then()
        .statusCode(200)
        .header("Content-Type", "application/pdf")
        .header("Content-Disposition", containsString("filename="));
}
```

---

### 3.5. Tầng Performance & Load Testing: **k6 (Grafana)**

Hệ thống EduRepo tích hợp tính năng RAG AI và streaming token, đây là khu vực tiêu tốn tài nguyên nhất (CPU, bộ nhớ, giới hạn Rate Limit).

- **Công cụ đề xuất:** **k6** (viết kịch bản bằng JavaScript, chạy bằng binary Go siêu nhẹ, ăn ít RAM hơn JMeter gấp 10 lần).
- **Mục tiêu kiểm thử:**
  1. Kiểm tra giới hạn tần suất: Gửi dồn dập 20 request/phút xem hệ thống có trả về `429 Too Many Requests` như thiết kế không.
  2. Đo lường chỉ số **TTFT (Time To First Token)**: Đo độ trễ từ lúc sinh viên bấm gửi câu hỏi đến khi token đầu tiên của AI trả về trình duyệt.
  3. Đo khả năng chịu tải: Giả lập 50 sinh viên cùng tải đề thi và hỏi đáp tài liệu đồng thời.

#### Kịch bản mẫu kiểm thử tải Rate Limit với k6 (`load-test.js`):
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,            // 10 người dùng ảo đồng thời
  duration: '30s',    // Chạy trong 30 giây
};

export default function () {
  const url = 'http://localhost:8080/api/assistant/chat';
  const payload = JSON.stringify({
    documentId: 1,
    message: 'Giải thích khái niệm tính đa hình trong Java'
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': 'JSESSIONID=demo-session-token'
    },
  };

  const res = http.post(url, payload, params);

  // Đánh giá tỷ lệ thành công 200 và phản hồi 429 khi vượt ngưỡng
  check(res, {
    'Trạng thái 200 hoặc 429': (r) => r.status === 200 || r.status === 429,
    'Thời gian phản hồi < 2s': (r) => r.timings.duration < 2000,
  });

  sleep(1);
}
```

---

### 3.6. Tầng AI / RAG Quality Evaluation: **RAGAS / DEEPEVAL**

> 💡 **Điểm nhấn tạo sự khác biệt:** Dự án có tính năng RAG Assistant tra cứu học liệu. Nếu chỉ test web thông thường thì chưa đánh giá được AI trả lời đúng hay bịa đặt (*Hallucination*).

**Ragas (Retrieval Augmented Generation Assessment)** là framework chuẩn quốc tế đánh giá tự động hệ thống RAG qua 4 chỉ số cốt lõi:
1. **Faithfulness (Độ trung thực):** Câu trả lời của AI có dựa trên đúng đoạn trích trong giáo trình không (phát hiện bịa đặt).
2. **Answer Relevance (Độ liên quan):** Câu trả lời có đúng trọng tâm câu hỏi của sinh viên không.
3. **Context Precision (Độ chuẩn xác trích dẫn):** Các đoạn trích dẫn được xếp hạng cao có thực sự chứa câu trả lời không.
4. **Context Recall (Độ bao phủ trích dẫn):** Có bỏ sót thông tin quan trọng nào từ tài liệu không.

*Cách triển khai:* Viết một script Python chạy trong `scripts/eval_rag.py` lấy dataset câu hỏi mẫu từ các giáo trình nạp sẵn, chấm điểm RAG pipeline và xuất báo cáo radar chart.

---

### 3.7. Tầng Security & Code Coverage

1. **Báo cáo độ phủ mã nguồn: JaCoCo (Java Code Coverage Library)**
   - Tích hợp trực tiếp vào Maven (`pom.xml`).
   - Tự động xuất báo cáo HTML trực quan đo % độ phủ dòng lệnh (Line Coverage) và rẽ nhánh (Branch Coverage) của toàn bộ 243 test case.
   - Thêm plugin vào `pom.xml`:
   ```xml
   <plugin>
       <groupId>org.jacoco</groupId>
       <artifactId>jacoco-maven-plugin</artifactId>
       <version>0.8.12</version>
       <executions>
           <execution>
               <goals>
                   <goal>prepare-agent</goal>
               </goals>
           </execution>
           <execution>
               <id>report</id>
               <phase>test</phase>
               <goals>
                   <goal>report</goal>
               </goals>
           </execution>
       </executions>
   </plugin>
   ```
   - Chạy lệnh: `mvn clean test jacoco:report` ➔ Báo cáo hiển thị tại `target/site/jacoco/index.html`.

2. **Quét lỗ hổng bảo mật: OWASP Dependency-Check**
   - Quét tự động toàn bộ thư viện bên thứ 3 trong `pom.xml` để phát hiện lỗ hổng CVE đã biết.

---

## 4. BẢNG TỔNG HỢP VÀ ĐỀ XUẤT CÔNG CỤ TỐI ƯU CHO DỰ ÁN

| Phân loại kiểm thử | Công cụ số 1 (Nên dùng ngay) | Công cụ thay thế / Mở rộng | Lý do lựa chọn cho EduRepo |
| :--- | :--- | :--- | :--- |
| **Unit Testing** | **JUnit 5 + Mockito + AssertJ** | Spock Framework | Đang chạy rất tốt 243 test case, chuẩn mực của Java Spring Boot. |
| **Integration Web** | **Spring Boot Test (MockMvc)** | REST Assured | Nhanh, nhẹ, kiểm tra phân quyền Spring Security và CSRF tức thời. |
| **Database Integration** | **Testcontainers (MySQL 8)** | H2 In-Memory DB | Chạy database thật trong Docker, kiểm thử đúng 100% dialect MySQL và Flyway. |
| **E2E & UI Testing** | **Microsoft Playwright (Java)** | Selenium WebDriver | **Tốt nhất cho EduRepo:** Tự động bắt sự kiện SSE, modal, đa tab, quay video lỗi. |
| **Load & Stress Testing** | **k6 (Grafana)** | Apache JMeter | Nhẹ, dễ viết script kịch bản kiểm tra rate limit và streaming chat AI. |
| **RAG AI Evaluation** | **Ragas / DeepEval** | G-Eval | Chấm điểm tự động độ chính xác và trung thực của Chatbot RAG. |
| **Code Coverage** | **JaCoCo** | Cobertura | Xuất báo cáo % độ phủ code đẹp mắt, làm minh chứng cho báo cáo đề tài. |

---

## 5. LỘ TRÌNH TRIỂN KHAI KIỂM THỬ TỰ ĐỘNG (ROADMAP)

### Giai đoạn 1: Hoàn thiện nền tảng & Đo lường (Làm ngay)
- [x] Đã có 243 test case Unit & Integration chạy trên JUnit 5.
- [ ] Tích hợp **JaCoCo Maven Plugin** vào `pom.xml` để sinh báo cáo độ phủ mã nguồn (đạt mục tiêu > 80% coverage).
- [ ] Thiết lập **GitHub Actions CI Workflow** chạy tự động `mvn test` mỗi khi push code lên repository.

### Giai đoạn 2: Tự động hóa giao diện người dùng E2E
- [ ] Thêm thư viện **Playwright Java** vào `pom.xml`.
- [ ] Xây dựng 5 kịch bản E2E quan trọng nhất:
  1. *Đăng ký -> Nhận OTP -> Đăng nhập.*
  2. *Submitter nộp bài PDF -> Reviewer duyệt theo Rubric -> Xuất bản.*
  3. *Mở cửa sổ Chatbot -> Hỏi đáp giáo trình -> Nhận phản hồi streaming.*
  4. *Người dùng tìm kiếm tài liệu -> Lọc đa tiêu chí -> Đọc trực tuyến.*
  5. *Đổi mật khẩu -> Đồng bộ SSE đa tab.*

### Giai đoạn 3: Kiểm thử tải & Đánh giá AI RAG
- [ ] Viết kịch bản **k6** kiểm tra tải đồng thời 50 users và kiểm tra Rate Limiter HTTP 429.
- [ ] Viết script **Ragas** đánh giá độ trung thực (Faithfulness) của các câu trả lời giáo trình.

---

## 6. MẪU PIPELINE CI/CD TỰ ĐỘNG HÓA KIỂM THỬ (GITHUB ACTIONS)

Tạo tệp `.github/workflows/test-automation.yml` để hệ thống tự động chạy kiểm thử mỗi khi có commit mới:

```yaml
name: EduRepo Automation Testing Pipeline

on:
  push:
    branches: [ "main", "dev" ]
  pull_request:
    branches: [ "main" ]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_DATABASE: edurepo_test
          MYSQL_ROOT_PASSWORD: root
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping" --health-interval=10s --health-timeout=5s --health-retries=3

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
        cache: maven

    - name: Run Unit & Integration Tests with JaCoCo
      run: mvn clean test jacoco:report

    - name: Upload Test Coverage Report
      uses: actions/upload-artifact@v4
      with:
        name: jacoco-coverage-report
        path: target/site/jacoco/
```

---
*Tài liệu được khởi tạo và cấu trúc chuyên biệt cho đồ án hệ thống EduRepo.*
