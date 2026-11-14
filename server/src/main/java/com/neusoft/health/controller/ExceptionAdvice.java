package com.neusoft.health.controller;

import com.neusoft.health.dto.*;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ExceptionAdvice {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(new ApiResponse(e.status, e.getMessage(), null));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ApiResponse> duplicate(Exception e) {
    return ResponseEntity.status(409)
        .body(new ApiResponse(409, "手机号、工号、体检人档案或有效预约重复，请检查后重试", null));
  }

  @ExceptionHandler({CannotAcquireLockException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ApiResponse> conflict(Exception e) {
    return ResponseEntity.status(409).body(new ApiResponse(409, "数据正在更新，请刷新后重试", null));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    NumberFormatException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ApiResponse> invalid(Exception e) {
    return ResponseEntity.badRequest().body(new ApiResponse(400, "请求参数格式不正确", null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse> unexpected(Exception e) {
    LoggerFactory.getLogger(getClass()).error("请求处理异常", e);
    return ResponseEntity.internalServerError().body(new ApiResponse(500, "服务器处理失败，请稍后重试", null));
  }
}
