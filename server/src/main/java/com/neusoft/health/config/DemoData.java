package com.neusoft.health.config;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.entity.Principal;
import com.neusoft.health.mapper.Database;
import com.neusoft.health.service.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoData implements ApplicationRunner {
  private final Database db;
  private final AccountService accounts;
  private final OrderService orders;

  @Value("${health.seed-enabled:true}")
  private boolean enabled;

  public DemoData(Database db, AccountService accounts, OrderService orders) {
    this.db = db;
    this.accounts = accounts;
    this.orders = orders;
  }

  /** 初始化缺失数据；升级默认文案时仅匹配旧默认值，不重写管理员已修改的资料。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    boolean first = db.one("SELECT seedKey FROM seed_history WHERE seedKey='catalog-v1'") == null;
    if (first) {
      db.exec(
          "INSERT IGNORE INTO"
              + " hospital(hpId,name,picture,telephone,address,businessHours,deadline,state)"
              + " VALUES(1,'云医院体检中心','/img/hospita1.png','0372-0000001','健康路1号','周一至周五"
              + " 7:30-15:30','采血截止"
              + " 10:30',1),(2,'东软健康体检中心','/img/hospita2.png','0372-0000002','康宁路2号','周一至周五"
              + " 7:30-15:30','采血截止 10:30',1)");
      db.exec(
          "INSERT IGNORE INTO checkitem(ciId,ciName,ciContent,meaning,remarks)"
              + " VALUES(1,'一般检查','血压、身高、体重','了解基本身体状况','检查前保持安静'),(2,'血常规','白细胞、血红蛋白','了解血液细胞基本情况','结果仅作课程演示'),(3,'内科检查','听诊及常规问诊','记录医生检查所见','由医生填写')");
      detail(1, 1, "收缩压", "mmHg", 90, 140, null, 1);
      detail(2, 1, "舒张压", "mmHg", 60, 90, null, 1);
      detail(3, 1, "身高", "cm", null, null, null, 3);
      detail(4, 1, "体重", "kg", null, null, null, 3);
      detail(5, 2, "白细胞计数", "10^9/L", 3.5, 9.5, null, 1);
      detail(6, 2, "血红蛋白", "g/L", 115, 175, null, 1);
      detail(7, 3, "内科检查所见", "", null, null, null, 4);
      db.exec(
          "INSERT IGNORE INTO setmeal(smId,name,type,price)"
              + " VALUES(1,'男士基础体检',1,350.00),(2,'女士基础体检',0,380.00),(3,'通用健康体检',2,299.00),(4,'男士全面体检',1,580.00),(5,'女士全面体检',0,620.00)");
      for (long sm = 1; sm <= 5; sm++)
        for (long ci = 1; ci <= (sm >= 4 ? 3 : 2); ci++)
          db.exec(
              "INSERT IGNORE INTO setmealdetailed(smId,ciId) VALUES(#{p.sm},#{p.ci})",
              "sm",
              sm,
              "ci",
              ci);
      // 关联仅在首次初始化时写入，医院已有管理配置时不重新补回被删除的关联。
      for (long hp = 1; hp <= 2; hp++)
        for (long sm = 1; sm <= 5; sm++)
          db.exec(
              "INSERT IGNORE INTO hospital_setmeal(hpId,smId) VALUES(#{p.hp},#{p.sm})",
              "hp",
              hp,
              "sm",
              sm);
      staff(1, "D001", "演示医生甲", 1L, "DOCTOR", "Doctor123!");
      staff(2, "D002", "演示医生乙", 2L, "DOCTOR", "Doctor123!");
      staff(3, "admin", "系统管理员", null, "ADMIN", "Admin123!");
    }
    migrateHospitalLabels();
    migrateOrderHospitalLabels();
    for (var hospital : db.rows("SELECT hpId FROM hospital WHERE state=1"))
      for (int i = 0; i <= 60; i++) {
        LocalDate d = today().plusDays(i);
        db.exec(
            "INSERT IGNORE INTO appointment_schedule(hpId,appointmentDate,capacity)"
                + " VALUES(#{p.hp},#{p.date},#{p.cap})",
            "hp",
            hospital.get("hpId"),
            "date",
            d,
            "cap",
            d.getDayOfWeek().getValue() <= 5 ? 200 : 0);
      }
    if (db.one("SELECT userId FROM users WHERE userId='13800000001'") == null) {
      accounts.register(
          new HashMap<>(
              Map.of(
                  "userId",
                  "13800000001",
                  "password",
                  "Health123!",
                  "realName",
                  "演示用户",
                  "sex",
                  1,
                  "birthday",
                  "1990-01-01",
                  "identityCard",
                  identity("11010119900101001"))));
    }
    if (first
        && db.one(
                "SELECT orderId FROM orders WHERE userId='13800000001' AND"
                    + " requestId='seed-archived-report-v1'")
            == null) {
      var self =
          db.one("SELECT personId FROM person WHERE userId='13800000001' AND isSelf=1 AND state=1");
      if (self != null) {
        LocalDate d = today().plusDays(1);
        while (d.getDayOfWeek().getValue() > 5) d = d.plusDays(1);
        var m =
            orders.create(
                "13800000001",
                new HashMap<>(
                    Map.of(
                        "personId",
                        self.get("personId"),
                        "hpId",
                        1,
                        "smId",
                        1,
                        "orderDate",
                        d.toString(),
                        "requestId",
                        "seed-archived-report-v1")));
        long id = ((Number) m.get("orderId")).longValue();
        var fields =
            db.rows(
                "SELECT cidrId,sourceDetailId FROM cidetailedreport WHERE orderId=#{p.id}",
                "id",
                id);
        var values = new ArrayList<Map<String, Object>>();
        String[] results = {"", "145", "80", "175", "72", "5.6", "140", "未见明显异常"};
        for (var field : fields)
          values.add(
              new HashMap<>(
                  Map.of(
                      "cidrId",
                      field.get("cidrId"),
                      "value",
                      results[((Number) field.get("sourceDetailId")).intValue()],
                      "isError",
                      0)));
        var staff = new Principal(null, 1L, "DOCTOR", 1L);
        orders.saveDetails(
            staff, new HashMap<>(Map.of("orderId", id, "version", 0, "items", values)));
        orders.saveConclusion(
            staff,
            new HashMap<>(
                Map.of(
                    "orderId",
                    id,
                    "version",
                    1,
                    "title",
                    "演示报告：血压偏高",
                    "content",
                    "虚构数据，仅用于展示录入、异常提示与报告归档流程。")));
        orders.archive(staff, new HashMap<>(Map.of("orderId", id, "version", 2)));
      }
    }
    if (first) db.exec("INSERT INTO seed_history(seedKey) VALUES('catalog-v1')");
  }

  private void migrateHospitalLabels() {
    String key = "hospital-labels-20261112";
    if (db.one("SELECT seedKey FROM seed_history WHERE seedKey=#{p.key}", "key", key) != null)
      return;
    // 逐字段匹配原始默认文案，不覆盖管理员的自定义名称或地址，也不改写历史订单快照。
    // BINARY 比较保留严格匹配语义，包括字段末尾的空格。
    db.exec(
        "UPDATE hospital SET name='云医院体检中心' WHERE hpId=1"
            + " AND BINARY name=BINARY '安阳云医院体检中心（演示）'");
    db.exec(
        "UPDATE hospital SET name='东软健康体检中心' WHERE hpId=2"
            + " AND BINARY name=BINARY '东软健康体检中心（演示）'");
    db.exec(
        "UPDATE hospital SET address='健康路1号' WHERE hpId=1"
            + " AND BINARY address=BINARY '安阳市健康路1号（虚构地址）'");
    db.exec(
        "UPDATE hospital SET address='康宁路2号' WHERE hpId=2"
            + " AND BINARY address=BINARY '安阳市康宁路2号（虚构地址）'");
    db.exec("INSERT IGNORE INTO seed_history(seedKey) VALUES(#{p.key})", "key", key);
  }

  private void migrateOrderHospitalLabels() {
    String key = "order-hospital-labels-20261114";
    if (db.one("SELECT seedKey FROM seed_history WHERE seedKey=#{p.key}", "key", key) != null)
      return;
    // 仅修正旧默认占位文案；价格、套餐、结果等历史快照及管理员自定义名称均保持不变。
    // 不按医院当前资料动态覆盖订单，避免真正的后续资料变更影响历史记录。
    db.exec(
        "UPDATE orders SET hospitalName='云医院体检中心' WHERE hpId=1"
            + " AND BINARY hospitalName=BINARY '安阳云医院体检中心（演示）'");
    db.exec(
        "UPDATE orders SET hospitalName='东软健康体检中心' WHERE hpId=2"
            + " AND BINARY hospitalName=BINARY '东软健康体检中心（演示）'");
    db.exec(
        "UPDATE orders SET hospitalAddress='健康路1号' WHERE hpId=1"
            + " AND BINARY hospitalAddress=BINARY '安阳市健康路1号（虚构地址）'");
    db.exec(
        "UPDATE orders SET hospitalAddress='康宁路2号' WHERE hpId=2"
            + " AND BINARY hospitalAddress=BINARY '安阳市康宁路2号（虚构地址）'");
    db.exec("INSERT IGNORE INTO seed_history(seedKey) VALUES(#{p.key})", "key", key);
  }

  private void detail(
      long id, long ci, String name, String unit, Number min, Number max, Number normal, int type) {
    db.exec(
        "INSERT IGNORE INTO"
            + " checkitemdetailed(cdId,ciId,name,unit,minrange,maxrange,normalValue,type,required)"
            + " VALUES(#{p.id},#{p.ci},#{p.name},#{p.unit},#{p.min},#{p.max},#{p.normal},#{p.type},1)",
        "id",
        id,
        "ci",
        ci,
        "name",
        name,
        "unit",
        unit,
        "min",
        min,
        "max",
        max,
        "normal",
        normal,
        "type",
        type);
  }

  private void staff(long id, String code, String name, Long hp, String role, String password) {
    if (db.one("SELECT docId FROM doctor WHERE docCode=#{p.code}", "code", code) == null)
      db.exec(
          "INSERT IGNORE INTO doctor(docId,docCode,realName,password,hpId,role)"
              + " VALUES(#{p.id},#{p.code},#{p.name},#{p.password},#{p.hp},#{p.role})",
          "id",
          id,
          "code",
          code,
          "name",
          name,
          "password",
          accounts.hashPassword(password),
          "hp",
          hp,
          "role",
          role);
  }

  public static String identity(String first17) {
    int[] w = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    int sum = 0;
    for (int i = 0; i < 17; i++) sum += (first17.charAt(i) - '0') * w[i];
    return first17 + "10X98765432".charAt(sum % 11);
  }
}
