-- 为已初始化的课程库增加站内消息和机构须知；不删除、重置或改写原业务记录。
-- 后端启动同样会执行 CREATE TABLE IF NOT EXISTS，并幂等回填历史通知。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS user_notification (
 notificationId BIGINT PRIMARY KEY AUTO_INCREMENT,userId VARCHAR(11) NOT NULL,orderId BIGINT NOT NULL,type VARCHAR(20) NOT NULL,title VARCHAR(100) NOT NULL,content VARCHAR(500) NOT NULL,createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,readAt TIMESTAMP NULL,
 UNIQUE KEY uq_notification_event(orderId,type),KEY ix_notification_owner(userId,readAt,createdAt),FOREIGN KEY(userId) REFERENCES users(userId),FOREIGN KEY(orderId) REFERENCES orders(orderId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS hospital_guidance (
 hpId BIGINT PRIMARY KEY,preparation VARCHAR(2000) NOT NULL,arrivalSteps VARCHAR(2000) NOT NULL,updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(hpId) REFERENCES hospital(hpId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;
INSERT IGNORE INTO user_notification(userId,orderId,type,title,content,createdAt)
SELECT userId,orderId,'BOOKED','预约成功',
 CONCAT('预约编号 ',orderId,' · ',hospitalName,' · ',DATE_FORMAT(orderDate,'%Y-%m-%d'),'。预约已创建，请核对日期并按时到院。'),createdAt
FROM orders;
INSERT IGNORE INTO user_notification(userId,orderId,type,title,content,createdAt)
SELECT o.userId,o.orderId,'CANCELLED','预约已取消',
 CONCAT('预约编号 ',o.orderId,' · ',o.hospitalName,' · ',DATE_FORMAT(o.orderDate,'%Y-%m-%d'),'。预约已取消，相应名额已释放，订单历史仍然保留。'),COALESCE(p.refundedAt,o.createdAt)
FROM orders o LEFT JOIN payment p ON p.orderId=o.orderId WHERE o.state=3;
INSERT IGNORE INTO user_notification(userId,orderId,type,title,content,createdAt)
SELECT userId,orderId,'ARCHIVED','体检报告已归档',
 CONCAT('预约编号 ',orderId,' · ',hospitalName,' · ',DATE_FORMAT(orderDate,'%Y-%m-%d'),'。报告已经归档，可以查看完整结果和总检结论。'),COALESCE(archivedAt,createdAt)
FROM orders WHERE state=2;
COMMIT;
