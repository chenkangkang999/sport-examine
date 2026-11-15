// 只把后端已归档的数值与参考值用于展示，不据此生成诊断。
export function numericValue(value) {
  if (value == null || String(value).trim() === '') return null;
  const result = Number(value);
  return Number.isFinite(result) ? result : null;
}

export function formatReference(detail) {
  const lower = numericValue(detail.minrange);
  const upper = numericValue(detail.maxrange);
  if (Number(detail.type) === 1) {
    if (lower != null && upper != null) return `${lower} - ${upper}`;
    if (lower != null) return `≥ ${lower}`;
    if (upper != null) return `≤ ${upper}`;
    return '';
  }
  if (Number(detail.type) === 2)
    return (
      detail.normalValueString ||
      (numericValue(detail.normalValue) == null ? '' : String(Number(detail.normalValue)))
    );
  return detail.normalValueString || '';
}

export function numericReference(detail) {
  if (Number(detail.type) === 1) {
    const lower = numericValue(detail.minrange),
      upper = numericValue(detail.maxrange);
    if (lower == null && upper == null) return null;
    if (lower != null && upper != null && lower > upper) return null;
    return { type: 'range', lower, upper };
  }
  if (Number(detail.type) === 2) {
    const target = numericValue(detail.normalValue);
    return target == null ? null : { type: 'target', target };
  }
  return null;
}
