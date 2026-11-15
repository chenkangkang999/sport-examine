<script setup>
import { dateText, sexText } from '../state';

defineProps({
  report: { type: Object, required: true },
  formatRange: { type: Function, required: true },
});
function resultText(value) {
  return value == null || String(value).trim() === '' ? '—' : value;
}
</script>

<template>
  <!-- 独立于 480px 页面容器，避免固定导航、容器查询和标签切换限制打印内容。 -->
  <Teleport to="body">
    <article class="report-print-root" aria-label="完整体检报告打印版">
      <header class="print-report-heading">
        <p>{{ report.order.hospitalName }}</p>
        <h1>体检报告</h1>
        <span>体检日期：{{ dateText(report.order.orderDate) }}</span>
      </header>

      <dl class="print-report-person">
        <div>
          <dt>体检人</dt>
          <dd>{{ report.order.personName }}</dd>
        </div>
        <div>
          <dt>性别</dt>
          <dd>{{ sexText(report.order.personSex) }}</dd>
        </div>
        <div>
          <dt>出生日期</dt>
          <dd>{{ dateText(report.order.personBirthday) }}</dd>
        </div>
        <div>
          <dt>证件号码</dt>
          <dd>{{ report.order.personIdentityMasked }}</dd>
        </div>
        <div>
          <dt>预约编号</dt>
          <dd>{{ report.order.orderId }}</dd>
        </div>
        <div>
          <dt>体检套餐</dt>
          <dd>{{ report.order.setmealName }}</dd>
        </div>
        <div class="print-person-full">
          <dt>机构地址</dt>
          <dd>{{ report.order.hospitalAddress || '—' }}</dd>
        </div>
      </dl>

      <section class="print-report-results">
        <h2>检查结果</h2>
        <table v-for="item in report.items" :key="item.cirId" class="print-report-table">
          <colgroup>
            <col class="print-col-name" />
            <col class="print-col-value" />
            <col class="print-col-unit" />
            <col class="print-col-range" />
            <col class="print-col-flag" />
          </colgroup>
          <thead>
            <tr>
              <th colspan="5" class="print-group-heading">{{ item.ciName }}</th>
            </tr>
            <tr>
              <th scope="col">检查项目</th>
              <th scope="col">结果</th>
              <th scope="col">单位</th>
              <th scope="col">参考范围</th>
              <th scope="col">提示</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="detail in item.details" :key="detail.cidrId">
              <th scope="row">{{ detail.name }}</th>
              <td>{{ resultText(detail.value) }}</td>
              <td>{{ detail.unit || '—' }}</td>
              <td>{{ formatRange(detail) || '—' }}</td>
              <td :class="{ 'print-result-abnormal': Number(detail.isError) === 1 }">
                {{ Number(detail.isError) === 1 ? '异常' : '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="print-report-conclusions">
        <h2>总检结论与建议</h2>
        <article
          v-for="(result, index) in report.conclusions"
          :key="result.orId"
          class="print-report-conclusion"
        >
          <h3>{{ index + 1 }}、{{ result.title }}</h3>
          <p>{{ result.content }}</p>
        </article>
        <p v-if="!report.conclusions?.length">暂无总检结论。</p>
      </section>
      <footer class="print-report-footer">
        预约编号 {{ report.order.orderId }} · {{ report.order.hospitalName }}
      </footer>
    </article>
  </Teleport>
</template>

<style>
.report-print-root {
  display: none;
}

/* 命名页面只用于本报告，不改变用户在其他页面调用打印时的纸张设置。 */
@page health-report {
  size: A4;
  margin: 14mm;
}

@media print {
  html:has(body > .report-print-root),
  body:has(> .report-print-root) {
    width: auto !important;
    min-width: 0 !important;
    height: auto !important;
    min-height: 0 !important;
    margin: 0 !important;
    padding: 0 !important;
    background: #fff !important;
    overflow: visible !important;
    container-type: normal !important;
  }
  body:has(> .report-print-root) > :not(.report-print-root),
  body:has(> .report-print-root)::before,
  body:has(> .report-print-root)::after {
    display: none !important;
  }
  .report-print-root {
    display: block !important;
    page: health-report;
    width: auto;
    max-width: none;
    margin: 0;
    padding: 0;
    position: static;
    color: #222;
    background: #fff;
    font-family: 'Microsoft YaHei', 'SimSun', sans-serif;
    font-size: 10pt;
    line-height: 1.6;
    overflow: visible;
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }
  .report-print-root * {
    box-sizing: border-box;
    overflow-wrap: anywhere;
  }
  .print-report-heading {
    text-align: center;
    margin-bottom: 6mm;
    padding-bottom: 4mm;
    border-bottom: 1px solid #555;
    break-inside: avoid;
  }
  .print-report-heading p,
  .print-report-heading h1 {
    margin: 0;
  }
  .print-report-heading p {
    font-size: 12pt;
  }
  .print-report-heading h1 {
    margin: 2mm 0;
    font-size: 20pt;
    line-height: 1.5;
  }
  .print-report-heading span {
    font-size: 9pt;
  }
  .print-report-person {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 2mm 6mm;
    margin: 0 0 6mm;
    break-inside: avoid;
  }
  .print-report-person > div {
    display: grid;
    grid-template-columns: 19mm minmax(0, 1fr);
    gap: 2mm;
  }
  .print-report-person .print-person-full {
    grid-column: 1 / -1;
  }
  .print-report-person dt,
  .print-report-person dd {
    margin: 0;
  }
  .print-report-person dt {
    color: #555;
  }
  .report-print-root h2 {
    margin: 6mm 0 3mm;
    padding-left: 2.5mm;
    border-left: 2px solid #555;
    font-size: 12pt;
    line-height: 1.6;
    break-after: avoid;
  }
  .print-report-table {
    width: 100%;
    table-layout: fixed;
    border-collapse: collapse;
    margin-bottom: 4mm;
    font-size: 9pt;
    line-height: 1.6;
  }
  .print-col-name {
    width: 24%;
  }
  .print-col-value {
    width: 34%;
  }
  .print-col-unit {
    width: 12%;
  }
  .print-col-range {
    width: 20%;
  }
  .print-col-flag {
    width: 10%;
  }
  .print-report-table thead {
    display: table-header-group;
  }
  .print-report-table tr {
    break-inside: avoid;
  }
  .print-report-table th,
  .print-report-table td {
    padding: 2mm 2.5mm;
    border: 1px solid #bbb;
    text-align: left;
    vertical-align: top;
    white-space: pre-wrap;
    font-weight: 400;
  }
  .print-report-table thead th {
    background: #f3f5f5;
    font-weight: 600;
  }
  .print-report-table .print-group-heading {
    background: #eaf0f0;
    font-size: 10pt;
  }
  .print-result-abnormal {
    color: #a5362d;
    font-weight: 600 !important;
  }
  .print-report-conclusion h3 {
    margin: 4mm 0 1.5mm;
    font-size: 10pt;
    break-after: avoid;
  }
  .print-report-conclusion p {
    margin: 0;
    white-space: pre-wrap;
    widows: 3;
    orphans: 3;
  }
  .print-report-footer {
    margin-top: 8mm;
    padding-top: 3mm;
    border-top: 1px solid #bbb;
    font-size: 8pt;
    color: #666;
    text-align: right;
    break-inside: avoid;
  }
}
</style>
