package utils;

/**
 * 分页参数钳制工具。
 *
 * 分页参数直接来自客户端，如果不设上限，一次请求就可能把整张表读进内存
 * （例如 size=100000），既是性能问题也是拒绝服务的入口。
 */
public final class PaginationUtil {

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 单页最大条数 */
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtil() {
    }

    /** 页码最小为 1 */
    public static int page(int page) {
        return page < 1 ? 1 : page;
    }

    /** 每页条数：非法值回落到默认值，超出上限则截断 */
    public static int size(int size) {
        return size(size, DEFAULT_PAGE_SIZE);
    }

    /** 每页条数：非法值回落到指定默认值，超出上限则截断 */
    public static int size(int size, int defaultSize) {
        if (size <= 0) {
            return defaultSize;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
