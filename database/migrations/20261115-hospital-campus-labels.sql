-- 将两所默认机构统一为和平院区、建设院区；仅匹配指定医院的已知旧默认名称。
-- 与后端初始化使用同一个一次性版本标记；重跑不覆盖管理员后续修改。
-- 仅更新名称及历史通知中的名称片段，不更新地址、金额、检查结果或消息已读时间。
SET NAMES utf8mb4;
START TRANSACTION;
SET @health_apply_campus_labels = NOT EXISTS (
  SELECT 1 FROM seed_history WHERE seedKey = 'hospital-campus-labels-20261115'
);

UPDATE hospital SET name = '云医院-和平院区'
WHERE @health_apply_campus_labels = 1 AND hpId = 1
  AND (BINARY name = BINARY '云医院体检中心'
    OR BINARY name = BINARY '安阳云医院体检中心（演示）');

UPDATE hospital SET name = '云医院-建设院区'
WHERE @health_apply_campus_labels = 1 AND hpId = 2
  AND (BINARY name = BINARY '东软健康体检中心'
    OR BINARY name = BINARY '东软健康体检中心（演示）');

UPDATE orders SET hospitalName = '云医院-和平院区'
WHERE @health_apply_campus_labels = 1 AND hpId = 1
  AND (BINARY hospitalName = BINARY '云医院体检中心'
    OR BINARY hospitalName = BINARY '安阳云医院体检中心（演示）');

UPDATE orders SET hospitalName = '云医院-建设院区'
WHERE @health_apply_campus_labels = 1 AND hpId = 2
  AND (BINARY hospitalName = BINARY '东软健康体检中心'
    OR BINARY hospitalName = BINARY '东软健康体检中心（演示）');

UPDATE user_notification n JOIN orders o ON o.orderId = n.orderId
SET n.content = REPLACE(REPLACE(n.content,
  ' · 云医院体检中心 · ', ' · 云医院-和平院区 · '),
  ' · 安阳云医院体检中心（演示） · ', ' · 云医院-和平院区 · ')
WHERE @health_apply_campus_labels = 1 AND o.hpId = 1
  AND (LOCATE(BINARY ' · 云医院体检中心 · ', BINARY n.content) > 0
    OR LOCATE(BINARY ' · 安阳云医院体检中心（演示） · ', BINARY n.content) > 0);

UPDATE user_notification n JOIN orders o ON o.orderId = n.orderId
SET n.content = REPLACE(REPLACE(n.content,
  ' · 东软健康体检中心 · ', ' · 云医院-建设院区 · '),
  ' · 东软健康体检中心（演示） · ', ' · 云医院-建设院区 · ')
WHERE @health_apply_campus_labels = 1 AND o.hpId = 2
  AND (LOCATE(BINARY ' · 东软健康体检中心 · ', BINARY n.content) > 0
    OR LOCATE(BINARY ' · 东软健康体检中心（演示） · ', BINARY n.content) > 0);

INSERT IGNORE INTO seed_history(seedKey)
SELECT 'hospital-campus-labels-20261115' WHERE @health_apply_campus_labels = 1;
COMMIT;
