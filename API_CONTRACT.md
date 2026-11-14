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

## 演示账号

- 用户 `13800000001 / Health123!`
- 医院1医生 `D001 / Doctor123!`，医院2医生 `D002 / Doctor123!`
- 管理员 `admin / Admin123!`
