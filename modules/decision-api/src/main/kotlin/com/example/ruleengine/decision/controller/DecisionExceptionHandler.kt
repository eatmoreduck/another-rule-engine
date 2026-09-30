package com.example.ruleengine.decision.controller

import cn.dev33.satoken.exception.NotLoginException
import cn.dev33.satoken.exception.NotPermissionException
import cn.dev33.satoken.exception.NotRoleException
import com.example.ruleengine.decision.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 全局异常处理（与 admin-api 同构）：将 Sa-Token 异常与请求格式异常转换为旧契约的
 * 标准三字段 JSON 响应（{code, message, error}，文案逐字一致）。
 * 决策业务异常不经过此处——服务层 fail-safe 后以 200 + REJECT 响应体返回。
 */
@RestControllerAdvice
class DecisionExceptionHandler {
    private val log = LoggerFactory.getLogger(DecisionExceptionHandler::class.java)

    /** 未登录 → 401 */
    @ExceptionHandler(NotLoginException::class)
    fun handleNotLogin(e: NotLoginException): ResponseEntity<ErrorResponse> {
        log.debug("未登录访问: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of(401, "未登录或登录已过期"))
    }

    /** 权限不足 → 403（消息带权限码，与旧实现一致） */
    @ExceptionHandler(NotPermissionException::class)
    fun handleNotPermission(e: NotPermissionException): ResponseEntity<ErrorResponse> {
        log.debug("权限不足: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(403, "无操作权限: ${e.permission}"))
    }

    /** 角色不足 → 403 */
    @ExceptionHandler(NotRoleException::class)
    fun handleNotRole(e: NotRoleException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(403, "无角色权限: ${e.role}"))

    /** Bean Validation 失败 → 400（字段名: 消息，分号连接，与旧实现一致） */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message =
            e.bindingResult.fieldErrors
                .joinToString(separator = "; ") { fieldError -> "${fieldError.field}: ${fieldError.defaultMessage}" }
                .ifEmpty { "请求参数验证失败" }
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, message))
    }

    /** 请求方法不支持 → 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse.of(405, "请求方法不支持: ${e.method}"))

    /** Content-Type 不支持 → 415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ErrorResponse.of(415, "Content-Type 不支持: ${e.contentType}"))

    /** 请求体解析失败 → 400 */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(400, "请求体解析失败: ${e.mostSpecificCause.message}"))

    /** 兜底 → 500 */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("未处理异常", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "服务器内部错误"))
    }
}
