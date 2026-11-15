package com.neusoft.health.controller;

import static com.neusoft.health.dto.ApiResponse.ok;
import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.service.*;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 新增体验接口仍由后端会话授权，并接入既有写请求 CSRF 拦截器。 */
@RestController
public class ExperienceController {
  private final AccountService accounts;
  private final ExperienceService experience;
  private final NotificationService notifications;

  public ExperienceController(
      AccountService accounts, ExperienceService experience, NotificationService notifications) {
    this.accounts = accounts;
    this.experience = experience;
    this.notifications = notifications;
  }

  private Principal user(HttpSession session) {
    var principal = (Principal) session.getAttribute("user");
    if (principal == null) throw new BusinessException(401, "请先登录");
    accounts.user(principal.userId());
    return principal;
  }

  private Principal staff(HttpSession session) {
    var principal = (Principal) session.getAttribute("staff");
    if (principal == null) throw new BusinessException(401, "请先登录医生管理端");
    var current = accounts.staff(principal.docId());
    return new Principal(
        null,
        principal.docId(),
        text(current, "role"),
        current.get("hpId") == null ? null : ((Number) current.get("hpId")).longValue());
  }

  private Principal administrator(HttpSession session) {
    var principal = staff(session);
    if (!"ADMIN".equals(principal.role())) throw new BusinessException(403, "仅管理员可以执行此操作");
    return principal;
  }

  @GetMapping("/tijian/dashboard")
  public Object dashboard(HttpSession session) {
    return ok(experience.dashboard(user(session).userId()));
  }

  @GetMapping("/tijian/notifications")
  public Object notifications(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      HttpSession session) {
    return ok(notifications.list(user(session).userId(), page, pageSize, unreadOnly));
  }

  @PostMapping("/tijian/notifications/read")
  public Object read(@RequestBody Map<String, Object> body, HttpSession session) {
    return ok(notifications.read(user(session).userId(), number(body, "id")));
  }

  @PostMapping("/tijian/notifications/read-all")
  public Object readAll(HttpSession session) {
    return ok(notifications.readAll(user(session).userId()));
  }

  @GetMapping("/tijian/reports/trends")
  public Object trends(@RequestParam long personId, HttpSession session) {
    return ok(experience.trends(user(session).userId(), personId));
  }

  @GetMapping("/tijian/orders/guidance")
  public Object guidance(@RequestParam long orderId, HttpSession session) {
    return ok(experience.orderGuidance(user(session).userId(), orderId));
  }

  @GetMapping("/tijiancms/admin/hospital-guidance")
  public Object hospitalGuidance(@RequestParam long hpId, HttpSession session) {
    administrator(session);
    return ok(experience.guidance(hpId));
  }

  @PostMapping("/tijiancms/admin/hospital-guidance/save")
  public Object saveGuidance(@RequestBody Map<String, Object> body, HttpSession session) {
    administrator(session);
    return ok(experience.saveGuidance(body));
  }

  @GetMapping("/tijiancms/workbench")
  public Object workbench(@RequestParam(required = false) Long hpId, HttpSession session) {
    return ok(experience.workbench(staff(session), hpId));
  }

  @GetMapping("/tijiancms/admin/statistics")
  public Object statistics(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long hpId,
      HttpSession session) {
    return ok(experience.statistics(administrator(session), from, to, hpId));
  }
}
