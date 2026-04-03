package com.example.ruleengine.service;

import com.example.ruleengine.domain.NameListEntry;
import com.example.ruleengine.model.dto.CreateNameListEntryRequest;
import com.example.ruleengine.repository.NameListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NameListService {

    private final NameListRepository nameListRepository;

    /** Hot-path: check if a value exists in a list within a scope */
    public boolean existsInList(String listKey, String listType, String keyType, String keyValue) {
        return nameListRepository.existsActiveEntry(listKey, listType, keyType, keyValue);
    }

    @Transactional
    public NameListEntry createEntry(CreateNameListEntryRequest request, String operator) {
        NameListEntry entry = NameListEntry.builder()
                .listKey(request.getListKey() != null && !request.getListKey().isBlank()
                        ? request.getListKey() : "GLOBAL")
                .listType(request.getListType())
                .keyType(request.getKeyType())
                .keyValue(request.getKeyValue())
                .reason(request.getReason())
                .source(request.getSource())
                .createdBy(operator)
                .build();

        if (request.getExpiredAt() != null && !request.getExpiredAt().isBlank()) {
            entry.setExpiredAt(java.time.LocalDateTime.parse(request.getExpiredAt(),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return nameListRepository.save(entry);
    }

    public NameListEntry getEntry(Long id) {
        return nameListRepository.findById(id).orElse(null);
    }

    public Page<NameListEntry> listEntries(String listKey, String listType, String keyType, Pageable pageable) {
        if (listKey != null && listType != null && keyType != null) {
            return nameListRepository.findByListKeyAndListTypeAndKeyType(listKey, listType, keyType, pageable);
        } else if (listKey != null && listType != null) {
            return nameListRepository.findByListKeyAndListType(listKey, listType, pageable);
        } else if (listKey != null) {
            return nameListRepository.findByListKey(listKey, pageable);
        }
        return nameListRepository.findAll(pageable);
    }

    @Transactional
    public void deleteEntry(Long id) {
        nameListRepository.deleteById(id);
    }

    /** 获取所有不重复的 listKey */
    public List<String> getDistinctListKeys() {
        return nameListRepository.findDistinctListKeys();
    }
}
