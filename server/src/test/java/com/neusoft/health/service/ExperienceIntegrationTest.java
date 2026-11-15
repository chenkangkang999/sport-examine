package com.neusoft.health.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.neusoft.health.HealthApplication;
import com.neusoft.health.config.DemoData;
import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 只连接独立测试库；新增账号、订单、医院和通知在每个用例结束后回滚。 */
@SpringBootTest(classes = HealthApplication.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Transactional
class ExperienceIntegrationTest {
  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    String url = System.getenv("TEST_DB_URL");
    if (url != null && !url.matches(".*[/]tijian_test([?].*)?$"))
      throw new IllegalStateException("集成测试仅允许独立 tijian_test 数据库");
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    registry.add("health.seed-enabled", () -> true);
  }

  @Autowired AccountService accounts;
  @Autowired OrderService orders;
  @Autowired ExperienceService experience;
  @Autowired NotificationService notifications;
  @Autowired Database db;
  @Autowired MockMvc mvc;
  private static final AtomicInteger serials = new AtomicInteger(501);
  private final Principal admin = new Principal(null, 3L, "ADMIN", null);

  String account() {
    int serial = serials.getAndAdd(2);
    String phone = "1399000" + String.format("%04d", serial);
    accounts.register(
        new HashMap<>(
            Map.of(
                "userId",
                phone,
                "password",
                "Health123!",
                "realName",
                "改进功能测试用户",
                "sex",
                1,
                "birthday",
                "1990-01-01",
                "identityCard",
                DemoData.identity("11010119900101" + serial))));
    return phone;
  }

  long hospital() {
    long hp =
        db.insert(
            "INSERT INTO hospital(name,address,telephone,businessHours,deadline)"
                + " VALUES('功能集成测试医院','测试路1号','000-0000','工作日','请联系机构')");
    db.exec("INSERT INTO hospital_setmeal(hpId,smId) VALUES(#{p.hp},1)", "hp", hp);
    return hp;
  }

  Map<String, Object> booking(String owner, long hp, LocalDate day) {
    db.exec(
        "INSERT INTO appointment_schedule(hpId,appointmentDate,capacity,used)"
            + " VALUES(#{p.hp},#{p.day},20,0) ON DUPLICATE KEY UPDATE"
            + " capacity=GREATEST(capacity,20)",
        "hp",
        hp,
        "day",
        day);
    long person = ((Number) accounts.persons(owner).get(0).get("personId")).longValue();
    return orders.create(
        owner,
        new HashMap<>(
            Map.of(
                "personId",
                person,
                "hpId",
                hp,
                "smId",
                1,
                "orderDate",
                day.toString(),
                "requestId",
                UUID.randomUUID().toString())));
  }

  long id(Map<String, Object> row) {
    return ((Number) row.get("orderId")).longValue();
  }

  Principal doctor(long hp) {
    return new Principal(null, 1L, "DOCTOR", hp);
  }

  void archive(Map<String, Object> row) {
    long id = id(row);
    var changes = new ArrayList<Map<String, Object>>();
    for (var detail : db.rows("SELECT * FROM cidetailedreport WHERE orderId=#{p.id}", "id", id)) {
      String value = detail.get("minrange") == null ? "175" : detail.get("minrange").toString();
      changes.add(Map.of("cidrId", detail.get("cidrId"), "value", value, "isError", 0));
    }
    var staff = doctor(((Number) row.get("hpId")).longValue());
    orders.saveDetails(staff, new HashMap<>(Map.of("orderId", id, "version", 0, "items", changes)));
    orders.saveConclusion(
        staff,
        new HashMap<>(
            Map.of("orderId", id, "version", 1, "title", "测试结论", "content", "仅用于验证归档流程。")));
    orders.archive(staff, new HashMap<>(Map.of("orderId", id, "version", 2)));
  }

  @Test
  void notificationEventsAreAtomicIdempotentAndPrivate() {
    String owner = account(), other = account();
    long hp = hospital();
    var order = booking(owner, hp, Values.today().plusDays(1));
    long id = id(order);
    var request =
        db.one("SELECT requestId FROM orders WHERE orderId=#{p.id}", "id", id).get("requestId");
    var retry =
        new HashMap<String, Object>(
            Map.of(
                "personId",
                order.get("personId"),
                "hpId",
                hp,
                "smId",
                1,
                "orderDate",
                order.get("orderDate").toString(),
                "requestId",
                request));
    orders.create(owner, retry);
    notifications.record(id, "BOOKED");
    assertEquals(1, notifications.unread(owner));
    var item = Values.maps(notifications.list(owner, 1, 20, false).get("items")).get(0);
    long notification = ((Number) item.get("id")).longValue();
    assertFalse((Boolean) item.get("isRead"));
    assertThrows(BusinessException.class, () -> notifications.read(other, notification));
    notifications.read(owner, notification);
    notifications.read(owner, notification);
    assertEquals(0, notifications.unread(owner));
    orders.cancel(owner, id);
    orders.cancel(owner, id);
    assertEquals(2L, ((Number) notifications.list(owner, 1, 20, false).get("total")).longValue());
    assertEquals(1L, ((Number) notifications.list(owner, 1, 20, true).get("total")).longValue());
    notifications.readAll(other);
    assertEquals(1, notifications.unread(owner));
    notifications.backfill();
    assertEquals(2L, ((Number) notifications.list(owner, 1, 20, false).get("total")).longValue());
    assertTrue(
        Values.maps(notifications.list(owner, 1, 20, false).get("items")).stream()
            .anyMatch(n -> n.get("type").equals("BOOKED") && Boolean.TRUE.equals(n.get("isRead"))));
    notifications.readAll(owner);
    assertEquals(0, notifications.unread(owner));
  }

  @Test
  void dashboardChoosesActiveAppointmentAndOnlyOwnedArchivedReport() {
    String owner = account(), other = account();
    long hp = hospital();
    var first = booking(owner, hp, Values.today().plusDays(1));
    archive(first);
    var active = booking(owner, hp, Values.today().plusDays(2));
    var otherOrder = booking(other, hp, Values.today().plusDays(3));
    archive(otherOrder);
    var dashboard = experience.dashboard(owner);
    assertEquals(
        id(active),
        ((Number) ((Map<?, ?>) dashboard.get("appointment")).get("orderId")).longValue());
    assertEquals(
        id(first),
        ((Number) ((Map<?, ?>) dashboard.get("latestReport")).get("orderId")).longValue());
    assertEquals(3L, ((Number) dashboard.get("unreadCount")).longValue());
    orders.cancel(owner, id(active));
    var recent = (Map<?, ?>) experience.dashboard(owner).get("appointment");
    assertEquals(id(active), ((Number) recent.get("orderId")).longValue());
  }

  @Test
  void trendsSeparatePeopleUnitsAndIncludeOnlyArchivedNumericResults() {
    String owner = account(), other = account();
    long hp = hospital();
    var first = booking(owner, hp, Values.today().plusDays(1));
    archive(first);
    var second = booking(owner, hp, Values.today().plusDays(2));
    archive(second);
    db.exec(
        "UPDATE cidetailedreport SET unit='kPa' WHERE orderId=#{p.id} AND sourceDetailId=1",
        "id",
        id(second));
    db.exec(
        "UPDATE cidetailedreport SET value='非数值历史记录' WHERE orderId=#{p.id} AND sourceDetailId=3",
        "id",
        id(second));
    var pending = booking(owner, hp, Values.today().plusDays(3));
    db.exec("UPDATE cidetailedreport SET value='999' WHERE orderId=#{p.id}", "id", id(pending));
    var another = booking(other, hp, Values.today().plusDays(1));
    archive(another);
    long person = ((Number) first.get("personId")).longValue();
    assertThrows(BusinessException.class, () -> experience.trends(other, person));
    var series = Values.maps(experience.trends(owner, person).get("series"));
    assertEquals(7, series.size());
    assertEquals(
        2,
        series.stream().filter(s -> ((Number) s.get("sourceDetailId")).longValue() == 1).count());
    var pressure =
        series.stream()
            .filter(s -> ((Number) s.get("sourceDetailId")).longValue() == 2)
            .findFirst()
            .orElseThrow();
    var points = Values.maps(pressure.get("points"));
    assertEquals(2, points.size());
    assertEquals(id(first), ((Number) points.get(0).get("orderId")).longValue());
    assertEquals(id(second), ((Number) points.get(1).get("orderId")).longValue());
    // 名称或单位含换行时，也不能把不同单位的历史结果合成一条曲线。
    db.exec("UPDATE cidetailedreport SET name=#{p.name},unit=#{p.unit} WHERE orderId=#{p.id} AND sourceDetailId=2", "name", "指标\n附加", "unit", "mmHg", "id", id(first));
    db.exec("UPDATE cidetailedreport SET name=#{p.name},unit=#{p.unit} WHERE orderId=#{p.id} AND sourceDetailId=2", "name", "指标", "unit", "附加\nmmHg", "id", id(second));
    assertEquals(2, Values.maps(experience.trends(owner, person).get("series")).stream().filter(s -> ((Number) s.get("sourceDetailId")).longValue() == 2).count());
    assertTrue(
        series.stream()
            .flatMap(s -> Values.maps(s.get("points")).stream())
            .noneMatch(
                p ->
                    ((Number) p.get("orderId")).longValue() == id(pending)
                        || ((Number) p.get("orderId")).longValue() == id(another)));
  }

  @Test
  void guidanceUsesSafeDefaultsPreservesSnapshotsAndChecksOwnership() {
    String owner = account(), other = account();
    long hp = hospital();
    var order = booking(owner, hp, Values.today().plusDays(1));
    assertEquals(
        ExperienceService.DEFAULT_PREPARATION,
        experience.orderGuidance(owner, id(order)).get("preparation"));
    var saved = experience.saveGuidance(Map.of("hpId", hp, "preparation", " ", "arrivalSteps", ""));
    assertEquals(ExperienceService.DEFAULT_ARRIVAL, saved.get("arrivalSteps"));
    assertTrue(saved.get("arrivalSteps").toString().contains("\n"));
    experience.saveGuidance(
        Map.of("hpId", hp, "preparation", "携带预约记录与证件", "arrivalSteps", "到院\n服务台核对\n按指引检查"));
    assertEquals("携带预约记录与证件", experience.orderGuidance(owner, id(order)).get("preparation"));
    assertEquals(order.get("hospitalName"), orders.order(id(order)).get("hospitalName"));
    assertThrows(BusinessException.class, () -> experience.orderGuidance(other, id(order)));
    assertThrows(
        BusinessException.class,
        () ->
            experience.saveGuidance(
                Map.of("hpId", hp, "preparation", "字".repeat(2001), "arrivalSteps", "步骤")));
    db.exec("UPDATE hospital SET state=2 WHERE hpId=#{p.hp}", "hp", hp);
    assertNotNull(experience.orderGuidance(owner, id(order)));
    assertThrows(
        BusinessException.class,
        () -> experience.saveGuidance(Map.of("hpId", hp, "preparation", "", "arrivalSteps", "")));
  }

  @Test
  void statisticsAndWorkbenchDoNotDuplicateCapacityAndRespectHospitalScope() {
    long hp = hospital(), otherHp = hospital();
    LocalDate today = Values.today();
    String firstOwner = account(),
        secondOwner = account(),
        thirdOwner = account(),
        otherOwner = account();
    var archived = booking(firstOwner, hp, today);
    archive(archived);
    var started = booking(secondOwner, hp, today);
    var detail =
        db.one(
            "SELECT cidrId FROM cidetailedreport WHERE orderId=#{p.id} LIMIT 1", "id", id(started));
    orders.saveDetails(
        doctor(hp),
        new HashMap<>(
            Map.of(
                "orderId",
                id(started),
                "version",
                0,
                "items",
                List.of(Map.of("cidrId", detail.get("cidrId"), "value", "120", "isError", 0)))));
    var cancelled = booking(thirdOwner, hp, today.plusDays(1));
    orders.cancel(thirdOwner, id(cancelled));
    booking(otherOwner, otherHp, today);
    var work = experience.workbench(doctor(hp), otherHp);
    assertEquals(2, ((Number) work.get("todayCount")).intValue());
    assertEquals(0, ((Number) work.get("pendingEntry")).intValue());
    assertEquals(1, ((Number) work.get("pendingArchive")).intValue());
    assertEquals(1, ((Number) work.get("archivedCount")).intValue());
    var filtered =
        (Map<?, ?>) orders.list(doctor(hp), Map.of("hpId", otherHp, "workStage", "started"));
    assertEquals(1, ((Number) filtered.get("total")).intValue());
    assertThrows(
        BusinessException.class, () -> orders.list(doctor(hp), Map.of("workStage", "other")));
    var stats = experience.statistics(admin, today.toString(), today.plusDays(1).toString(), hp);
    var totals = (Map<?, ?>) stats.get("totals");
    assertEquals(3, ((Number) totals.get("appointments")).intValue());
    assertEquals(1, ((Number) totals.get("cancelled")).intValue());
    assertEquals(40, ((Number) totals.get("capacity")).intValue());
    assertEquals(2, ((Number) totals.get("used")).intValue());
    assertEquals(new BigDecimal("5.00"), totals.get("utilization"));
    assertEquals(new BigDecimal("50.00"), totals.get("archiveRate"));
    var meals = Values.maps(stats.get("popularMeals"));
    assertEquals(2, ((Number) meals.get(0).get("appointments")).intValue());
    assertEquals(new BigDecimal("700.00"), meals.get(0).get("amount"));
    assertEquals(2, Values.maps(stats.get("daily")).size());
    assertThrows(
        BusinessException.class,
        () -> experience.statistics(doctor(hp), today.toString(), today.toString(), hp));
    assertThrows(
        BusinessException.class,
        () -> experience.statistics(admin, today.toString(), today.plusDays(366).toString(), hp));
  }

  @Test
  void newHttpEndpointsRecheckSessionsRolesAndCsrf() throws Exception {
    mvc.perform(get("/tijian/dashboard")).andExpect(status().isUnauthorized());
    mvc.perform(get("/tijian/notifications")).andExpect(status().isUnauthorized());
    var doctorSession = new MockHttpSession();
    doctorSession.setAttribute("staff", new Principal(null, 1L, "ADMIN", null));
    // Session中伪造管理员角色不能绕过数据库中D001的医生身份。
    mvc.perform(get("/tijiancms/admin/statistics").session(doctorSession))
        .andExpect(status().isForbidden());
    String owner = account();
    var userSession = new MockHttpSession();
    userSession.setAttribute("user", new Principal(owner, null, "USER", null));
    mvc.perform(get("/tijian/dashboard").session(userSession)).andExpect(status().isOk());
    mvc.perform(post("/tijian/notifications/read-all").session(userSession))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/tijian/notifications/read-all")
                .session(userSession)
                .header("X-CSRF-Token", userSession.getAttribute("csrf")))
        .andExpect(status().isOk());
    var adminSession = new MockHttpSession();
    adminSession.setAttribute("staff", admin);
    mvc.perform(get("/tijiancms/admin/statistics").session(adminSession))
        .andExpect(status().isOk());
  }
}
