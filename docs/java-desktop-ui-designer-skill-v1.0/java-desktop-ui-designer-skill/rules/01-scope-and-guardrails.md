# Scope, Boundaries & Guardrails
# Phạm vi, ranh giới và nguyên tắc bảo vệ

## 1. Source of truth / Nguồn chuẩn

**Vietnamese:** Ứng dụng Python/PyWebView hiện tại là nguồn tham chiếu chính cho chức năng, cấu trúc điều hướng và hành vi. Không được coi HTML/CSS là kiến trúc bắt buộc phải sao chép sang JavaFX.

**English:** The existing Python/PyWebView application is the primary reference for capabilities, navigation, and behavior. HTML/CSS implementation details are not required to be copied into JavaFX.

## 2. Separate UI from logic / Tách UI khỏi logic

During discovery, read source code to understand behavior. During prototype implementation, do not connect real services, databases, credentials, file mutations, or production APIs.

Trong giai đoạn khảo sát, được đọc mã nguồn để hiểu hành vi. Trong giai đoạn prototype, không kết nối dịch vụ thật, database, thông tin xác thực, thao tác ghi dữ liệu thật hoặc API production.

## 3. No silent feature loss / Không âm thầm loại bỏ chức năng

Every discovered visible or reachable UI action must be represented in the component catalog and traceability map. If an action is intentionally omitted or consolidated, record the reason and obtain user approval.

Mọi thao tác giao diện được phát hiện, kể cả thao tác nằm trong menu hoặc trạng thái phụ, phải có trong danh mục component và bảng truy vết. Nếu muốn bỏ hoặc gộp, phải ghi rõ lý do và xin duyệt.

## 4. Evidence labels / Nhãn mức độ bằng chứng

Use one of these values for every important behavior:

- `observed`: directly observed in the running application.
- `code_verified`: confirmed from event handler/backend code.
- `inferred`: plausible interpretation, not fully verified.
- `unknown`: not established.

Never present `inferred` or `unknown` as confirmed behavior.

## 5. Change control / Kiểm soát thay đổi

- No backend rewrite.
- No database schema changes.
- No business-rule changes.
- No unrelated dependency or framework migration.
- No deletion of existing UI features.
- No replacement of Monaco or other editor technology unless explicitly requested.
- Keep each screen implementation isolated and reviewable.

## 6. Approval gates / Điểm chờ phê duyệt

Gate A: approve screen inventory and migration order.  
Gate B: approve the individual screen specification and proposed redesign.  
Gate C: approve runnable JavaFX prototype.  
Gate D: approve integration of real application logic.

The agent must stop at each requested gate. Silence is not approval.
