package com.example.dockb.common;

/**
 * 业务响应码枚举。
 *
 * <p>0 成功；非 0 失败。前端按 code 判断成败，按 message 提示。
 */
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务自定义错误
    DOCUMENT_NOT_FOUND(1001, "文档不存在"),
    FILE_EMPTY(1002, "上传文件为空"),
    FILE_TYPE_INVALID(1003, "不支持的文件类型"),
    FILE_READ_FAILED(1004, "文件读取失败"),
    UPLOAD_FAILED(1005, "文件保存失败"),
    QUESTION_EMPTY(1101, "问题不能为空"),
    M3_UNREACHABLE(1201, "M3 服务不可达"),
    M3_BAD_RESPONSE(1202, "M3 返回解析失败"),

    // 安全相关
    RATE_LIMITED(429, "请求过于频繁，请稍后重试"),
    CSRF_INVALID(403, "CSRF 校验失败"),
    FILE_TYPE_MISMATCH(1006, "文件内容与扩展名不符，存在安全风险");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}