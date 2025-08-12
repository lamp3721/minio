package org.example.miniodemo.common.util;

import org.springframework.util.StringUtils;

/**
 * 路径验证和净化工具类。
 * <p>
 * 用于防御目录遍历攻击 (Path Traversal)。
 */
public final class PathValidationUtil {

    private PathValidationUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 清理并验证文件路径，防止目录遍历攻击和绝对路径注入。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>使用 {@link StringUtils#cleanPath} 对路径进行规范化，例如将 "a/../b" 转换为 "b"。</li>
     *   <li>检查规范化后的路径是否包含目录遍历序列 ".."，阻止类似 "../evil.txt" 的攻击。</li>
     *   <li>检查路径是否以 "/" 开头，防止绝对路径注入。</li>
     * </ol>
     *
     * @param path 来自用户输入的原始路径，不能为空。
     * @return 返回经过清理和验证后的安全相对路径。
     * @throws IllegalArgumentException 如果路径为 null，或者包含不安全的路径内容（如 ".." 或以 "/" 开头）。
     */
    public static String clean(String path) {
        if (path == null) {
            throw new IllegalArgumentException("路径不能为空.");
        }
        String cleanedPath = StringUtils.cleanPath(path);

        if (cleanedPath.contains("..")) {
            throw new IllegalArgumentException("无效路径：不允许使用目录遍历序列（“..”）。");
        }

        if (cleanedPath.startsWith("/")) {
            throw new IllegalArgumentException("无效路径：不允许使用绝对路径。");
        }

        return cleanedPath;
    }

} 