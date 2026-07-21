package com.meridian.claims.util;

import java.util.List;

public class Page<T> {

    private final List<T> items;
    private final int pageNumber;
    private final int pageSize;
    private final int totalItems;

    public Page(List<T> items, int pageNumber, int pageSize, int totalItems) {
        this.items = items;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    public List<T> getItems() { return items; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }
    public int getTotalItems() { return totalItems; }

    public int getTotalPages() {
        if (pageSize <= 0) return 0;
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    public boolean isFirst() { return pageNumber <= 1; }
    public boolean isLast() { return pageNumber >= getTotalPages(); }
    public boolean hasPrevious() { return pageNumber > 1; }
    public boolean hasNext() { return pageNumber < getTotalPages(); }
    public int getPreviousPage() { return pageNumber - 1; }
    public int getNextPage() { return pageNumber + 1; }

    public int getOffset() { return (pageNumber - 1) * pageSize; }
}
