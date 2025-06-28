package org.example.miniodemo.common.util;

import java.time.LocalDate;

/**
 * 文件路径处理工具类。
 * <p>
 * 提供静态方法来处理文件在对象存储中的路径生成和解析，
 * 以避免在多个服务中出现重复代码。
 */
public final class FilePathUtil {

    private FilePathUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 根据日期、文件哈希和原始文件名构建结构化的对象存储路径。
     *
     * @param originalFileName 文件的原始名称。
     * @param fileHash         文件的内容哈希。
     * @return 生成的对象存储路径，格式为 "YYYY/MM/DD/hash/originalFileName"。
     */
    public static String buildDateBasedPath(String originalFileName, String fileHash, String folderPath) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        return String.join("/", folderPath,year, month, day, fileHash, originalFileName);
    }

    /**
     * 从一个结构化的对象存储路径中提取文件哈希值。
     * <p>
     * 假设路径格式为 ".../hash/filename"。
     *
     * @param path 完整的对象存储路径。
     * @return 提取出的哈希字符串，如果路径格式不匹配则返回 null。
     */
    public static String extractHashFromPath(String path) {
        if (path == null) {
            return null;
        }
        String[] parts = path.split("/");
        // 倒数第二部分是哈希值
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return null;
    }
} 