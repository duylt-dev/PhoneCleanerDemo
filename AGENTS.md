# PhoneCleanerDemo — Codex

## Đọc trước khi triển khai

1. Đọc `.claude/CLAUDE.md`, rồi `LLM.md` (đặc biệt §2, §4–§12).
2. Đọc phần liên quan trong `docs/android-mvi-best-practices.md`,
   `docs/system-architecture.md` và `docs/screens/`.
3. Áp dụng `.claude/rules/development-rules.md`, `primary-workflow.md` và
   plan/phase/report hiện có trong `plans/`.
4. Khi phân công, đọc `.claude/rules/orchestration-protocol.md` và role tương ứng
   trong `.claude/agents/`. Khi sửa tài liệu, đọc `documentation-management.md`.

Các file trên là nguồn quy tắc chung cho Claude và Codex; không sao chép thành
hai bộ quy tắc độc lập. Xác nhận quyết định sản phẩm/phạm vi còn chưa rõ trước
khi triển khai phần phụ thuộc. Quyết định user đã chốt có hiệu lực xuyên lượt;
không hỏi lại. Tiếp tục các phần độc lập đã được cho phép.

## Quy tắc dự án được ưu tiên

- Kotlin dùng `PascalCase.kt`; resource Android dùng `snake_case`; quy tắc
  kebab-case chung chỉ áp dụng cho tài liệu và artifact phù hợp.
- Giữ package `com.pion.phonecleaner` và module graph của `LLM.md`.
  Ví dụ từ dự án khác hoặc đường dẫn tài liệu không tồn tại không yêu cầu
  tạo thêm kiến trúc, tablet shell hay feature ngoài phạm vi.
- `:domain`/`:core:common` không phụ thuộc Android/Compose. Feature chỉ thấy
  domain/core, không thấy data hoặc feature khác.
- Screen ViewModel kế thừa `MviViewModel`; action đi qua `onIntent`.
  State/Intent/Effect ở `XContract.kt`. Coroutine qua `launchSafely` hoặc
  `collectSafely`; ném lại `CancellationException`, giữ structured concurrency.
- Navigation là Effect, Route thu thập bằng lifecycle-aware `collect`.
  Screen stateless, không nhận ViewModel. Dùng design token và string EN/VI.
- Koin constructor injection; một chủ sở hữu cho mỗi shared binding;
  assembly ở app. Giữ các quyết định wording và feature availability.
- Fake viết tay trong test là quy ước của `LLM.md` §9. Không dùng hành vi giả
  trong production, placeholder assertions hoặc kết quả kiểm thử bịa đặt.
- Kiểm chứng trạng thái plan/lịch sử với source và kết quả chạy thực tế.
  Giữ ngoại lệ đã ghi ở `LLM.md` §11–§12; không tự mở rộng phạm vi để dọn chúng.

## Ánh xạ Claude sang Codex

- Read/Glob/Grep/Bash dùng file tools, `rg`/`rg --files`, shell của phiên Codex.
  Edit/Write dùng `apply_patch` hoặc công cụ sửa file tương ứng.
- Task/SendMessage dùng subagent/collaboration tools nếu có. Truyền work
  context, reports path, plans path, phase và quyền sở hữu file cho từng agent.
  Các writer không sửa chồng file; được đọc dependency để tích hợp.
- Đọc `.claude/skills/<name>/SKILL.md` trước khi áp dụng skill phù hợp.
  Tên `ck:` và slash command là tên gọi bên Claude; dùng capability thực có.
  Không tuyên bố chạy một tool/skill runtime không tồn tại.
- Role Markdown là hướng dẫn công việc; frontmatter model/tools/memory của
  Claude không cấu hình Codex. Giữ model và permissions của phiên hiện tại.
- Truyền active plan rõ ràng; `set-active-plan.cjs` không lưu trạng thái nếu
  thiếu `CK_SESSION_ID`.
- Không chuyển `settings.json`, statusline hoặc Anthropic usage hooks thành
  Codex hooks. Không sao chép secret/env value vào cấu hình hay báo cáo.
  Khám phá MCP đang có trước khi dùng; tham chiếu Markdown không cài MCP.

## Hoàn tất và kiểm chứng

Tiếp tục plan hiện có. Dùng các vai trò planning, implementation, testing,
review và docs theo primary workflow khi có công việc độc lập hữu ích.
Compile mã thay đổi bằng Gradle wrapper; trước khi đánh dấu hoàn tất chạy
build, unit test, lint và device test phù hợp với hành vi nền tảng đã sửa.
Thực hiện checklist MVI §9 trong phạm vi kiến trúc thực của dự án.

Ghi lệnh, kết quả và hạn chế thực tế. Check được tài liệu đề xuất nhưng chưa
có implementation không được ghi là đã pass. Không che lỗi bằng baseline
hoặc bỏ assertion. Đồng bộ phase/report, `LLM.md` và tài liệu bị ảnh hưởng
cùng thay đổi; phân biệt hoàn tất code và device gate chưa chạy.

Giữ nguyên công việc chưa commit. Không tự commit, push, publish hoặc viết
lại Git history. Cấu hình này chỉ áp dụng trong dự án; không tự sửa config
Codex toàn máy hoặc bộ nhớ toàn cục.
