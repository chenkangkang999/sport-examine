package com.neusoft.health.service;

import static org.junit.jupiter.api.Assertions.*;

import com.neusoft.health.dto.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;

class ValidationTest {
  @Test
  void numericRangesAndEqualityAreCalculatedOnServer() {
    assertEquals(
        1, OrderService.error(Map.of("type", 1, "minrange", 90, "maxrange", 140), "141", 0));
    assertEquals(
        0, OrderService.error(Map.of("type", 1, "minrange", 90, "maxrange", 140), "140", 1));
    assertEquals(1, OrderService.error(Map.of("type", 2, "normalValue", 0), "1", 0));
    assertThrows(
        BusinessException.class,
        () -> OrderService.error(Map.of("type", 1, "minrange", 90), "abc", 0));
  }

  @Test
  void bcryptInputLimitCountsUtf8Bytes() {
    assertThrows(
        BusinessException.class,
        () -> Values.password(Map.of("password", "中".repeat(25)), "password"));
    assertDoesNotThrow(() -> Values.password(Map.of("password", "Health123!"), "password"));
  }

  @Test
  void invalidIdentityIsRejected() {
    var p =
        new HashMap<String, Object>(
            Map.of(
                "realName",
                "测试",
                "sex",
                1,
                "birthday",
                "1990-01-01",
                "identityCard",
                "110101199001010010"));
    assertThrows(BusinessException.class, () -> Values.identity(p));
  }
}
