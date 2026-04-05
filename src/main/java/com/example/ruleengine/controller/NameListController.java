package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.domain.NameListEntry;
import com.example.ruleengine.model.dto.CreateNameListEntryRequest;
import com.example.ruleengine.service.NameListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/name-list")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class NameListController {

    private final NameListService nameListService;

    @PostMapping
    @SaCheckPermission("api:name-list:manage")
    public ResponseEntity<NameListEntry> create(@Valid @RequestBody CreateNameListEntryRequest request) {
        log.info("添加名单条目: listKey={}, {} {} {}", request.getListKey(), request.getListType(), request.getKeyType(), request.getKeyValue());
        NameListEntry entry = nameListService.createEntry(request, "system");
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/{id}")
    @SaCheckPermission("api:name-list:view")
    public ResponseEntity<NameListEntry> get(@PathVariable Long id) {
        NameListEntry entry = nameListService.getEntry(id);
        if (entry == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entry);
    }

    @GetMapping
    @SaCheckPermission("api:name-list:view")
    public ResponseEntity<Page<NameListEntry>> list(
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) String listType,
            @RequestParam(required = false) String keyType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<NameListEntry> result = nameListService.listEntries(listKey, listType, keyType, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/list-keys")
    @SaCheckPermission("api:name-list:view")
    public ResponseEntity<List<String>> listKeys() {
        return ResponseEntity.ok(nameListService.getDistinctListKeys());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("api:name-list:delete")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("删除名单条目: id={}", id);
        nameListService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }
}
