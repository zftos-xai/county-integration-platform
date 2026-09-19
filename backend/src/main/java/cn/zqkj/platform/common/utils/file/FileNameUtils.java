package cn.zqkj.platform.common.utils.file;

import java.util.Locale;

/**
 * 实现不访问文件系统的通用文件名解析。
 *
 * <p>本类参考RuoYi文件类型工具，是{@code Func}门面的分类实现，业务代码不得绕过门面直接调用。</p>
 */
public final class FileNameUtils {

    /**
     * 禁止创建文件名工具实现实例。
     */
    private FileNameUtils() {
    }

    /**
     * 从Unix或Windows风格路径中取得文件名。
     *
     * <p>示例：{@code name("/tmp/report.pdf")}返回{@code "report.pdf"}。</p>
     *
     * @param value 可选路径或文件名
     * @return 最后一个路径片段；输入为空时返回空
     */
    public static String name(String value) {
        if (value == null) {
            return null;
        }
        int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return value.substring(separator + 1);
    }

    /**
     * 取得文件名的小写扩展名。
     *
     * <p>示例：{@code extension("report.PDF")}返回{@code "pdf"}。</p>
     *
     * @param value 可选路径或文件名
     * @return 不含点号的小写扩展名；没有扩展名时返回空字符串
     */
    public static String extension(String value) {
        String fileName = name(value);
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int separator = fileName.lastIndexOf('.');
        if (separator <= 0 || separator == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 取得不含最后一个扩展名的文件名。
     *
     * <p>示例：{@code baseName("report.final.pdf")}返回{@code "report.final"}。</p>
     *
     * @param value 可选路径或文件名
     * @return 不含路径和最后一个扩展名的文件名；输入为空时返回空
     */
    public static String baseName(String value) {
        String fileName = name(value);
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }
        int separator = fileName.lastIndexOf('.');
        return separator <= 0 ? fileName : fileName.substring(0, separator);
    }
}
