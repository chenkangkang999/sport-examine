package com.neusoft.health.dto;

public record ApiResponse(int code, String message, Object data) {
  public static ApiResponse ok(Object data) {
    return new ApiResponse(0, "成功", data);
  }
}
