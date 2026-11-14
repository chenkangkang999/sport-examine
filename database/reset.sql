-- 仅在明确需要清空本项目数据时执行；先确认已连接正确数据库。
-- 建议先 mysqldump 备份。本文件不删除数据库与账号。
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS payment,overallresult,cidetailedreport,cireport,orders,appointment_schedule,hospital_setmeal,setmealdetailed,checkitemdetailed,checkitem,setmeal,person,doctor,users,hospital,seed_history;
SET FOREIGN_KEY_CHECKS=1;
-- 执行 schema.sql 或重启后端重建结构；启用 SEED_ENABLED 才会生成虚构演示数据。
