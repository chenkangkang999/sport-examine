package com.neusoft.health.service;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final Database db;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public String hashPassword(String value) {
    return encoder.encode(value);
  }

  public AccountService(Database db) {
    this.db = db;
  }

  public Map<String, Object> user(String id) {
    var u =
        db.one(
            "SELECT userId,realName,sex,birthday,identityCard FROM users WHERE userId=#{p.id}",
            "id",
            id);
    if (u == null) throw new BusinessException(401, "请重新登录");
    u.put("identityCardMasked", mask((String) u.remove("identityCard")));
    return u;
  }

  public Map<String, Object> staff(long id) {
    var d =
        db.one(
            "SELECT docId,docCode,realName,sex,deptno,hpId,role,state FROM doctor WHERE"
                + " docId=#{p.id} AND state=1",
            "id",
            id);
    if (d == null) throw new BusinessException(401, "医生账号不可用，请重新登录");
    return d;
  }

  public Principal login(Map<String, Object> p, boolean management) {
    String key = management ? "docCode" : "userId";
    String id = required(p, key, 30);
    String pass = password(p, "password");
    var row =
        db.one(
            management
                ? "SELECT * FROM doctor WHERE docCode=#{p.id} AND state=1"
                : "SELECT * FROM users WHERE userId=#{p.id}",
            "id",
            id);
    if (row == null || !encoder.matches(pass, String.valueOf(row.get("password"))))
      throw new BusinessException(401, "账号或密码不正确");
    return management
        ? new Principal(
            null,
            ((Number) row.get("docId")).longValue(),
            String.valueOf(row.get("role")),
            row.get("hpId") == null ? null : ((Number) row.get("hpId")).longValue())
        : new Principal(id, null, "USER", null);
  }

  @Transactional
  public Principal register(Map<String, Object> raw) {
    var p = new HashMap<>(raw);
    String id = required(p, "userId", 11);
    if (!id.matches("1[3-9][0-9]{9}")) throw new BusinessException(400, "请输入正确的手机号");
    identity(p);
    p.put("password", encoder.encode(password(p, "password")));
    db.exec(
        "INSERT INTO users(userId,password,realName,sex,birthday,identityCard)"
            + " VALUES(#{p.userId},#{p.password},#{p.realName},#{p.sex},#{p.birthday},#{p.identityCard})",
        p);
    p.put("relationship", "本人");
    p.put("isSelf", true);
    insertPerson(p);
    return new Principal(id, null, "USER", null);
  }

  private long insertPerson(Map<String, Object> p) {
    return db.insert(
        "INSERT INTO"
            + " person(userId,realName,sex,birthday,identityCard,identityHash,relationship,isSelf)"
            + " VALUES(#{p.userId},#{p.realName},#{p.sex},#{p.birthday},#{p.identityCard},#{p.identityHash},#{p.relationship},#{p.isSelf})",
        p);
  }

  public List<Map<String, Object>> persons(String id) {
    var rows =
        db.rows(
            "SELECT personId,realName,sex,birthday,identityCard,relationship,isSelf,state FROM"
                + " person WHERE userId=#{p.id} AND state=1 ORDER BY isSelf DESC,personId",
            "id",
            id);
    for (var p : rows) p.put("identityCardMasked", mask((String) p.remove("identityCard")));
    return rows;
  }

  @Transactional
  public Object savePerson(String id, Map<String, Object> raw) {
    var p = new HashMap<>(raw);
    p.put("userId", id);
    Map<String, Object> old = null;
    if (!text(p, "personId").isEmpty()) {
      old =
          db.one(
              "SELECT * FROM person WHERE personId=#{p.personId} AND userId=#{p.userId} AND state=1"
                  + " FOR UPDATE",
              p);
      if (old == null) throw new BusinessException(404, "体检人不存在");
      if (text(p, "identityCard").isEmpty()) p.put("identityCard", old.get("identityCard"));
    }
    identity(p);
    p.put(
        "relationship",
        old != null && bool(old.get("isSelf")) ? "本人" : required(p, "relationship", 20));
    if (old != null) {
      if (!Objects.equals(old.get("identityHash"), p.get("identityHash"))
          && db.one("SELECT orderId FROM orders WHERE personId=#{p.personId} AND state=1", p)
              != null) throw new BusinessException(409, "存在未归档预约，不能修改证件");
      db.exec(
          "UPDATE person SET"
              + " realName=#{p.realName},sex=#{p.sex},birthday=#{p.birthday},identityCard=#{p.identityCard},identityHash=#{p.identityHash},relationship=#{p.relationship}"
              + " WHERE personId=#{p.personId}",
          p);
      if (bool(old.get("isSelf")))
        db.exec(
            "UPDATE users SET"
                + " realName=#{p.realName},sex=#{p.sex},birthday=#{p.birthday},identityCard=#{p.identityCard}"
                + " WHERE userId=#{p.userId}",
            p);
    } else {
      var removed =
          db.one(
              "SELECT personId FROM person WHERE userId=#{p.userId} AND"
                  + " identityHash=#{p.identityHash} AND state=2 FOR UPDATE",
              p);
      if (removed == null) {
        p.put("isSelf", false);
        p.put("personId", insertPerson(p));
      } else {
        p.put("personId", removed.get("personId"));
        db.exec(
            "UPDATE person SET"
                + " realName=#{p.realName},sex=#{p.sex},birthday=#{p.birthday},relationship=#{p.relationship},state=1"
                + " WHERE personId=#{p.personId}",
            p);
      }
    }
    return persons(id).stream()
        .filter(x -> String.valueOf(x.get("personId")).equals(String.valueOf(p.get("personId"))))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public Object updateProfile(String id, Map<String, Object> p) {
    var self = db.one("SELECT personId FROM person WHERE userId=#{p.id} AND isSelf=1", "id", id);
    var v = new HashMap<>(p);
    v.put("personId", self.get("personId"));
    savePerson(id, v);
    return user(id);
  }

  @Transactional
  public Object removePerson(String id, Map<String, Object> p) {
    var x = new HashMap<>(p);
    x.put("userId", id);
    var row =
        db.one(
            "SELECT * FROM person WHERE personId=#{p.personId} AND userId=#{p.userId} FOR UPDATE",
            x);
    if (row == null) throw new BusinessException(404, "档案不存在");
    if (bool(row.get("isSelf"))) throw new BusinessException(400, "本人档案不能删除");
    if (db.one("SELECT orderId FROM orders WHERE personId=#{p.personId} AND state=1", x) != null)
      throw new BusinessException(409, "存在未归档预约，不能删除档案");
    db.exec("UPDATE person SET state=2 WHERE personId=#{p.personId}", x);
    return true;
  }

  @Transactional
  public Object changePassword(String id, Map<String, Object> p) {
    var row = db.one("SELECT password FROM users WHERE userId=#{p.id} FOR UPDATE", "id", id);
    if (!encoder.matches(password(p, "oldPassword"), String.valueOf(row.get("password"))))
      throw new BusinessException(400, "原密码不正确");
    db.exec(
        "UPDATE users SET password=#{p.password} WHERE userId=#{p.id}",
        "password",
        encoder.encode(password(p, "newPassword")),
        "id",
        id);
    return true;
  }
}
