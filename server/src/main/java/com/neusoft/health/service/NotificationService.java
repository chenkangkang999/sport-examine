package com.neusoft.health.service;

import static com.neusoft.health.service.Values.*;

import com.neusoft.health.dto.BusinessException;
import com.neusoft.health.mapper.Database;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 消息与业务写入共用事务；唯一约束保证重试与历史回填均不会重复通知。 */
@Service
public class NotificationService {
  private final Database db;

  public NotificationService(Database db) {
    this.db = db;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void backfill() {
    for (String type : List.of("BOOKED", "CANCELLED", "ARCHIVED")) record(null, type);
  }

  @Transactional
  public void record(Long orderId, String type) {
    String title;
    String description;
    String timestamp;
    String condition;
    switch (type) {
      case "BOOKED" -> {
        title = "预约成功";
        description = "预约已创建，请核对日期并按时到院。";
        timestamp = "o.createdAt";
        condition = "1=1";
      }
      case "CANCELLED" -> {
        title = "预约已取消";
        description = "预约已取消，相应名额已释放，订单历史仍然保留。";
        timestamp = "COALESCE(p.refundedAt,o.createdAt)";
        condition = "o.state=3";
      }
      case "ARCHIVED" -> {
        title = "体检报告已归档";
        description = "报告已经归档，可以查看完整结果和总检结论。";
        timestamp = "COALESCE(o.archivedAt,o.createdAt)";
        condition = "o.state=2";
      }
      default -> throw new IllegalArgumentException("Unknown notification type");
    }
    var params = Database.params("type", type, "title", title, "description", description);
    if (orderId != null) {
      condition += " AND o.orderId=#{p.orderId}";
      params.put("orderId", orderId);
    }
    db.exec(
        "INSERT IGNORE INTO user_notification(userId,orderId,type,title,content,createdAt) SELECT"
            + " o.userId,o.orderId,#{p.type},#{p.title}, CONCAT('预约编号 ',o.orderId,' ·"
            + " ',o.hospitalName,' · ',DATE_FORMAT(o.orderDate,'%Y-%m-%d'),'。',#{p.description}),"
            + timestamp
            + " FROM orders o LEFT JOIN payment p ON p.orderId=o.orderId WHERE "
            + condition,
        params);
  }

  public long unread(String owner) {
    return ((Number)
            db.one(
                    "SELECT COUNT(*) AS total FROM user_notification WHERE userId=#{p.owner} AND"
                        + " readAt IS NULL",
                    "owner",
                    owner)
                .get("total"))
        .longValue();
  }

  public Map<String, Object> list(String owner, int page, int pageSize, boolean unreadOnly) {
    page = Math.max(1, page);
    pageSize = Math.min(100, Math.max(1, pageSize));
    String where = " FROM user_notification WHERE userId=#{p.owner}";
    if (unreadOnly) where += " AND readAt IS NULL";
    var params =
        Database.params("owner", owner, "limit", pageSize, "offset", ((long) page - 1) * pageSize);
    long total =
        ((Number) db.one("SELECT COUNT(*) AS total" + where, params).get("total")).longValue();
    var storedRows =
        db.rows(
            "SELECT notificationId AS id,orderId,type,title,content,createdAt,readAt"
                + where
                + " ORDER BY createdAt DESC,notificationId DESC LIMIT #{p.limit} OFFSET"
                + " #{p.offset}",
            params);
    // 不修改 MyBatis 会话缓存中的行；同一事务重复查询仍须保留真实 readAt。
    var rows = new ArrayList<Map<String, Object>>();
    for (var storedRow : storedRows) {
      var row = new HashMap<>(storedRow);
      row.put("isRead", row.get("readAt") != null);
      row.remove("readAt");
      rows.add(row);
    }
    return Map.of(
        "items",
        rows,
        "total",
        total,
        "page",
        page,
        "pageSize",
        pageSize,
        "unreadCount",
        unread(owner));
  }

  @Transactional
  public Object read(String owner, long id) {
    var row =
        db.one(
            "SELECT userId FROM user_notification WHERE notificationId=#{p.id} FOR UPDATE",
            "id",
            id);
    if (row == null) throw new BusinessException(404, "消息不存在");
    if (!owner.equals(row.get("userId"))) throw new BusinessException(403, "无权访问此消息");
    db.exec(
        "UPDATE user_notification SET readAt=COALESCE(readAt,CURRENT_TIMESTAMP)"
            + " WHERE notificationId=#{p.id} AND userId=#{p.owner}",
        "id",
        id,
        "owner",
        owner);
    return Map.of("unreadCount", unread(owner));
  }

  @Transactional
  public Object readAll(String owner) {
    db.exec(
        "UPDATE user_notification SET readAt=CURRENT_TIMESTAMP WHERE userId=#{p.owner} AND readAt"
            + " IS NULL",
        "owner",
        owner);
    return Map.of("unreadCount", unread(owner));
  }
}
