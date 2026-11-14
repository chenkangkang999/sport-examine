package com.neusoft.health.service;

import static org.junit.jupiter.api.Assertions.*;

import com.neusoft.health.HealthApplication;
import com.neusoft.health.config.DemoData;
import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = HealthApplication.class)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Transactional
class MySqlIntegrationTest {
  @DynamicPropertySource
  static void db(DynamicPropertyRegistry registry) {
    String url = System.getenv("TEST_DB_URL");
    if (url != null && !url.matches(".*[/]tijian_test([?].*)?$"))
      throw new IllegalStateException("集成测试仅允许专用 tijian_test 数据库");
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    registry.add("health.seed-enabled", () -> true);
  }

  @Autowired AccountService accounts;
  @Autowired OrderService orders;
  @Autowired Database db;
  static final AtomicInteger seq = new AtomicInteger(101);
  Principal doctor = new Principal(null, 1L, "DOCTOR", 1L);

  String account() {
    int n = seq.getAndAdd(2);
    String phone = "1390000" + String.format("%04d", n);
    accounts.register(
        new HashMap<>(
            Map.of(
                "userId",
                phone,
                "password",
                "Health123!",
                "realName",
                "测试用户",
                "sex",
                1,
                "birthday",
                "1990-01-01",
                "identityCard",
                DemoData.identity("11010119900101" + String.format("%03d", n)))));
    return phone;
  }

  Map<String, Object> booking(String phone) {
    var person = accounts.persons(phone).get(0);
    LocalDate d = Values.today().plusDays(1);
    while (d.getDayOfWeek().getValue() > 5) d = d.plusDays(1);
    var p =
        new HashMap<String, Object>(
            Map.of(
                "personId",
                person.get("personId"),
                "hpId",
                1,
                "smId",
                1,
                "orderDate",
                d.toString(),
                "requestId",
                UUID.randomUUID().toString(),
                "amount",
                0.01));
    return orders.create(phone, p);
  }

  @Test
  void createCancelIsPersistentAndIdempotent() {
    String owner = account();
    var o = booking(owner);
    long id = ((Number) o.get("orderId")).longValue();
    assertEquals("350.00", o.get("amount").toString());
    // 手册左侧性别筛选必须在服务端生效，同时保留医生所属医院约束。
    var maleList = (Map<?, ?>) orders.list(doctor, Map.of("userId", owner, "sex", 1));
    var femaleList = (Map<?, ?>) orders.list(doctor, Map.of("userId", owner, "sex", 0));
    assertEquals(1L, ((Number) maleList.get("total")).longValue());
    assertEquals(0L, ((Number) femaleList.get("total")).longValue());
    var original = db.one("SELECT requestId FROM orders WHERE orderId=#{p.id}", "id", id);
    var retry =
        new HashMap<String, Object>(
            Map.of(
                "personId",
                o.get("personId"),
                "hpId",
                1,
                "smId",
                1,
                "orderDate",
                o.get("orderDate").toString(),
                "requestId",
                original.get("requestId")));
    assertEquals(id, ((Number) orders.create(owner, retry).get("orderId")).longValue());
    orders.cancel(owner, id);
    orders.cancel(owner, id);
    assertEquals(3, ((Number) orders.order(id).get("state")).intValue());
    assertEquals(
        "REFUNDED",
        db.one("SELECT status FROM payment WHERE orderId=#{p.id}", "id", id).get("status"));
  }

  @Test
  void reportVersionOwnershipAndArchiveRules() {
    String owner = account();
    var o = booking(owner);
    long id = ((Number) o.get("orderId")).longValue();
    assertThrows(BusinessException.class, () -> orders.owned("13800000001", id));
    assertThrows(
        BusinessException.class, () -> orders.scoped(new Principal(null, 2L, "DOCTOR", 2L), id));
    assertThrows(BusinessException.class, () -> orders.userReport(owner, id));
    assertThrows(
        BusinessException.class,
        () -> orders.archive(doctor, new HashMap<>(Map.of("orderId", id, "version", 0))));
    var changes = new ArrayList<Map<String, Object>>();
    for (var detail : db.rows("SELECT * FROM cidetailedreport WHERE orderId=#{p.id}", "id", id)) {
      String value = detail.get("minrange") == null ? "正常" : detail.get("minrange").toString();
      changes.add(
          new HashMap<>(Map.of("cidrId", detail.get("cidrId"), "value", value, "isError", 0)));
    }
    orders.saveDetails(
        doctor, new HashMap<>(Map.of("orderId", id, "version", 0, "items", changes)));
    assertThrows(
        BusinessException.class,
        () ->
            orders.saveDetails(
                doctor, new HashMap<>(Map.of("orderId", id, "version", 0, "items", changes))));
    assertThrows(BusinessException.class, () -> orders.cancel(owner, id));
    orders.saveConclusion(
        doctor,
        new HashMap<>(
            Map.of("orderId", id, "version", 1, "title", "总检结论", "content", "未见明显异常（测试）")));
    orders.archive(doctor, new HashMap<>(Map.of("orderId", id, "version", 2)));
    assertDoesNotThrow(() -> orders.userReport(owner, id));
    assertThrows(
        BusinessException.class,
        () ->
            orders.saveDetails(
                doctor, new HashMap<>(Map.of("orderId", id, "version", 3, "items", changes))));
  }

  @Test
  void changingCatalogDoesNotChangeSnapshots() {
    String owner = account();
    var o = booking(owner);
    long id = ((Number) o.get("orderId")).longValue();
    db.exec("UPDATE setmeal SET name='修改后的套餐',price=999 WHERE smId=1");
    db.exec("UPDATE checkitem SET ciName='修改后的项目' WHERE ciId=1");
    assertEquals("男士基础体检", orders.order(id).get("setmealName"));
    assertEquals("350.00", orders.order(id).get("amount").toString());
    @SuppressWarnings("unchecked")
    var items = (List<Map<String, Object>>) orders.report(id).get("items");
    assertEquals("一般检查", items.get(0).get("ciName"));
  }
}
