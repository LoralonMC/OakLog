package dev.oakheart.oaklog.util;

public final class PaginationMath {

    public static final int ENTRIES_PER_PAGE = 36;
    public static final int FIRST_ENTRY_SLOT = 9;

    private PaginationMath() {}

    public static int totalPages(int totalEntries) {
        if (totalEntries <= 0) return 1;
        return (int) Math.ceil(totalEntries / (double) ENTRIES_PER_PAGE);
    }

    public static int clampPage(int page, int totalEntries) {
        int max = totalPages(totalEntries);
        if (page < 0) return 0;
        if (page >= max) return max - 1;
        return page;
    }
}
