-- 修正旧默认医院文案在历史订单中的快照；正常的新订单继续保存预约时的医院资料。
-- 仅处理指定医院且字段仍严格等于旧默认文案的记录，可重复执行。
-- 不修改个人信息、金额、套餐、检查结果或管理员自定义资料。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE orders SET hospitalName = '云医院体检中心'
WHERE hpId = 1 AND BINARY hospitalName = BINARY '安阳云医院体检中心（演示）';

UPDATE orders SET hospitalName = '东软健康体检中心'
WHERE hpId = 2 AND BINARY hospitalName = BINARY '东软健康体检中心（演示）';

UPDATE orders SET hospitalAddress = '健康路1号'
WHERE hpId = 1 AND BINARY hospitalAddress = BINARY '安阳市健康路1号（虚构地址）';

UPDATE orders SET hospitalAddress = '康宁路2号'
WHERE hpId = 2 AND BINARY hospitalAddress = BINARY '安阳市康宁路2号（虚构地址）';

INSERT IGNORE INTO seed_history(seedKey) VALUES ('order-hospital-labels-20261114');
COMMIT;
