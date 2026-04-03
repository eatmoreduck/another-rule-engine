package com.example.ruleengine.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateNameListEntryRequest {
    @NotBlank(message = "名单类型不能为空")
    private String listType;  // BLACK or WHITE

    /** 名单 Key，默认 GLOBAL，可传 flowKey 做决策流级隔离 */
    private String listKey;

    @NotBlank(message = "键类型不能为空")
    private String keyType;   // ID_NO, DEVICE_ID, IP, PHONE_NO, MAC_ADDR

    @NotBlank(message = "键值不能为空")
    private String keyValue;

    private String reason;
    private String source;
    private String expiredAt; // ISO datetime string, optional
}
