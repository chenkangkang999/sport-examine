package com.neusoft.health.controller;

import static com.neusoft.health.dto.ApiResponse.ok;
import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.*;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import com.neusoft.health.service.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  @Autowired private ApplicationAvailability availability;
  private final AccountService accounts;
  private final CatalogService catalog;
  private final OrderService orders;
  private final AdminService admin;
  private final Database db;

  public HealthController(
      AccountService accounts,
      CatalogService catalog,
      OrderService orders,
      AdminService admin,
      Database db) {
    this.accounts = accounts;
    this.catalog = catalog;
    this.orders = orders;
    this.admin = admin;
    this.db = db;
  }

  private Principal user(HttpSession s) {
    var p = (Principal) s.getAttribute("user");
    if (p == null) throw new BusinessException(401, "请先登录");
    accounts.user(p.userId());
    return p;
  }

  private Principal staff(HttpSession s) {
    var p = (Principal) s.getAttribute("staff");
    if (p == null) throw new BusinessException(401, "请先登录医生管理端");
    var current = accounts.staff(p.docId());
    return new Principal(
        null,
        p.docId(),
        text(current, "role"),
        current.get("hpId") == null ? null : ((Number) current.get("hpId")).longValue());
  }

  private Principal administrator(HttpSession s) {
    var p = staff(s);
    if (!"ADMIN".equals(p.role())) throw new BusinessException(403, "仅管理员可以执行此操作");
    return p;
  }

  private void catalogIdentity(HttpSession s) {
    if (s.getAttribute("staff") != null) staff(s);
    else user(s);
  }

  @GetMapping("/tijian/auth/session")
  public Object userSession(HttpSession s) {
    var result = new HashMap<String, Object>();
    result.put("csrfToken", s.getAttribute("csrf"));
    var p = (Principal) s.getAttribute("user");
    result.put("user", p == null ? null : accounts.user(p.userId()));
    return ok(result);
  }

  @GetMapping("/tijiancms/auth/session")
  public Object staffSession(HttpSession s) {
    var result = new HashMap<String, Object>();
    result.put("csrfToken", s.getAttribute("csrf"));
    var p = (Principal) s.getAttribute("staff");
    result.put("staff", p == null ? null : accounts.staff(p.docId()));
    return ok(result);
  }

  @PostMapping("/tijian/users/getUsersByUserIdByPass")
  public Object login(@RequestBody Map<String, Object> p, HttpServletRequest r) {
    var principal = accounts.login(p, false);
    r.changeSessionId();
    r.getSession().setAttribute("user", principal);
    return ok(accounts.user(principal.userId()));
  }

  @PostMapping("/tijian/users/saveUsers")
  public Object register(@RequestBody Map<String, Object> p, HttpServletRequest r) {
    var principal = accounts.register(p);
    r.changeSessionId();
    r.getSession().setAttribute("user", principal);
    return ok(accounts.user(principal.userId()));
  }

  @PostMapping("/tijiancms/doctor/getDoctorByCodeByPass")
  public Object doctorLogin(@RequestBody Map<String, Object> p, HttpServletRequest r) {
    var principal = accounts.login(p, true);
    r.changeSessionId();
    r.getSession().setAttribute("staff", principal);
    return ok(accounts.staff(principal.docId()));
  }

  @PostMapping("/tijian/auth/logout")
  public Object userLogout(HttpSession s) {
    s.removeAttribute("user");
    return ok(true);
  }

  @PostMapping("/tijiancms/auth/logout")
  public Object staffLogout(HttpSession s) {
    s.removeAttribute("staff");
    return ok(true);
  }

  @GetMapping("/tijian/users/me")
  public Object me(HttpSession s) {
    return ok(accounts.user(user(s).userId()));
  }

  @PostMapping("/tijian/users/updateProfile")
  public Object profile(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(accounts.updateProfile(user(s).userId(), p));
  }

  @PostMapping("/tijian/users/changePassword")
  public Object password(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(accounts.changePassword(user(s).userId(), p));
  }

  @GetMapping("/tijian/users/getUsersById")
  public Object registered(@RequestParam String userId, HttpSession s) {
    user(s);
    return ok(db.one("SELECT userId FROM users WHERE userId=#{p.id}", "id", userId) != null);
  }

  @GetMapping("/tijian/persons/list")
  public Object persons(HttpSession s) {
    return ok(accounts.persons(user(s).userId()));
  }

  @PostMapping("/tijian/persons/save")
  public Object personSave(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(accounts.savePerson(user(s).userId(), p));
  }

  @PostMapping("/tijian/persons/remove")
  public Object personRemove(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(accounts.removePerson(user(s).userId(), p));
  }

  @GetMapping("/tijian/hospital/listHospital")
  public Object hospitals(HttpSession s) {
    catalogIdentity(s);
    return ok(catalog.hospitals());
  }

  @GetMapping("/tijian/hospital/getHospitalById")
  public Object hospital(@RequestParam long hpId, HttpSession s) {
    catalogIdentity(s);
    return ok(catalog.hospital(hpId));
  }

  @GetMapping("/tijian/setmeal/listSetmealByType")
  public Object meals(
      @RequestParam(required = false) Long hpId,
      @RequestParam(required = false) Integer type,
      HttpSession s) {
    user(s);
    return ok(catalog.meals(hpId, type));
  }

  @GetMapping("/tijian/setmeal/getSetmealById")
  public Object meal(
      @RequestParam long smId, @RequestParam(required = false) Long hpId, HttpSession s) {
    user(s);
    return ok(catalog.meal(smId, hpId));
  }

  @GetMapping("/tijian/calendar/listAppointmentCalendar")
  public Object calendar(
      @RequestParam long hpId, @RequestParam int year, @RequestParam int month, HttpSession s) {
    user(s);
    return ok(catalog.calendar(hpId, year, month));
  }

  @PostMapping("/tijian/orders/saveOrders")
  public Object create(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(orders.create(user(s).userId(), p));
  }

  @GetMapping("/tijian/orders/listOrdersByUserId")
  public Object mine(
      @RequestParam(required = false) Integer state,
      @RequestParam(required = false) Long personId,
      HttpSession s) {
    return ok(orders.userOrders(user(s).userId(), state, personId));
  }

  @GetMapping("/tijian/orders/getOrdersByUserId")
  public Object hasActive(HttpSession s) {
    return ok(orders.userOrders(user(s).userId(), 1, null).size());
  }

  @GetMapping("/tijian/orders/getOrdersById")
  public Object myOrder(@RequestParam long orderId, HttpSession s) {
    return ok(orders.owned(user(s).userId(), orderId));
  }

  @PostMapping("/tijian/orders/removeOrders")
  public Object cancel(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(orders.cancel(user(s).userId(), number(p, "orderId")));
  }

  @GetMapping("/tijian/reports/list")
  public Object myReports(@RequestParam(required = false) Long personId, HttpSession s) {
    return ok(orders.userOrders(user(s).userId(), 2, personId));
  }

  @GetMapping("/tijian/reports/detail")
  public Object myReport(@RequestParam long orderId, HttpSession s) {
    return ok(orders.userReport(user(s).userId(), orderId));
  }

  @GetMapping("/tijian/ciReport/listCiReport")
  public Object userItems(@RequestParam long orderId, HttpSession s) {
    orders.userReport(user(s).userId(), orderId);
    return ok(orders.report(orderId).get("items"));
  }

  @GetMapping("/tijian/overallResult/listOverallResultByOrderId")
  public Object userConclusions(@RequestParam long orderId, HttpSession s) {
    orders.userReport(user(s).userId(), orderId);
    return ok(orders.report(orderId).get("conclusions"));
  }

  @GetMapping("/tijiancms/orders/listOrders")
  public Object listOrders(@RequestParam Map<String, Object> p, HttpSession s) {
    return ok(orders.list(staff(s), p));
  }

  @GetMapping("/tijiancms/orders/getOrdersById")
  public Object staffOrder(@RequestParam long orderId, HttpSession s) {
    return ok(orders.scoped(staff(s), orderId));
  }

  @GetMapping("/tijiancms/setmeal/listSetmeal")
  public Object allMeals(HttpSession s) {
    staff(s);
    return ok(catalog.meals(null, null));
  }

  @GetMapping("/tijiancms/ciReport/listCiReport")
  public Object report(@RequestParam long orderId, HttpSession s) {
    orders.scoped(staff(s), orderId);
    return ok(orders.report(orderId));
  }

  @PostMapping("/tijiancms/ciReport/createReportTemplate")
  public Object template(@RequestBody Map<String, Object> p, HttpSession s) {
    long id = number(p, "orderId");
    orders.scoped(staff(s), id);
    return ok(orders.report(id));
  }

  @PostMapping("/tijiancms/ciDetailedReport/updateCiDetailedReport")
  public Object saveDetails(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(orders.saveDetails(staff(s), p));
  }

  @GetMapping("/tijiancms/overallResult/listOverallResultByOrderId")
  public Object conclusions(@RequestParam long orderId, HttpSession s) {
    orders.scoped(staff(s), orderId);
    return ok(orders.report(orderId).get("conclusions"));
  }

  @PostMapping({
    "/tijiancms/overallResult/saveOverallResult",
    "/tijiancms/overallResult/updateOverallResult"
  })
  public Object saveConclusion(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(orders.saveConclusion(staff(s), p));
  }

  @PostMapping("/tijiancms/overallResult/removeOverallResult")
  public Object removeConclusion(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(orders.removeConclusion(staff(s), p));
  }

  @PostMapping("/tijiancms/orders/updateOrdersState")
  public Object archive(@RequestBody Map<String, Object> p, HttpSession s) {
    return ok(orders.archive(staff(s), p));
  }

  @GetMapping("/tijiancms/admin/hospital-setmeals")
  public Object hospitalMeals(@RequestParam long hpId, HttpSession s) {
    administrator(s);
    return ok(admin.hospitalMeals(hpId));
  }

  @PostMapping("/tijiancms/admin/hospital-setmeals/save")
  public Object hospitalMealsSave(@RequestBody Map<String, Object> p, HttpSession s) {
    administrator(s);
    return ok(admin.saveHospitalMeals(p));
  }

  @GetMapping("/tijiancms/admin/schedules")
  public Object schedules(
      @RequestParam long hpId, @RequestParam String from, @RequestParam String to, HttpSession s) {
    administrator(s);
    return ok(catalog.schedules(hpId, date(from), date(to)));
  }

  @PostMapping("/tijiancms/admin/schedules/save")
  public Object schedulesSave(@RequestBody Map<String, Object> p, HttpSession s) {
    administrator(s);
    return ok(admin.saveSchedules(p));
  }

  @GetMapping("/tijiancms/admin/{resource}")
  public Object resources(@PathVariable String resource, HttpSession s) {
    administrator(s);
    return ok(admin.list(resource));
  }

  @PostMapping("/tijiancms/admin/{resource}/save")
  public Object resourceSave(
      @PathVariable String resource, @RequestBody Map<String, Object> p, HttpSession s) {
    return ok(admin.save(resource, p, administrator(s).docId()));
  }

  @PostMapping("/tijiancms/admin/{resource}/disable")
  public Object resourceDisable(
      @PathVariable String resource, @RequestBody Map<String, Object> p, HttpSession s) {
    return ok(admin.disable(resource, number(p, "id"), administrator(s).docId()));
  }

  @GetMapping("/health")
  public Object health() {
    return ok(Map.of("application", "HealthProject", "status", "UP"));
  }

  @GetMapping("/health/ready")
  public ResponseEntity<ApiResponse> ready() {
    boolean ready = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
    return ResponseEntity.status(ready ? 200 : 503)
        .body(new ApiResponse(ready ? 0 : 503, ready ? "服务已就绪" : "正在初始化", Map.of("ready", ready)));
  }
}
