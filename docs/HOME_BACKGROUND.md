# 用户端柔和医疗背景

2026年11月15日，按最新要求为用户端内容页统一浅色医疗底图，手机与电脑中间的应用区域同步，电脑两侧保留原灰色。图片仅作为页面底层背景，内容卡片、表单、导航和弹窗使用不透明白底。沿用内置 imagegen 生成的图片，未重新生成或调用额外图片接口。

- 最终资源：[user-web/public/img/home-care-background-v1.png](../user-web/public/img/home-care-background-v1.png)，1536×1024，约1.58 MB。网站运行时从本地 `/img/home-care-background-v1.png` 读取，无第三方图片请求。
- 图片风格：浅青绿、米白、水彩感，听诊器、医疗十字及叶片集中在边缘，中央保持留白。
- 背景层 `care-backdrop` 与应用壳同宽，最大480px、水平居中，不延伸到电脑两侧；放在内容下方，设置 `aria-hidden` 和 `pointer-events:none`。进入登录页自动移除。
- 主题集中在 `user-web/src/care-theme.css`，`care-shell` 标记普通用户页，仅将页面画布变为透明以展示底图。未将内容卡片、表单、导航或弹窗变透明。
- 卡片、标题、图片、导航及原有内容尺寸保持不变。彩色登录页（含白色登录框）、个人顶部、成功页渐变、报告蓝色标题与状态色均保留，管理端不调整。
- 打印组件继续使用独立白底；打印媒体中隐藏底图，不影响A4输出。
- 当前版本已检查390px下全部用户页面、五步预约、档案和套餐对比弹窗、注册与资料编辑，以及953px电脑个人中心。内容白底不透图、桌面外侧灰色、登录与彩色区域均符合预期；另复验大字模式、一屏菜单、套餐对比和A4打印。
- 图片随项目源码维护，运行不依赖生成工具或外部图片服务；本轮其他改动见 [更新记录](../CHANGELOG.md)。

## 最终生成提示词

```text
Use case: stylized-concept. Asset type: an understated raster background image behind the homepage of a Chinese cloud hospital health-check booking website, NOT a UI mockup or banner. Create one elegant, extremely light, welcoming medical wellness illustration, landscape ratio about 3:2. Ivory-white background, delicate pale mint, powder aqua, warm cream, and very faint peach. Airy watercolor-like atmospheric washes, soft ambient daylight, subtle frosted-glass texture. At the far outer edges only, very faint fine-line details of a stethoscope, a small rounded medical cross, and a couple of natural leaves, loosely cropped and sparsely arranged. Keep the entire middle 65 percent nearly empty and very light for readable cards; visual interest concentrated near corners and outer margins, staying faint enough to function as a background. Gentle, calming, professional, humane healthcare mood. Palette and medical theme should coordinate with an existing teal Chinese medical app. No people, no busy hospital room, no large icons, no grid or repeated pattern, no typography, no letters, no numbers, no logos, no text, no watermark, no buttons or interface elements. Mostly near-white with identifiable but understated mint medical illustration detail. Opaque background.
```
