# 前后端接口约定

所有接口返回 `{code:0,message:"成功",data:...}`。异常使用 400/401/403/404/409 HTTP 状态，响应仍为 `{code,message,data:null}`。日期为 `yyyy-MM-dd`，金额单位元。开发端通过 Vite proxy 转发 `/tijian` 和 `/tijiancms`。

## 会话

GET `/tijian/auth/session` 返回 `{csrfToken,user}`；GET `/tijiancms/auth/session` 返回 `{csrfToken,staff}`。写请求包括 `X-CSRF-Token`，首次请求先读取会话。两种身份可以同时登录并单独退出。所有 POST 请求使用 JSON。

- POST `/tijian/users/saveUsers` 注册并登录：`{userId,password,realName,sex,birthday,identityCard}`。
- POST `/tijian/users/getUsersByUserIdByPass` 登录：`{userId,password}`。
- POST `/tijian/auth/logout`；GET `/tijian/users/me`；POST `/tijian/users/updateProfile`；POST `/tijian/users/changePassword` `{oldPassword,newPassword}`。
- POST `/tijiancms/doctor/getDoctorByCodeByPass` `{docCode,password}`；POST `/tijiancms/auth/logout`。
- 用户字段：`userId,realName,sex,birthday,identityCardMasked`。管理身份：`docId,docCode,realName,role,hpId`，role 为 ADMIN/DOCTOR。

## 用户接口

- GET `/tijian/persons/list`；POST `/tijian/persons/save` `{personId?,realName,sex,birthday,identityCard,relationship}`；POST `/tijian/persons/remove` `{personId}`。档案包含 `personId,isSelf,identityCardMasked`；不返回完整证件；更新时 identityCard 留空保留原值。
- GET `/tijian/hospital/listHospital`；GET `/tijian/hospital/getHospitalById?hpId=1`。医院字段 `hpId,name,picture,telephone,address,businessHours,deadline,state`。
- GET `/tijian/setmeal/listSetmealByType?hpId=1&type=1`；GET `/tijian/setmeal/getSetmealById?hpId=1&smId=1`。套餐字段 `smId,name,type,price,state,items`；type 1男/0女/2不限。
- GET `/tijian/calendar/listAppointmentCalendar?hpId=1&year=2026&month=11`，返回 `[{date,capacity,used,remaining,available}]`。
- POST `/tijian/orders/saveOrders` `{personId,hpId,smId,orderDate,requestId}` 返回订单。
- GET `/tijian/orders/listOrdersByUserId?state=&personId=` 返回数组；GET `/tijian/orders/getOrdersById?orderId=1`；POST `/tijian/orders/removeOrders` `{orderId}`。
- GET `/tijian/reports/list?personId=` 返回已归档订单数组；GET `/tijian/reports/detail?orderId=1` 返回报告。

## 医生接口

- GET `/tijiancms/orders/listOrders?page=1&pageSize=10&userId=&realName=&sex=&smId=&orderDate=&state=&hpId=` 返回 `{items,total,page,pageSize}`。sex 为 0 女、1 男，留空表示全部；筛选同时遵守医生所属医院限制。
- GET `/tijiancms/orders/getOrdersById?orderId=1`。
- GET `/tijiancms/ciReport/listCiReport?orderId=1` 返回报告；POST `/tijiancms/ciReport/createReportTemplate` `{orderId}`，幂等返回报告。
- POST `/tijiancms/ciDetailedReport/updateCiDetailedReport` `{orderId,version,items:[{cidrId,value,isError}]}`。
- POST `/tijiancms/overallResult/saveOverallResult` `{orderId,version,title,content,orId?}`；POST `/tijiancms/overallResult/removeOverallResult` `{orderId,version,orId}`。
- POST `/tijiancms/orders/updateOrdersState` `{orderId,version}` 归档。
- 以上所有报告写入返回更新后的报告对象。
- GET `/tijiancms/setmeal/listSetmeal` 返回所有套餐，供筛选使用。

## 通用对象

订单字段：`orderId,personId,personName,personSex,personBirthday,personIdentityMasked,userId,hpId,smId,hospitalName,hospitalAddress,hospitalTelephone,businessHours,deadline,setmealName,amount,orderDate,state,reportVersion,reportStarted`。state 为 1未归档、2已归档、3已取消；reportStarted 为布尔值。

报告：`{order,items,conclusions}`。

items：`[{cirId,ciId,ciName,details:[{cidrId,name,unit,minrange,maxrange,normalValue,normalValueString,type,value,isError,required}]}]`；基础套餐的 items 使用 `ciId,ciName,ciContent,details`，details 主键为 `cdId`。

conclusions：`[{orId,title,content}]`，title 和 content 均必填。type 为 1范围数值、2等值数值、3/4/5文本。isError 为数字 0/1；required 为布尔值。更新版本使用 `order.reportVersion`。

## 管理员接口

GET `/tijiancms/admin/{resource}` 返回数组；POST `/tijiancms/admin/{resource}/save` 保存返回行；POST `/tijiancms/admin/{resource}/disable` `{id}` 停用。

resource 为 `hospitals,setmeals,checkitems,doctors`。主键分别 `hpId,smId,ciId,docId`。保存可传主键更新。

- hospitals：`name,picture,telephone,address,businessHours,deadline,state`。
- setmeals：`name,type,price,state,ciIds:[1,2]`，列表返回 ciIds。
- checkitems：`ciName,ciContent,meaning,remarks,state,details:[{name,unit,minrange,maxrange,normalValue,normalValueString,type,required}]`。
- doctors：`docCode,realName,password,sex,deptno,hpId,role,state`，修改时空密码保留；不返回密码哈希。
- GET `/tijiancms/admin/hospital-setmeals?hpId=1` 返回 smId 数组；POST `/tijiancms/admin/hospital-setmeals/save` `{hpId,smIds:[1,2]}`。
- GET `/tijiancms/admin/schedules?hpId=1&from=2026-11-01&to=2026-11-30` 返回 `[{hpId,date,capacity,used,remaining,available}]`；POST `/tijiancms/admin/schedules/save` `{hpId,from,to,capacity,weekdays:[1,2,3,4,5]}`。区间中未勾选的星期容量为0，变更不能低于已用名额。

## 新增体验接口

以下接口沿用会话与 CSRF 校验、统一响应和错误码。GET 返回只读数据，POST 写请求必须带 `X-CSRF-Token`。

| 方法与路径                                     | 参数                                  | data                                                               |
| ---------------------------------------------- | ------------------------------------- | ------------------------------------------------------------------ |
| GET `/tijian/dashboard`                        | 无                                    | `{appointment,latestReport,unreadCount}`，订单对象可为 null        |
| GET `/tijian/notifications`                    | `page=1,pageSize=20,unreadOnly=false` | `{items,total,page,pageSize,unreadCount}`                          |
| POST `/tijian/notifications/read`              | `{id}`                                | `{unreadCount}`                                                    |
| POST `/tijian/notifications/read-all`          | `{}`                                  | `{unreadCount}`                                                    |
| GET `/tijian/reports/trends`                   | `personId`                            | `{personId,personName,series}`                                     |
| GET `/tijian/orders/guidance`                  | `orderId`                             | `{preparation,arrivalSteps}`                                       |
| GET `/tijiancms/workbench`                     | `hpId?`                               | `{todayCount,pendingEntry,pendingArchive,archivedCount,todayDate}` |
| GET `/tijiancms/admin/hospital-guidance`       | `hpId`                                | `{hpId,preparation,arrivalSteps}`                                  |
| POST `/tijiancms/admin/hospital-guidance/save` | `{hpId,preparation,arrivalSteps}`     | 保存后的机构指南                                                   |
| GET `/tijiancms/admin/statistics`              | `from?,to?,hpId?`，最多366天          | `{from,to,hpId,totals,daily,popularMeals,hospitalSummary}`         |

消息字段：`{id,orderId,type,title,content,isRead,createdAt}`，type 为 BOOKED/CANCELLED/ARCHIVED。只能读取或修改本人的消息；单条和全部已读均幂等。历史通知在启动时补齐，不重置已读状态。

趋势 series：`[{key,sourceDetailId,name,unit,type,points:[{orderId,orderDate,value,minrange,maxrange,normalValue,normalValueString,isError}]}]`。仅返回该账号拥有的体检人已归档报告；不同单位、类型或源明细分开，同次参考范围使用历史快照。类型3的文本只有可以转换为数值时才参与趋势。报告 `items.details` 同时增加 `sourceDetailId`，用于跳转定位。

订单查询额外接受 `workStage=unstarted|started|today`：分别限定未开始录入、已开始录入、今日有效预约。其他值返回400；与已有筛选条件共同生效。医生的医院范围由后端强制指定，即使提交其他 hpId 也不能越过自身范围。`pendingArchive` 为“录入中／待归档”，不代表结果已全部填写。

统计仅限管理员。`totals` 为 `{appointments,active,archived,cancelled,capacity,used,utilization,archiveRate}`；`daily` 每日多一个 `date` 且不含 archiveRate；`popularMeals` 为 `{smId,name,appointments,amount}` 数组；`hospitalSummary` 为 `{hpId,name,appointments,active,archived,cancelled}` 数组。订单与名额均按体检日期计，使用率与归档率返回0–100的百分数，分母为零时为0。归档率为已归档／有效预约数，不计取消；热门套餐仅计有效订单，采用历史套餐名。

指南由管理员维护，订单读取要求归属当前账号。两段文案各不超过2000字符；空白使用通用提示，不虚构具体检查准备要求。

## 演示账号

- 用户 `13800000001 / Health123!`
- 和平院区（hpId=1）医生 `D001 / Doctor123!`，建设院区（hpId=2）医生 `D002 / Doctor123!`
- 管理员 `admin / Admin123!`
