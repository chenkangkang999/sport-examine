package com.neusoft.health.service;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 面向首页、健康趋势和工作区的查询，不改变预约及报告快照。 */
@Service
public class ExperienceService {
  private final Database db;
  private final OrderService orders;
  private final NotificationService notifications;
  public static final String DEFAULT_PREPARATION = "请携带有效身份证件和预约信息到院。具体检查准备事项请提前联系体检机构，并遵从机构的安排。";
  public static final String DEFAULT_ARRIVAL =
      "核对预约日期和体检机构\n到院服务台核对身份及预约\n按工作人员指引领取检查安排\n完成检查后等待报告归档。";

  public ExperienceService(Database db, OrderService orders, NotificationService notifications) {
    this.db = db;
    this.orders = orders;
    this.notifications = notifications;
  }

  public Map<String, Object> dashboard(String owner) {
    var appointment =
        db.one(
            "SELECT "
                + OrderService.ORDER_COLUMNS
                + " FROM orders WHERE userId=#{p.owner} AND state=1 ORDER BY"
                + " (orderDate>=#{p.today}) DESC,CASE WHEN orderDate>=#{p.today} THEN orderDate END"
                + " ASC,orderId DESC LIMIT 1",
            "owner",
            owner,
            "today",
            today());
    if (appointment == null)
      appointment =
          db.one(
              "SELECT "
                  + OrderService.ORDER_COLUMNS
                  + " FROM orders WHERE userId=#{p.owner} ORDER BY orderId DESC LIMIT 1",
              "owner",
              owner);
    var result = new HashMap<String, Object>();
    result.put("appointment", appointment);
    result.put(
        "latestReport",
        db.one(
            "SELECT "
                + OrderService.ORDER_COLUMNS
                + " FROM orders WHERE userId=#{p.owner} AND state=2 ORDER BY archivedAt"
                + " DESC,orderId DESC LIMIT 1",
            "owner",
            owner));
    result.put("unreadCount", notifications.unread(owner));
    return result;
  }

  public Map<String, Object> trends(String owner, long personId) {
    var person =
        db.one(
            "SELECT personId,realName FROM person WHERE personId=#{p.id} AND userId=#{p.owner}",
            "id",
            personId,
            "owner",
            owner);
    if (person == null) throw new BusinessException(404, "体检人不存在或无权访问");
    var rows =
        db.rows(
            "SELECT"
                + " d.sourceDetailId,d.name,d.unit,d.type,d.value,d.minrange,d.maxrange,d.normalValue,d.normalValueString,d.isError,o.orderId,o.orderDate"
                + " FROM cidetailedreport d JOIN orders o ON o.orderId=d.orderId WHERE"
                + " o.userId=#{p.owner} AND o.personId=#{p.id} AND o.state=2 AND d.type IN(1,2,3)"
                + " ORDER BY o.orderDate,o.orderId,d.cidrId",
            "owner",
            owner,
            "id",
            personId);
    var series = new LinkedHashMap<String, Map<String, Object>>();
    for (var row : rows) {
      BigDecimal value;
      try {
        value = new BigDecimal(text(row, "value"));
      } catch (NumberFormatException ignored) {
        continue;
      }
      String unit = text(row, "unit");
      String name = text(row, "name");
      String type = String.valueOf(row.get("type"));
      // 字段自身可能包含换行；长度前缀保证名称、单位和类型不会发生拼接歧义。
      String key =
          row.get("sourceDetailId")
              + ":"
              + hash(name.length() + ":" + name + unit.length() + ":" + unit + type.length() + ":" + type);
      var group =
          series.computeIfAbsent(
              key,
              unused -> {
                var map = new LinkedHashMap<String, Object>();
                map.put("key", key);
                map.put("sourceDetailId", row.get("sourceDetailId"));
                map.put("name", row.get("name"));
                map.put("unit", unit);
                map.put("type", row.get("type"));
                map.put("points", new ArrayList<Map<String, Object>>());
                return map;
              });
      var point = new LinkedHashMap<>(row);
      for (String field : List.of("sourceDetailId", "name", "unit", "type")) point.remove(field);
      point.put("value", value);
      @SuppressWarnings("unchecked")
      var points = (List<Map<String, Object>>) group.get("points");
      points.add(point);
    }
    return Map.of(
        "personId",
        personId,
        "personName",
        person.get("realName"),
        "series",
        new ArrayList<>(series.values()));
  }

  public Map<String, Object> orderGuidance(String owner, long orderId) {
    var order = orders.owned(owner, orderId);
    var guidance = new HashMap<>(guidance(((Number) order.get("hpId")).longValue()));
    guidance.remove("hpId");
    return guidance;
  }

  public Map<String, Object> guidance(long hpId) {
    hospital(hpId, false);
    var result =
        db.one(
            "SELECT hpId,preparation,arrivalSteps FROM hospital_guidance WHERE hpId=#{p.hp}",
            "hp",
            hpId);
    if (result == null)
      return new HashMap<>(
          Map.of(
              "hpId", hpId, "preparation", DEFAULT_PREPARATION, "arrivalSteps", DEFAULT_ARRIVAL));
    return result;
  }

  @Transactional
  public Map<String, Object> saveGuidance(Map<String, Object> raw) {
    long hpId = number(raw, "hpId");
    hospital(hpId, true);
    var params = new HashMap<>(raw);
    bounded(raw, "preparation", 2000);
    bounded(raw, "arrivalSteps", 2000);
    params.put(
        "preparation",
        text(raw, "preparation").isEmpty() ? DEFAULT_PREPARATION : text(raw, "preparation"));
    params.put(
        "arrivalSteps",
        text(raw, "arrivalSteps").isEmpty() ? DEFAULT_ARRIVAL : text(raw, "arrivalSteps"));
    db.exec(
        "INSERT INTO hospital_guidance(hpId,preparation,arrivalSteps)"
            + " VALUES(#{p.hpId},#{p.preparation},#{p.arrivalSteps}) ON DUPLICATE KEY UPDATE"
            + " preparation=VALUES(preparation),arrivalSteps=VALUES(arrivalSteps),updatedAt=CURRENT_TIMESTAMP",
        params);
    return guidance(hpId);
  }

  private void hospital(long hpId, boolean enabled) {
    var hospital = db.one("SELECT state FROM hospital WHERE hpId=#{p.hp}", "hp", hpId);
    if (hospital == null) throw new BusinessException(404, "医院不存在");
    if (enabled && integer(hospital, "state", 0) != 1)
      throw new BusinessException(400, "仅可配置启用医院的体检指南");
  }

  private Long scopedHospital(Principal staff, Long requested) {
    if ("ADMIN".equals(staff.role())) {
      if (requested != null) hospital(requested, false);
      return requested;
    }
    if (!"DOCTOR".equals(staff.role()) || staff.hpId() == null)
      throw new BusinessException(403, "医生尚未关联医院");
    return staff.hpId();
  }

  public Map<String, Object> workbench(Principal staff, Long hpId) {
    Long hospital = scopedHospital(staff, hpId);
    var params = Database.params("today", today(), "hp", hospital);
    String where = hospital == null ? "" : " WHERE hpId=#{p.hp}";
    var result =
        db.one(
            "SELECT COALESCE(SUM(orderDate=#{p.today} AND state<>3),0) AS todayCount,"
                + "COALESCE(SUM(state=1 AND reportStarted=0),0) AS pendingEntry,"
                + "COALESCE(SUM(state=1 AND reportStarted=1),0) AS pendingArchive,"
                + "COALESCE(SUM(state=2),0) AS archivedCount FROM orders"
                + where,
            params);
    result.put("todayDate", today());
    return result;
  }

  public Map<String, Object> statistics(
      Principal staff, String fromText, String toText, Long hpId) {
    if (!"ADMIN".equals(staff.role())) throw new BusinessException(403, "仅管理员可以查看统计");
    Long hospital = scopedHospital(staff, hpId);
    LocalDate from =
        fromText == null || fromText.isBlank() ? today().minusDays(29) : date(fromText);
    LocalDate to = toText == null || toText.isBlank() ? today() : date(toText);
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365)
      throw new BusinessException(400, "统计日期范围应为1至366天");
    var params = Database.params("from", from, "to", to, "hp", hospital);
    String scope = hospital == null ? "" : " AND hpId=#{p.hp}";
    var orderRows =
        db.rows(
            "SELECT orderDate AS date,COUNT(*) AS appointments,COALESCE(SUM(state=1),0) AS"
                + " active,COALESCE(SUM(state=2),0) AS archived,COALESCE(SUM(state=3),0) AS"
                + " cancelled FROM orders WHERE orderDate BETWEEN #{p.from} AND #{p.to}"
                + scope
                + " GROUP BY orderDate",
            params);
    var scheduleRows =
        db.rows(
            "SELECT appointmentDate AS date,SUM(capacity) AS capacity,SUM(used) AS used FROM"
                + " appointment_schedule WHERE appointmentDate BETWEEN #{p.from} AND #{p.to}"
                + scope
                + " GROUP BY appointmentDate",
            params);
    var byDate = new HashMap<LocalDate, Map<String, Object>>();
    for (var row : orderRows) byDate.put(date(row.get("date")), row);
    var scheduleByDate = new HashMap<LocalDate, Map<String, Object>>();
    for (var row : scheduleRows) scheduleByDate.put(date(row.get("date")), row);
    var daily = new ArrayList<Map<String, Object>>();
    var totals = new LinkedHashMap<String, Object>();
    for (String key :
        List.of("appointments", "active", "archived", "cancelled", "capacity", "used"))
      totals.put(key, 0L);
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      var row = new LinkedHashMap<String, Object>();
      row.put("date", day);
      var order = byDate.getOrDefault(day, Map.of());
      var schedule = scheduleByDate.getOrDefault(day, Map.of());
      for (String key :
          List.of("appointments", "active", "archived", "cancelled", "capacity", "used")) {
        long amount =
            numeric((key.equals("capacity") || key.equals("used") ? schedule : order).get(key));
        row.put(key, amount);
        totals.put(key, numeric(totals.get(key)) + amount);
      }
      row.put("utilization", percent(numeric(row.get("used")), numeric(row.get("capacity"))));
      daily.add(row);
    }
    totals.put(
        "utilization", percent(numeric(totals.get("used")), numeric(totals.get("capacity"))));
    totals.put(
        "archiveRate",
        percent(
            numeric(totals.get("archived")),
            numeric(totals.get("active")) + numeric(totals.get("archived"))));
    var popular =
        db.rows(
            "SELECT smId,setmealName AS name,COUNT(*) AS appointments,SUM(amount) AS amount FROM"
                + " orders WHERE state IN(1,2) AND orderDate BETWEEN #{p.from} AND #{p.to}"
                + scope
                + " GROUP BY smId,setmealName ORDER BY appointments DESC,smId,setmealName LIMIT 10",
            params);
    String hospitalScope = hospital == null ? "" : " AND o.hpId=#{p.hp}";
    var hospitals =
        db.rows(
            "SELECT o.hpId,h.name,COUNT(*) AS appointments,SUM(o.state=1) AS active,SUM(o.state=2)"
                + " AS archived,SUM(o.state=3) AS cancelled FROM orders o JOIN hospital h ON"
                + " h.hpId=o.hpId WHERE o.orderDate BETWEEN #{p.from} AND #{p.to}"
                + hospitalScope
                + " GROUP BY o.hpId,h.name ORDER BY appointments DESC,o.hpId",
            params);
    var result = new LinkedHashMap<String, Object>();
    result.put("from", from);
    result.put("to", to);
    result.put("hpId", hospital);
    result.put("totals", totals);
    result.put("daily", daily);
    result.put("popularMeals", popular);
    result.put("hospitalSummary", hospitals);
    return result;
  }

  private static long numeric(Object value) {
    return value instanceof Number n ? n.longValue() : 0L;
  }

  private static BigDecimal percent(long value, long total) {
    return total == 0
        ? BigDecimal.ZERO
        : BigDecimal.valueOf(value)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
            .min(BigDecimal.valueOf(100));
  }
}
