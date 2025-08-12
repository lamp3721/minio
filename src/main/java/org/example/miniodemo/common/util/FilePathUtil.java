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
     * 根据当前日期和文件信息构建一个基于日期的存储路径。
     * <p>
     * 生成的路径格式为：
     * {folderPath}/{year}/{month}/{day}/{fileHash}/{originalFileName}
     * <p>
     * 例如：
     * "default/2025/08/12/abc123def456/file.txt"
     * <p>
     * 该路径便于按照日期组织存储文件，并且通过文件哈希值避免重复。
     *
     * @param folderPath       顶级文件夹路径，一般用于区分不同业务或用途的文件存储根目录
     * @param fileHash         文件的哈希值，用于标识文件的唯一性
     * @param originalFileName 文件的原始名称，保留文件扩展名等信息
     * @return 构建好的完整存储路径字符串
     */
    public static String buildDateBasedPath(String folderPath, String fileHash, String originalFileName) {
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        return String.join("/", folderPath, year, month, day, fileHash, originalFileName);
    }


    /**
     * 从结构化的对象存储路径中提取文件的哈希值。
     * <p>
     * 假设路径格式为 ".../{hash}/{filename}"，
     * 其中哈希值位于倒数第二个路径段。
     *
     * @param path 对象存储中的完整文件路径，不能为 null。
     * @return 返回路径中提取的哈希字符串；如果路径为空或格式不符合预期，返回 null。
     */
    public static String extractHashFromPath(String path) {
        if (path == null) {
            return null;
        }
        String[] parts = path.split("/");
        // 倒数第二部分通常为哈希值
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return null;
    }

} 