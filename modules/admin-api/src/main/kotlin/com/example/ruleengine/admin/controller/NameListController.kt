package com.example.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.example.ruleengine.admin.dto.CreateNameListEntryRequest
import com.example.ruleengine.admin.dto.NameListEntryResponse
import com.example.ruleengine.admin.dto.PageResponse
import com.example.ruleengine.admin.namelist.NameListService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 黑白名单管理 REST API 控制器（路径/方法/权限码与旧 NameListController 一致）。
 *
 * 说明：旧实现的创建入口将 operator 硬编码为 "system"（不读请求头），此处保持一致。
 */
@RestController
@RequestMapping("/api/v1/name-list")
@SaCheckLogin
class NameListController(
    private val nameListService: NameListService,
) {
    private val log = LoggerFactory.getLogger(NameListController::class.java)

    /** 添加名单条目：POST /api/v1/name-list */
    @PostMapping
    @SaCheckPermission("api:name-list:manage")
    fun create(
        @Valid @RequestBody request: CreateNameListEntryRequest,
    ): ResponseEntity<NameListEntryResponse> {
        log.info(
            "添加名单条目: listKey={}, {} {} {}",
            request.listKey,
            request.listType,
            request.keyType,
            request.keyValue,
        )
        return ResponseEntity.ok(nameListService.createEntry(request, LEGACY_OPERATOR))
    }

    /** 单条详情：GET /api/v1/name-list/{id}（缺失 → 404 空体，照旧） */
    @GetMapping("/{id}")
    @SaCheckPermission("api:name-list:view")
    fun get(
        @PathVariable id: Long,
    ): ResponseEntity<NameListEntryResponse> {
        val entry = nameListService.getEntry(id)
        return if (entry != null) ResponseEntity.ok(entry) else ResponseEntity.notFound().build()
    }

    /** 分页列表（listKey/listType/keyType 递进过滤）：GET /api/v1/name-list */
    @GetMapping
    @SaCheckPermission("api:name-list:view")
    fun list(
        @RequestParam(required = false) listKey: String?,
        @RequestParam(required = false) listType: String?,
        @RequestParam(required = false) keyType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<NameListEntryResponse>> =
        ResponseEntity.ok(nameListService.listEntries(listKey, listType, keyType, page, size))

    /** 全部不重复的 listKey：GET /api/v1/name-list/list-keys */
    @GetMapping("/list-keys")
    @SaCheckPermission("api:name-list:view")
    fun listKeys(): ResponseEntity<List<String>> = ResponseEntity.ok(nameListService.getDistinctListKeys())

    /** 删除条目（物理删除，204 空体照旧）：DELETE /api/v1/name-list/{id} */
    @DeleteMapping("/{id}")
    @SaCheckPermission("api:name-list:delete")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        log.info("删除名单条目: id={}", id)
        nameListService.deleteEntry(id)
        return ResponseEntity.noContent().build()
    }

    companion object {
        /** 旧 NameListController 的创建入口恒以 "system" 落 createdBy */
        private const val LEGACY_OPERATOR = "system"
    }
}
