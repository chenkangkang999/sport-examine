const names = ['刘长胜', '孙鹿', '吕文达', '李若辰', '张石凡', '赵桂凤', '李文', '吴晓梦'];
const departments = [
  '健康管理',
  '内科',
  '检验科',
  '超声科',
  '外科',
  '健康管理',
  '内科',
  '营养咨询',
];
const focuses = [
  '体检流程与报告资料整理',
  '内科体检与常见检查说明',
  '检验项目与报告信息核对',
  '影像检查流程与资料准备',
  '外科体检项目说明',
  '家庭体检与健康档案整理',
  '综合体检与复查资料准备',
  '生活方式与健康咨询资料整理',
];
export const doctors = names.map((name, index) => ({
  id: String(index + 1),
  name,
  title: '体检医师',
  department: departments[index],
  image: `/img/doctor${index + 1}.jpg`,
  focus: focuses[index],
  introduction: `${name}参与体检中心的${departments[index]}相关工作，负责检查流程说明、体检资料核对及报告信息整理，帮助体检用户了解需要携带的资料和后续咨询方式。`,
  services: [
    '说明相关体检项目和到院流程',
    '核对既往报告和本次检查资料',
    '记录用户希望向医生咨询的问题',
  ],
  preparation: [
    '有效身份证件和预约信息',
    '已有的相关检查报告，按日期整理',
    '需要咨询的问题及既往病史资料',
  ],
}));
export const findDoctor = (id) => doctors.find((doctor) => doctor.id === String(id));
