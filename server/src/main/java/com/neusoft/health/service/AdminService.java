package com.neusoft.health.service;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.mapper.Database;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private final Database db;
  private final AccountService accounts;
  private final CatalogService catalog;

  public AdminService(Database db, AccountService accounts, CatalogService catalog) {
    this.db = db;
    this.accounts = accounts;
    this.catalog = catalog;
  }

  private String[] resource(String r) {
    return switch (r) {
      case "hospitals" -> new String[] {"hospital", "hpId"};
      case "setmeals" -> new String[] {"setmeal", "smId"};
      case "checkitems" -> new String[] {"checkitem", "ciId"};
      case "doctors" -> new String[] {"doctor", "docId"};
      default -> throw new BusinessException(404, "管理资源不存在");
    };
  }

  public List<Map<String, Object>> list(String r) {
    var spec = resource(r);
    String columns =
        "doctors".equals(r) ? "docId,docCode,realName,sex,deptno,hpId,role,state" : "*";
    var rows = db.rows("SELECT " + columns + " FROM " + spec[0] + " ORDER BY " + spec[1]);
    for (var row : rows) {
      if ("checkitems".equals(r))
        row.put(
            "details",
            db.rows(
                "SELECT * FROM checkitemdetailed WHERE ciId=#{p.id} AND state=1 ORDER BY cdId",
                "id",
                row.get("ciId")));
      if ("setmeals".equals(r))
        row.put(
            "ciIds",
            db
                .rows(
                    "SELECT ciId FROM setmealdetailed WHERE smId=#{p.id} ORDER BY ciId",
                    "id",
                    row.get("smId"))
                .stream()
                .map(x -> x.get("ciId"))
                .toList());
    }
    return rows;
  }

  @Transactional
  public Object save(String r, Map<String, Object> raw, long current) {
    var p = new HashMap<>(raw);
    bounded(p, "picture", 200);
    bounded(p, "telephone", 30);
    bounded(p, "address", 200);
    bounded(p, "businessHours", 120);
    bounded(p, "deadline", 80);
    bounded(p, "meaning", 300);
    bounded(p, "remarks", 300);
    var spec = resource(r);
    boolean update = !text(p, spec[1]).isEmpty();
    if (update
        && db.one(
                "SELECT "
                    + spec[1]
                    + " FROM "
                    + spec[0]
                    + " WHERE "
                    + spec[1]
                    + "=#{p.id} FOR UPDATE",
                "id",
                number(p, spec[1]))
            == null) throw new BusinessException(404, "记录不存在");
    int state = integer(p, "state", 1);
    if (state != 1 && state != 2) throw new BusinessException(400, "状态应为1启用或2停用");
    p.put("state", state);
    String fields;
    switch (r) {
      case "hospitals" -> {
        required(p, "name", 100);
        p.putIfAbsent("picture", "/img/hospita1.png");
        p.putIfAbsent("telephone", "");
        p.putIfAbsent("address", "");
        p.putIfAbsent("businessHours", "周一至周五 7:30-15:30");
        p.putIfAbsent("deadline", "采血截止 10:30");
        fields = "name,picture,telephone,address,businessHours,deadline,state";
      }
      case "setmeals" -> {
        required(p, "name", 100);
        int type = integer(p, "type", 2);
        if (type < 0 || type > 2) throw new BusinessException(400, "套餐类型不正确");
        p.put("type", type);
        var price = decimal(p.get("price"));
        if (price.signum() < 0
            || price.compareTo(new java.math.BigDecimal("99999999.99")) > 0
            || price.scale() > 2) throw new BusinessException(400, "价格应为非负金额且最多两位小数");
        p.put("price", price);
        fields = "name,type,price,state";
        validateIds(p.get("ciIds"), "checkitem", "ciId");
        if (((List<?>) p.get("ciIds")).isEmpty()) throw new BusinessException(400, "套餐至少包含一个检查项目");
      }
      case "checkitems" -> {
        required(p, "ciName", 100);
        required(p, "ciContent", 300);
        p.putIfAbsent("meaning", "");
        p.putIfAbsent("remarks", "");
        fields = "ciName,ciContent,meaning,remarks,state";
        if (maps(p.get("details")).isEmpty()) throw new BusinessException(400, "检查项目至少有一个明细");
        for (var d : maps(p.get("details"))) validateDetail(d);
      }
      case "doctors" -> {
        required(p, "docCode", 30);
        required(p, "realName", 40);
        p.putIfAbsent("role", "DOCTOR");
        p.putIfAbsent("sex", 1);
        p.putIfAbsent("deptno", 1);
        if (!Set.of(0, 1).contains(integer(p, "sex", -1)))
          throw new BusinessException(400, "性别应为0或1");
        if (!Set.of(1, 2, 3).contains(integer(p, "deptno", -1)))
          throw new BusinessException(400, "科室应为1检验科、2内科或3外科");
        String role = text(p, "role");
        if (!Set.of("DOCTOR", "ADMIN").contains(role)) throw new BusinessException(400, "角色不正确");
        if ("DOCTOR".equals(role)) catalog.hospital(number(p, "hpId"));
        else if (text(p, "hpId").isEmpty()) p.put("hpId", null);
        if (update && number(p, "docId") == current && (state != 1 || !"ADMIN".equals(role)))
          throw new BusinessException(400, "不能停用或降级当前管理员");
        String pwd = text(p, "password");
        if (!update || !pwd.isEmpty())
          p.put("password", accounts.hashPassword(password(p, "password")));
        else
          p.put(
              "password",
              db.one("SELECT password FROM doctor WHERE docId=#{p.docId}", p).get("password"));
        fields = "docCode,realName,password,sex,deptno,hpId,role,state";
      }
      default -> throw new BusinessException(404, "管理资源不存在");
    }
    String[] fieldList = fields.split(",");
    long id;
    if (update) {
      id = number(p, spec[1]);
      var assignments = new ArrayList<String>();
      for (String f : fieldList) assignments.add(f + "=#{p." + f + "}");
      db.exec(
          "UPDATE "
              + spec[0]
              + " SET "
              + String.join(",", assignments)
              + " WHERE "
              + spec[1]
              + "=#{p."
              + spec[1]
              + "}",
          p);
    } else {
      var placeholders = new ArrayList<String>();
      for (String f : fieldList) placeholders.add("#{p." + f + "}");
      id =
          db.insert(
              "INSERT INTO "
                  + spec[0]
                  + "("
                  + fields
                  + ") VALUES("
                  + String.join(",", placeholders)
                  + ")",
              p);
    }
    if ("setmeals".equals(r)) {
      db.exec("DELETE FROM setmealdetailed WHERE smId=#{p.id}", "id", id);
      for (Object ci : (List<?>) p.get("ciIds"))
        db.exec(
            "INSERT INTO setmealdetailed(smId,ciId) VALUES(#{p.id},#{p.ci})", "id", id, "ci", ci);
    }
    if ("checkitems".equals(r)) {
      db.exec("UPDATE checkitemdetailed SET state=2 WHERE ciId=#{p.id}", "id", id);
      for (var d : maps(p.get("details"))) {
        var detail = new HashMap<>(d);
        detail.put("ciId", id);
        detail.putIfAbsent("unit", "");
        detail.putIfAbsent("normalValueString", "");
        detail.putIfAbsent("remarks", "");
        detail.putIfAbsent("required", true);
        db.exec(
            "INSERT INTO"
                + " checkitemdetailed(ciId,name,unit,minrange,maxrange,normalValue,normalValueString,type,required,remarks)"
                + " VALUES(#{p.ciId},#{p.name},#{p.unit},#{p.minrange},#{p.maxrange},#{p.normalValue},#{p.normalValueString},#{p.type},#{p.required},#{p.remarks})",
            detail);
      }
    }
    long resultId = id;
    return list(r).stream()
        .filter(x -> ((Number) x.get(spec[1])).longValue() == resultId)
        .findFirst()
        .orElseThrow();
  }

  private void validateDetail(Map<String, Object> d) {
    required(d, "name", 100);
    bounded(d, "unit", 40);
    bounded(d, "normalValueString", 200);
    bounded(d, "remarks", 300);
    if (d.get("required") != null && !(d.get("required") instanceof Boolean))
      throw new BusinessException(400, "必填标记必须是布尔值");
    for (String f : List.of("minrange", "maxrange", "normalValue"))
      if (text(d, f).isEmpty()) d.put(f, null);
      else {
        var n = decimal(d.get(f));
        if (n.scale() > 4 || n.abs().compareTo(new java.math.BigDecimal("999999999999.9999")) > 0)
          throw new BusinessException(400, "参考值最多12位整数和4位小数");
      }
    int t = integer(d, "type", 4);
    if (t < 1 || t > 5) throw new BusinessException(400, "检查结果类型不正确");
    d.put("type", t);
    if (t == 1) {
      if (d.get("minrange") == null && d.get("maxrange") == null)
        throw new BusinessException(400, "范围类型至少填写一个边界");
      if (d.get("minrange") != null
          && d.get("maxrange") != null
          && decimal(d.get("minrange")).compareTo(decimal(d.get("maxrange"))) > 0)
        throw new BusinessException(400, "参考范围下限不能大于上限");
    }
    if (t == 2) decimal(d.get("normalValue"));
  }

  private void validateIds(Object ids, String table, String field) {
    if (!(ids instanceof List<?> list)) throw new BusinessException(400, "请提供关联编号数组");
    Set<String> distinct = new HashSet<>();
    for (Object id : list) {
      if (!distinct.add(String.valueOf(id))) throw new BusinessException(400, "关联编号重复");
      if (db.one(
              "SELECT " + field + " FROM " + table + " WHERE " + field + "=#{p.id} AND state=1",
              "id",
              id)
          == null) throw new BusinessException(400, "关联记录不存在或已停用");
    }
  }

  @Transactional
  public Object disable(String r, long id, long current) {
    var spec = resource(r);
    if ("doctors".equals(r) && id == current) throw new BusinessException(400, "不能停用当前管理员");
    if (db.exec("UPDATE " + spec[0] + " SET state=2 WHERE " + spec[1] + "=#{p.id}", "id", id) != 1)
      throw new BusinessException(404, "记录不存在");
    return true;
  }

  public Object hospitalMeals(long hp) {
    return db
        .rows("SELECT smId FROM hospital_setmeal WHERE hpId=#{p.hp} ORDER BY smId", "hp", hp)
        .stream()
        .map(x -> x.get("smId"))
        .toList();
  }

  @Transactional
  public Object saveHospitalMeals(Map<String, Object> p) {
    long hp = number(p, "hpId");
    catalog.hospital(hp);
    db.one("SELECT hpId FROM hospital WHERE hpId=#{p.id} FOR UPDATE", "id", hp);
    validateIds(p.get("smIds"), "setmeal", "smId");
    db.exec("DELETE FROM hospital_setmeal WHERE hpId=#{p.hp}", "hp", hp);
    for (Object sm : (List<?>) p.get("smIds"))
      db.exec(
          "INSERT INTO hospital_setmeal(hpId,smId) VALUES(#{p.hp},#{p.sm})", "hp", hp, "sm", sm);
    return hospitalMeals(hp);
  }

  @Transactional
  public Object saveSchedules(Map<String, Object> p) {
    long hp = number(p, "hpId");
    catalog.hospital(hp);
    LocalDate from = date(p.get("from")), to = date(p.get("to"));
    if (to.isBefore(from) || to.isAfter(from.plusDays(366)))
      throw new BusinessException(400, "排班区间最多一年");
    int capacity = integer(p, "capacity", -1);
    if (capacity < 0 || capacity > 100000) throw new BusinessException(400, "名额应为0至100000");
    if (!(p.get("weekdays") instanceof List<?> weekdays))
      throw new BusinessException(400, "请选择开放星期");
    Set<Integer> days = new HashSet<>();
    for (Object w : weekdays) {
      int n = Integer.parseInt(String.valueOf(w));
      if (n < 1 || n > 7) throw new BusinessException(400, "星期范围应为1至7");
      days.add(n);
    }
    db.one("SELECT hpId FROM hospital WHERE hpId=#{p.id} FOR UPDATE", "id", hp);
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      int cap = days.contains(d.getDayOfWeek().getValue()) ? capacity : 0;
      var row =
          db.one(
              "SELECT used FROM appointment_schedule WHERE hpId=#{p.hp} AND"
                  + " appointmentDate=#{p.date} FOR UPDATE",
              "hp",
              hp,
              "date",
              d);
      if (row != null && ((Number) row.get("used")).intValue() > cap)
        throw new BusinessException(409, d + "名额不能低于已预约人数");
      db.exec(
          "INSERT INTO appointment_schedule(hpId,appointmentDate,capacity)"
              + " VALUES(#{p.hp},#{p.date},#{p.cap}) ON DUPLICATE KEY UPDATE capacity=#{p.cap}",
          "hp",
          hp,
          "date",
          d,
          "cap",
          cap);
    }
    return catalog.schedules(hp, from, to);
  }
}
