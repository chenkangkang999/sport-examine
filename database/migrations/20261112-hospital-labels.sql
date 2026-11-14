-- 医院展示文案升级：在已初始化的课程项目数据库中执行。
-- 可以重复执行；仅修改指定 ID 且仍为旧默认文案的字段，保留管理员已改资料。
-- 不更新 orders 中的医院名称、地址等历史快照。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE hospital SET name = '云医院体检中心'
WHERE hpId = 1 AND BINARY name = BINARY '安阳云医院体检中心（演示）';

UPDATE hospital SET name = '东软健康体检中心'
WHERE hpId = 2 AND BINARY name = BINARY '东软健康体检中心（演示）';

UPDATE hospital SET address = '健康路1号'
WHERE hpId = 1 AND BINARY address = BINARY '安阳市健康路1号（虚构地址）';

UPDATE hospital SET address = '康宁路2号'
WHERE hpId = 2 AND BINARY address = BINARY '安阳市康宁路2号（虚构地址）';

INSERT IGNORE INTO seed_history(seedKey) VALUES ('hospital-labels-20261112');
COMMIT;
