package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.exception.NotLoginException
import cn.dev33.satoken.exception.NotPermissionException
import cn.dev33.satoken.exception.NotRoleException
import com.eatmoreduck.ruleengine.admin.dto.ErrorResponse
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
 * 全局异常处理：将 Sa-Token 异常与业务异常转换为旧契约的标准 JSON 响应
 * （{code, message, error} 三字段结构，错误文案逐字一致，前端仅读取 message）。
 */
@RestControllerAdvice
class AdminExceptionHandler {
    private val log = LoggerFactory.getLogger(AdminExceptionHandler::class.java)

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
    fun handleNotRole(e: NotRoleException): ResponseEntity<ErrorResponse> {
        log.debug("角色不足: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(403, "无角色权限: ${e.role}"))
    }

    /** 业务参数/状态错误 → 400 */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse.of(400, e.message ?: "请求参数错误"))

    /**
     * 状态冲突 → 400（守卫消息保留业务语义）。
     *
     * 与旧后端的差异点：旧实现的状态冲突（IllegalStateException）落入兜底 500"服务器内部错误"，
     * 业务消息丢失；按本批任务约定统一收敛为 400 类错误响应，message 保留具体守卫原因。
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse.of(400, e.message ?: "状态冲突"))

    /** Bean Validation 失败 → 400（字段名: 消息，分号连接，与旧实现一致） */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // 按字段名排序：Bean Validation 的字段遍历顺序不保证（跨 JVM 运行漂移），
        // 排序保证同一请求的错误消息确定（前端仅整体展示 message，顺序无契约含义）
        val message =
            e.bindingResult.fieldErrors
                .sortedBy { fieldError -> fieldError.field }
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
