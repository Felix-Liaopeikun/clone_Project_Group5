package com.example.dockb.util;

import com.example.dockb.common.BizException;
import com.example.dockb.common.ResultCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 文件安全校验工具类。
 *
 * <p>通过文件头魔数（Magic Bytes）验证上传文件的真实类型，防止文件类型伪装攻击。
 */
@Slf4j
public final class TrustedDocuments {

    /** PDF 文件魔数 */
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF

    /** ZIP/DOCX 文件魔数 */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // PK..

    private TrustedDocuments() {
        // utility class
    }

    /**
     * 通过魔数验证文件类型是否与扩展名匹配。
     *
     * @param inputStream 文件输入流
     * @param extension   文件扩展名（小写）
     * @throws BizException 文件内容与扩展名不匹配时抛出
     */
    public static void validateMagicBytes(InputStream inputStream, String extension) {
        if (extension == null) return;
        String ext = extension.toLowerCase();

        try {
            byte[] header = new byte[4];
            int read = inputStream.read(header);

            // 空文件跳过校验（后续业务层处理）
            if (read < 4) return;

            switch (ext) {
                case "pdf":
                    if (!Arrays.equals(header, PDF_MAGIC)) {
                        log.warn("[Security] PDF 魔数校验失败: header={}", bytesToHex(header));
                        throw new BizException(ResultCode.FILE_TYPE_MISMATCH,
                                "文件内容与扩展名(.pdf)不符，拒绝上传");
                    }
                    break;
                case "docx":
                    if (!Arrays.equals(header, ZIP_MAGIC)) {
                        log.warn("[Security] DOCX 魔数校验失败: header={}", bytesToHex(header));
                        throw new BizException(ResultCode.FILE_TYPE_MISMATCH,
                                "文件内容与扩展名(.docx)不符，拒绝上传");
                    }
                    break;
                case "txt":
                case "md":
                    // 纯文本文件：检查是否为二进制（含 null 字节）
                    for (byte b : header) {
                        if (b == 0x00) {
                            log.warn("[Security] 文本文件包含 null 字节，可能为二进制文件");
                            throw new BizException(ResultCode.FILE_TYPE_MISMATCH,
                                    "文件内容包含二进制数据，拒绝作为文本文件上传");
                        }
                    }
                    break;
                default:
                    // 未知类型：不做魔数校验
                    break;
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[Security] 魔数校验读取失败: {}", e.getMessage());
            // 读取失败时放行（由后续解析层处理）
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }
}
