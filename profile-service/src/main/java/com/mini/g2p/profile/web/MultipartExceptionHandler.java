package com.mini.g2p.profile.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class MultipartExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> handleMaxSize(MaxUploadSizeExceededException ex) {
    return ResponseEntity
        .status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(Map.of(
            "error", "file_too_large",
            "message", "File exceeds maximum allowed size"
        ));
  }
}
