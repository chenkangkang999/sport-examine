// 课程前端使用本地科普内容；来源供读者查阅，不代替个人的报告解读或诊疗建议。
// 编校日期属于本项目，原文发布日期以来源页面为准。
export const healthArticles = [
  {
    slug: 'lung-nodules',
    title: '体检查出肺结节，下一步怎么办？',
    description: '先看完整报告，再把检查资料留好。',
    category: '检查知识',
    image: '/img/info1.png',
    imageAlt: '肺部健康科普插图',
    compiledOn: '2026-11-14',
    summary:
      '看到“肺结节”时，先了解它是影像中的发现，不能仅凭这个名称判断良恶性。带着完整资料由医生评估，比只盯着某一个数字更有帮助。',
    sections: [
      {
        title: '先认识报告中的“结节”',
        paragraphs: [
          '肺结节描述的是肺部影像中的局部病灶，形成原因不止一种。医生会结合大小、密度、形态和个人情况综合判断，不能把“有结节”直接等同于“确诊肺癌”。',
        ],
      },
      {
        title: '复诊前，把这些资料准备好',
        items: [
          '保留本次完整检查报告和影像资料，不只保存截取的一句话。',
          '如有以往胸部检查，带上相应报告，方便医生比较变化。',
          '整理吸烟、职业接触、既往病史及家族史等信息，就诊时向医生说明。',
        ],
      },
      {
        title: '随访时间要因人而异',
        paragraphs: [
          '不同结节和不同风险人群的复查安排可能不同。由医生判断是否需要随访，以及使用什么检查、何时复查；不要把他人的复查时间直接套用到自己身上。',
          '如果随访中出现咯血、胸痛等不适，应及时就医，而不是只等待原定复查日期。',
        ],
      },
      {
        title: '给自己的一个小清单',
        items: [
          '记录医生确定的下一步安排和复查日期。',
          '将历次资料按检查日期整理，复诊时一起携带。',
          '有疑问时请医生解释报告中的术语，避免仅靠网络图片自行判断。',
        ],
      },
    ],
    sources: [
      {
        name: '北京市卫生健康委员会｜影像科普：别让“小结节”变成“大麻烦”',
        url: 'https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202504/t20250402_4053442.html',
      },
      {
        name: '国家卫生健康委员会｜肺癌筛查与早诊早治方案（2024年版）',
        url: 'https://www.nhc.gov.cn/ylyjs/gzdt/202408/42f43b5f0c4c4ebe90c78ee127959b92.shtml',
      },
    ],
  },
  {
    slug: 'thyroid-nodules',
    title: '体检发现甲状腺结节，先了解这些',
    description: '了解报告信息，和医生一起确定下一步。',
    category: '报告阅读',
    image: '/img/info2.png',
    imageAlt: '甲状腺检查科普插图',
    compiledOn: '2026-11-14',
    summary:
      '甲状腺结节常在超声检查中被发现。读报告时，应结合完整描述、风险分级和医生意见，不凭结节数量或大小单独下结论。',
    sections: [
      {
        title: '“结节”是一个检查发现',
        paragraphs: [
          '甲状腺结节是甲状腺内的局部病变。结节是否影响健康，需要结合性质、大小、变化和功能状态评估，不能仅凭“有结节”几个字判断具体病情。',
        ],
      },
      {
        title: '报告里可以关注哪些信息',
        items: [
          '结节的位置、大小及超声描述。',
          '是否有 TI-RADS 等风险分级，以及报告给出的建议。',
          '与以往同类检查相比是否有变化。',
        ],
        paragraphs: [
          '风险分级用于帮助医生评估，不等同于独立的最终诊断。是否需要其他检查，应由医生结合资料决定。',
        ],
      },
      {
        title: '随访安排由医生确定',
        paragraphs: [
          '超声是评估甲状腺结节的常用方法。不同检查结果的后续安排并不相同，复查间隔和进一步评估方式应按个人情况确定，不能只参照另一位体检者的经历。',
          '若结节近期明显变化，或出现持续声音嘶哑、吞咽及呼吸不适，应及时就医评估。',
        ],
      },
      {
        title: '就诊时带上完整资料',
        items: [
          '本次与以往的超声报告，已有的甲状腺相关检查结果。',
          '近期症状、既往病史以及需要向医生说明的家族史。',
          '想咨询的问题清单，并记录医生给出的随访安排。',
        ],
      },
    ],
    sources: [
      {
        name: '北京市卫生健康委员会｜科学对待甲状腺结节',
        url: 'https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jbzs/202606/t20260623_4711819.html',
      },
      {
        name: '中华医学会｜甲状腺结节知多少？',
        url: 'https://www.cma.org.cn/art/2023/6/28/art_4584_51517.html',
      },
    ],
  },
];

export function findHealthArticle(slug) {
  return healthArticles.find((article) => article.slug === slug);
}

export function readingMinutes(article) {
  const text = [
    article.summary,
    ...article.sections.flatMap((section) => [
      section.title,
      ...(section.paragraphs || []),
      ...(section.items || []),
    ]),
  ].join('');
  return Math.max(1, Math.ceil(text.length / 300));
}
