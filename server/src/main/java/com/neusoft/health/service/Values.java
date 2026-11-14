package com.neusoft.health.service;

import com.neusoft.health.dto.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public final class Values {
  private Values() {}

  public static String text(Map<String, Object> m, String k) {
    Object o = m.get(k);
    return o == null ? "" : o.toString().trim();
  }

  public static String required(Map<String, Object> m, String k, int max) {
    String s = text(m, k);
    if (s.isEmpty() || s.length() > max) throw new BusinessException(400, k + "不能为空且长度不能超过" + max);
    return s;
  }

  public static void bounded(Map<String, Object> m, String key, int max) {
    if (text(m, key).length() > max) throw new BusinessException(400, key + "长度不能超过" + max);
  }

  public static long number(Map<String, Object> m, String k) {
    try {
      return Long.parseLong(text(m, k));
    } catch (Exception e) {
      throw new BusinessException(400, k + "必须是整数");
    }
  }

  public static int integer(Map<String, Object> m, String k, int def) {
    if (text(m, k).isEmpty()) return def;
    long n = number(m, k);
    if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
      throw new BusinessException(400, k + "超出整数范围");
    return (int) n;
  }

  public static boolean bool(Object o) {
    return Boolean.TRUE.equals(o) || "1".equals(String.valueOf(o));
  }

  public static LocalDate date(Object o) {
    try {
      return LocalDate.parse(String.valueOf(o).substring(0, 10));
    } catch (Exception e) {
      throw new BusinessException(400, "日期格式应为 yyyy-MM-dd");
    }
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }

  public static String mask(String value) {
    return value == null || value.length() < 8
        ? ""
        : value.substring(0, 3) + "***********" + value.substring(value.length() - 4);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void identity(Map<String, Object> m) {
    String c = required(m, "identityCard", 18).toUpperCase(Locale.ROOT);
    if (!c.matches("[1-9][0-9]{16}[0-9X]")) throw new BusinessException(400, "请输入18位身份证号");
    int[] w = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    String checks = "10X98765432";
    int sum = 0;
    for (int i = 0; i < 17; i++) sum += (c.charAt(i) - '0') * w[i];
    if (checks.charAt(sum % 11) != c.charAt(17)) throw new BusinessException(400, "身份证校验位不正确");
    LocalDate b;
    try {
      b =
          LocalDate.of(
              Integer.parseInt(c.substring(6, 10)),
              Integer.parseInt(c.substring(10, 12)),
              Integer.parseInt(c.substring(12, 14)));
    } catch (Exception e) {
      throw new BusinessException(400, "身份证出生日期不正确");
    }
    if (b.isAfter(today()) || b.isBefore(today().minusYears(130)))
      throw new BusinessException(400, "出生日期不正确");
    int sex = integer(m, "sex", -1);
    if (sex != 0 && sex != 1) throw new BusinessException(400, "请选择性别");
    if (sex != (c.charAt(16) - '0') % 2) throw new BusinessException(400, "性别与证件不一致");
    if (!text(m, "birthday").isEmpty() && !date(m.get("birthday")).equals(b))
      throw new BusinessException(400, "生日与身份证不一致");
    m.put("identityCard", c);
    m.put("birthday", b);
    m.put("identityHash", hash(c));
    required(m, "realName", 40);
  }

  public static String password(Map<String, Object> m, String key) {
    String s = text(m, key);
    if (s.length() < 8 || s.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(400, "密码至少8位且UTF-8长度不超过72字节");
    return s;
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> maps(Object value) {
    if (!(value instanceof List<?> l)) return List.of();
    for (Object v : l) if (!(v instanceof Map)) throw new BusinessException(400, "列表格式不正确");
    return (List<Map<String, Object>>) value;
  }

  public static BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (Exception e) {
      throw new BusinessException(400, "请输入合法数值");
    }
  }
}
