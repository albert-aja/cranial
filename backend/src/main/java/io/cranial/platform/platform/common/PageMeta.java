package io.cranial.platform.platform.common;

public record PageMeta(
        long page,
        long size,
        long totalElements,
        long totalPages
) {
    public static PageMeta of(long page, long size, long totalElements) {
        long safeSize = Math.max(1L, size);
        long pages = (totalElements + safeSize - 1L) / safeSize;
        return new PageMeta(page, safeSize, totalElements, pages);
    }
}

