package com.example.ruleengine.service.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 密码强度校验器
 * 要求：最少 8 位，必须包含大写字母、小写字母和数字
 */
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");

    /**
     * 校验密码强度
     *
     * @param password 待校验密码
     * @return 校验结果，通过返回 null，不通过返回错误消息
     */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "密码长度不能少于 " + MIN_LENGTH + " 位";
        }

        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            return "密码必须包含至少一个大写字母";
        }

        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            return "密码必须包含至少一个小写字母";
        }

        if (!DIGIT_PATTERN.matcher(password).find()) {
            return "密码必须包含至少一个数字";
        }

        return null;
    }

    /**
     * 校验密码强度，返回所有不满足条件的错误信息
     *
     * @param password 待校验密码
     * @return 错误列表，空列表表示通过
     */
    public static List<String> validateAll(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (password != null) {
            if (!UPPERCASE_PATTERN.matcher(password).find()) {
                errors.add("密码必须包含至少一个大写字母");
            }
            if (!LOWERCASE_PATTERN.matcher(password).find()) {
                errors.add("密码必须包含至少一个小写字母");
            }
            if (!DIGIT_PATTERN.matcher(password).find()) {
                errors.add("密码必须包含至少一个数字");
            }
        }

        return errors;
    }
}
