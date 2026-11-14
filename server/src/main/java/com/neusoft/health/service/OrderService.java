package com.neusoft.health.service;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 预约与报告业务。公开写方法由 Spring 事务代理调用，失败时连同名额一起回滚。 */
@Service
public class OrderService {
  private final Database db;
  private final CatalogService catalog;
  public static final String ORDER_COLUMNS =
      "orderId,userId,personId,hpId,smId,orderDate,state,personName,personSex,personBirthday,personIdentityMasked,hospitalName,hospitalAddress,hospitalTelephone,businessHours,deadline,setmealName,amount,reportVersion,reportStarted,createdAt,archivedAt";

  public OrderService(Database db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  public Map<String, Object> order(long id) {
    var o = db.one("SELECT " + ORDER_COLUMNS + " FROM orders WHERE orderId=#{p.id}", "id", id);
    if (o == null) throw new BusinessException(404, "预约不存在");
    return o;
  }

  public Map<String, Object> owned(String owner, long id) {
    var o = order(id);
    if (!owner.equals(o.get("userId"))) throw new BusinessException(403, "无权查看此预约");
    return o;
  }

  public Map<String, Object> scoped(Principal staff, long id) {
    var o = order(id);
    if (!"ADMIN".equals(staff.role())
        && !Objects.equals(staff.hpId(), ((Number) o.get("hpId")).longValue()))
      throw new BusinessException(403, "不能访问其他医院预约");
    return o;
  }

  public List<Map<String, Object>> userOrders(String owner, Integer state, Long person) {
    String sql = "SELECT " + ORDER_COLUMNS + " FROM orders WHERE userId=#{p.owner}";
    var p = Database.params("owner", owner);
    if (state != null) {
      sql += " AND state=#{p.state}";
      p.put("state", state);
    }
    if (person != null) {
      sql += " AND personId=#{p.person}";
      p.put("person", person);
    }
    return db.rows(sql + " ORDER BY orderId DESC", p);
  }

  @Transactional
  public Map<String, Object> create(String owner, Map<String, Object> raw) {
    var p = new HashMap<>(raw);
    String request = required(p, "requestId", 64);
    long personId = number(p, "personId"), hp = number(p, "hpId"), sm = number(p, "smId");
    LocalDate date = date(p.get("orderDate"));
    // 先锁账号再检查请求编号：同一账号重复点击时，后一个请求复用已生成的订单。
    db.one("SELECT userId FROM users WHERE userId=#{p.id} FOR UPDATE", "id", owner);
    var existing =
        db.one(
            "SELECT orderId FROM orders WHERE userId=#{p.owner} AND requestId=#{p.request}",
            "owner",
            owner,
            "request",
            request);
    if (existing != null) return order(((Number) existing.get("orderId")).longValue());
    if (date.isBefore(today()) || date.isAfter(today().plusDays(60)))
      throw new BusinessException(400, "请选择未来60天内的可约日期");
    var person =
        db.one(
            "SELECT * FROM person WHERE personId=#{p.id} AND userId=#{p.owner} AND state=1 FOR"
                + " UPDATE",
            "id",
            personId,
            "owner",
            owner);
    if (person == null) throw new BusinessException(404, "体检人不存在");
    if (db.one(
            "SELECT orderId FROM orders WHERE activeIdentity=#{p.hash}",
            "hash",
            person.get("identityHash"))
        != null) throw new BusinessException(409, "该体检人已有未归档预约");
    var hospital = catalog.hospital(hp);
    var meal = catalog.meal(sm, hp);
    int type = ((Number) meal.get("type")).intValue();
    if (type != 2 && type != ((Number) person.get("sex")).intValue())
      throw new BusinessException(400, "套餐不适用于该体检人性别");
    // 名额由数据库条件更新；即使多人争抢最后一个名额，也只有一个事务能成功。
    if (db.exec(
            "UPDATE appointment_schedule SET used=used+1 WHERE hpId=#{p.hp} AND"
                + " appointmentDate=#{p.date} AND used<capacity",
            "hp",
            hp,
            "date",
            date)
        != 1) throw new BusinessException(409, "该日期不可预约或名额已满，请重新选择");
    p.put("userId", owner);
    p.put("orderDate", date);
    p.put("identityHash", person.get("identityHash"));
    p.put("personName", person.get("realName"));
    p.put("personSex", person.get("sex"));
    p.put("personBirthday", person.get("birthday"));
    p.put("personIdentityMasked", mask((String) person.get("identityCard")));
    p.put("hospitalName", hospital.get("name"));
    p.put("hospitalAddress", hospital.get("address"));
    p.put("hospitalTelephone", hospital.get("telephone"));
    p.put("businessHours", hospital.get("businessHours"));
    p.put("deadline", hospital.get("deadline"));
    p.put("setmealName", meal.get("name"));
    p.put("amount", meal.get("price"));
    long orderId =
        db.insert(
            "INSERT INTO"
                + " orders(userId,personId,hpId,smId,orderDate,requestId,identityHash,personName,personSex,personBirthday,personIdentityMasked,hospitalName,hospitalAddress,hospitalTelephone,businessHours,deadline,setmealName,amount)"
                + " VALUES(#{p.userId},#{p.personId},#{p.hpId},#{p.smId},#{p.orderDate},#{p.requestId},#{p.identityHash},#{p.personName},#{p.personSex},#{p.personBirthday},#{p.personIdentityMasked},#{p.hospitalName},#{p.hospitalAddress},#{p.hospitalTelephone},#{p.businessHours},#{p.deadline},#{p.setmealName},#{p.amount})",
            p);
    // 快照、订单、演示支付与占用名额同事务提交；基础套餐以后修改不会改写历史。
    snapshot(orderId, meal);
    db.exec(
        "INSERT INTO payment(orderId,amount) VALUES(#{p.id},#{p.amount})",
        "id",
        orderId,
        "amount",
        meal.get("price"));
    return order(orderId);
  }

  @SuppressWarnings("unchecked")
  private void snapshot(long orderId, Map<String, Object> meal) {
    var items = (List<Map<String, Object>>) meal.get("items");
    if (items.isEmpty()) throw new BusinessException(400, "套餐尚未配置检查项目");
    for (var item : items) {
      long cir =
          db.insert(
              "INSERT INTO cireport(orderId,ciId,ciName) VALUES(#{p.order},#{p.ci},#{p.name})",
              "order",
              orderId,
              "ci",
              item.get("ciId"),
              "name",
              item.get("ciName"));
      var details = (List<Map<String, Object>>) item.get("details");
      if (details.isEmpty()) throw new BusinessException(400, "检查项目尚未配置明细");
      for (var d : details) {
        var p = new HashMap<>(d);
        p.put("orderId", orderId);
        p.put("cirId", cir);
        db.exec(
            "INSERT INTO"
                + " cidetailedreport(cirId,orderId,sourceDetailId,name,unit,minrange,maxrange,normalValue,normalValueString,type,required)"
                + " VALUES(#{p.cirId},#{p.orderId},#{p.cdId},#{p.name},#{p.unit},#{p.minrange},#{p.maxrange},#{p.normalValue},#{p.normalValueString},#{p.type},#{p.required})",
            p);
      }
    }
  }

  @Transactional
  public Object cancel(String owner, long id) {
    var row = lock(id);
    if (!owner.equals(row.get("userId"))) throw new BusinessException(403, "不能取消他人的预约");
    if (integer(row, "state", 0) == 3) return order(id);
    if (integer(row, "state", 0) != 1
        || bool(row.get("reportStarted"))
        || !date(row.get("orderDate")).isAfter(today()))
      throw new BusinessException(409, "仅可取消预约日前且尚未录入结果的预约");
    db.exec(
        "UPDATE orders SET state=3,reportVersion=reportVersion+1 WHERE orderId=#{p.id}", "id", id);
    db.exec(
        "UPDATE appointment_schedule SET used=used-1 WHERE hpId=#{p.hp} AND"
            + " appointmentDate=#{p.date} AND used>0",
        "hp",
        row.get("hpId"),
        "date",
        row.get("orderDate"));
    db.exec(
        "UPDATE payment SET status='REFUNDED',refundedAt=CURRENT_TIMESTAMP WHERE orderId=#{p.id}"
            + " AND status='PAID'",
        "id",
        id);
    return order(id);
  }

  private Map<String, Object> lock(long id) {
    var r = db.one("SELECT * FROM orders WHERE orderId=#{p.id} FOR UPDATE", "id", id);
    if (r == null) throw new BusinessException(404, "预约不存在");
    return r;
  }

  public Object list(Principal staff, Map<String, Object> filter) {
    int page = Math.max(1, integer(filter, "page", 1)),
        size = Math.min(100, Math.max(1, integer(filter, "pageSize", 10)));
    var p = new HashMap<>(filter);
    String sql = " FROM orders WHERE 1=1";
    if (!"ADMIN".equals(staff.role())) {
      p.put("hpId", staff.hpId());
      sql += " AND hpId=#{p.hpId}";
    } else if (!text(p, "hpId").isEmpty()) sql += " AND hpId=#{p.hpId}";
    for (String field : List.of("smId", "state", "orderDate"))
      if (!text(p, field).isEmpty()) sql += " AND " + field + "=#{p." + field + "}";
    if (!text(p, "sex").isEmpty()) {
      int sex = integer(p, "sex", -1);
      if (sex != 0 && sex != 1) throw new BusinessException(400, "性别应为0或1");
      p.put("sex", sex);
      sql += " AND personSex=#{p.sex}";
    }
    if (!text(p, "userId").isEmpty()) {
      p.put("phoneLike", "%" + text(p, "userId") + "%");
      sql += " AND userId LIKE #{p.phoneLike}";
    }
    if (!text(p, "realName").isEmpty()) {
      p.put("nameLike", "%" + text(p, "realName") + "%");
      sql += " AND personName LIKE #{p.nameLike}";
    }
    long total = ((Number) db.one("SELECT COUNT(*) AS total" + sql, p).get("total")).longValue();
    p.put("limit", size);
    p.put("offset", ((long) page - 1) * size);
    var rows =
        db.rows(
            "SELECT "
                + ORDER_COLUMNS
                + sql
                + " ORDER BY orderId DESC LIMIT #{p.limit} OFFSET #{p.offset}",
            p);
    return Map.of("items", rows, "total", total, "page", page, "pageSize", size);
  }

  public Map<String, Object> report(long id) {
    var items =
        db.rows(
            "SELECT cirId,ciId,ciName FROM cireport WHERE orderId=#{p.id} ORDER BY cirId",
            "id",
            id);
    for (var i : items)
      i.put(
          "details",
          db.rows(
              "SELECT"
                  + " cidrId,name,unit,minrange,maxrange,normalValue,normalValueString,type,required,value,isError"
                  + " FROM cidetailedreport WHERE cirId=#{p.id} ORDER BY cidrId",
              "id",
              i.get("cirId")));
    return Map.of(
        "order",
        order(id),
        "items",
        items,
        "conclusions",
        db.rows(
            "SELECT orId,title,content FROM overallresult WHERE orderId=#{p.id} ORDER BY orId",
            "id",
            id));
  }

  public Object userReport(String owner, long id) {
    var o = owned(owner, id);
    if (integer(o, "state", 0) != 2) throw new BusinessException(403, "报告尚未归档，暂不可查看");
    return report(id);
  }

  private Map<String, Object> editable(Principal staff, Map<String, Object> p) {
    long id = number(p, "orderId");
    var r = lock(id);
    scoped(staff, id);
    if (integer(r, "state", 0) != 1) throw new BusinessException(409, "仅未归档预约可以编辑");
    if (integer(r, "reportVersion", 0) != integer(p, "version", -1))
      throw new BusinessException(409, "报告已更新，请重新加载后再保存");
    return r;
  }

  private void bump(long id) {
    db.exec(
        "UPDATE orders SET reportVersion=reportVersion+1,reportStarted=1 WHERE orderId=#{p.id}",
        "id",
        id);
  }

  @Transactional
  public Object saveDetails(Principal staff, Map<String, Object> p) {
    editable(staff, p);
    long id = number(p, "orderId");
    var changes = maps(p.get("items"));
    if (changes.isEmpty()) throw new BusinessException(400, "请选择需要保存的检查结果");
    var ids = new HashSet<Long>();
    for (var change : changes) {
      long detail = number(change, "cidrId");
      if (!ids.add(detail)) throw new BusinessException(400, "检查结果重复");
      var d =
          db.one(
              "SELECT * FROM cidetailedreport WHERE cidrId=#{p.detail} AND orderId=#{p.order}",
              "detail",
              detail,
              "order",
              id);
      if (d == null) throw new BusinessException(400, "检查结果不属于当前报告");
      String value = text(change, "value");
      if (value.length() > 500) throw new BusinessException(400, "结果长度不能超过500字");
      int error = error(d, value, integer(change, "isError", 0));
      db.exec(
          "UPDATE cidetailedreport SET value=#{p.value},isError=#{p.error} WHERE cidrId=#{p.id}",
          "value",
          value,
          "error",
          error,
          "id",
          detail);
    }
    bump(id);
    return report(id);
  }

  public static int error(Map<String, Object> detail, String value, int manual) {
    if (value.isBlank()) return 0;
    int type = integer(detail, "type", 4);
    if (type == 1) {
      BigDecimal n = decimal(value);
      return (detail.get("minrange") != null && n.compareTo(decimal(detail.get("minrange"))) < 0)
              || (detail.get("maxrange") != null
                  && n.compareTo(decimal(detail.get("maxrange"))) > 0)
          ? 1
          : 0;
    }
    if (type == 2) return decimal(value).compareTo(decimal(detail.get("normalValue"))) == 0 ? 0 : 1;
    if (manual != 0 && manual != 1) throw new BusinessException(400, "异常标记应为0或1");
    return manual;
  }

  @Transactional
  public Object saveConclusion(Principal staff, Map<String, Object> raw) {
    var p = new HashMap<>(raw);
    editable(staff, p);
    required(p, "title", 100);
    required(p, "content", 1000);
    if (text(p, "orId").isEmpty())
      db.exec(
          "INSERT INTO overallresult(orderId,title,content)"
              + " VALUES(#{p.orderId},#{p.title},#{p.content})",
          p);
    else if (db.exec(
            "UPDATE overallresult SET title=#{p.title},content=#{p.content} WHERE orId=#{p.orId}"
                + " AND orderId=#{p.orderId}",
            p)
        != 1) throw new BusinessException(404, "总检结论不存在");
    long id = number(p, "orderId");
    bump(id);
    return report(id);
  }

  @Transactional
  public Object removeConclusion(Principal staff, Map<String, Object> p) {
    editable(staff, p);
    if (db.exec("DELETE FROM overallresult WHERE orId=#{p.orId} AND orderId=#{p.orderId}", p) != 1)
      throw new BusinessException(404, "总检结论不存在");
    long id = number(p, "orderId");
    bump(id);
    return report(id);
  }

  @Transactional
  public Object archive(Principal staff, Map<String, Object> p) {
    editable(staff, p);
    long id = number(p, "orderId");
    var details = db.rows("SELECT * FROM cidetailedreport WHERE orderId=#{p.id}", "id", id);
    if (details.isEmpty()) throw new BusinessException(400, "报告未创建");
    for (var d : details) {
      String value = text(d, "value");
      if (bool(d.get("required")) && value.isBlank())
        throw new BusinessException(400, "请填写所有必填检查结果");
      error(d, value, integer(d, "isError", 0));
    }
    if (db.one("SELECT orId FROM overallresult WHERE orderId=#{p.id} LIMIT 1", "id", id) == null)
      throw new BusinessException(400, "请先填写总检结论");
    db.exec(
        "UPDATE orders SET"
            + " state=2,reportVersion=reportVersion+1,reportStarted=1,archivedAt=CURRENT_TIMESTAMP"
            + " WHERE orderId=#{p.id}",
        "id",
        id);
    return report(id);
  }
}
