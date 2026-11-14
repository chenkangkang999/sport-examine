package com.neusoft.health.service;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.mapper.Database;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final Database db;

  public CatalogService(Database db) {
    this.db = db;
  }

  public List<Map<String, Object>> hospitals() {
    return db.rows("SELECT * FROM hospital WHERE state=1 ORDER BY hpId");
  }

  public Map<String, Object> hospital(long id) {
    var r = db.one("SELECT * FROM hospital WHERE hpId=#{p.id} AND state=1", "id", id);
    if (r == null) throw new BusinessException(404, "医院不可用");
    return r;
  }

  public List<Map<String, Object>> meals(Long hpId, Integer type) {
    String sql = "SELECT s.* FROM setmeal s WHERE s.state=1";
    var p = new HashMap<String, Object>();
    if (hpId != null) {
      hospital(hpId);
      sql +=
          " AND EXISTS(SELECT 1 FROM hospital_setmeal h WHERE h.hpId=#{p.hpId} AND h.smId=s.smId)";
      p.put("hpId", hpId);
    }
    if (type != null) {
      sql += " AND s.type IN(#{p.type},2)";
      p.put("type", type);
    }
    return db.rows(sql + " ORDER BY s.smId", p);
  }

  public Map<String, Object> meal(long id, Long hp) {
    var r = db.one("SELECT * FROM setmeal WHERE smId=#{p.id} AND state=1", "id", id);
    if (r == null) throw new BusinessException(404, "套餐不可用");
    if (hp != null) {
      hospital(hp);
      if (db.one(
              "SELECT 1 FROM hospital_setmeal WHERE hpId=#{p.hp} AND smId=#{p.id}",
              "hp",
              hp,
              "id",
              id)
          == null) throw new BusinessException(400, "该医院不提供此套餐");
    }
    var items =
        db.rows(
            "SELECT c.* FROM checkitem c JOIN setmealdetailed s ON c.ciId=s.ciId WHERE"
                + " s.smId=#{p.id} AND c.state=1 ORDER BY c.ciId",
            "id",
            id);
    for (var c : items)
      c.put(
          "details",
          db.rows(
              "SELECT * FROM checkitemdetailed WHERE ciId=#{p.id} AND state=1 ORDER BY cdId",
              "id",
              c.get("ciId")));
    r.put("items", items);
    return r;
  }

  public List<Map<String, Object>> calendar(long hp, int year, int month) {
    hospital(hp);
    LocalDate start;
    try {
      start = LocalDate.of(year, month, 1);
    } catch (Exception e) {
      throw new BusinessException(400, "年月不正确");
    }
    return schedules(hp, start, start.withDayOfMonth(start.lengthOfMonth()));
  }

  public List<Map<String, Object>> schedules(long hp, LocalDate from, LocalDate to) {
    if (to.isBefore(from) || to.isAfter(from.plusDays(366)))
      throw new BusinessException(400, "日期范围不正确或超过一年");
    var rows =
        db.rows(
            "SELECT hpId,appointmentDate AS date,capacity,used,capacity-used AS remaining FROM"
                + " appointment_schedule WHERE hpId=#{p.hp} AND appointmentDate BETWEEN #{p.from}"
                + " AND #{p.to} ORDER BY appointmentDate",
            "hp",
            hp,
            "from",
            from,
            "to",
            to);
    var indexed = new HashMap<String, Map<String, Object>>();
    for (var r : rows) indexed.put(date(r.get("date")).toString(), r);
    var result = new ArrayList<Map<String, Object>>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      var r =
          indexed.getOrDefault(
              d.toString(),
              new HashMap<>(
                  Map.of("hpId", hp, "date", d, "capacity", 0, "used", 0, "remaining", 0)));
      r.put("available", !d.isBefore(today()) && ((Number) r.get("remaining")).intValue() > 0);
      result.add(r);
    }
    return result;
  }
}
