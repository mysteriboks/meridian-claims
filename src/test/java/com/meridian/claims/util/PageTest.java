package com.meridian.claims.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageTest {

    private List<String> items(int n) {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            list.add("item" + i);
        }
        return list;
    }

    @Test
    public void firstPageHasNoPrevious() {
        Page<String> page = new Page<String>(items(10), 1, 10, 50);
        assertFalse(page.hasPrevious());
        assertTrue(page.hasNext());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    public void lastPageHasNoNext() {
        Page<String> page = new Page<String>(items(10), 5, 10, 50);
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        assertTrue(page.isLast());
    }

    @Test
    public void totalPagesCalculatedCorrectly() {
        assertEquals(5, new Page<String>(items(10), 1, 10, 50).getTotalPages());
        assertEquals(6, new Page<String>(items(10), 1, 10, 51).getTotalPages());
        assertEquals(1, new Page<String>(items(5), 1, 10, 5).getTotalPages());
    }

    @Test
    public void offsetCalculatedFromPageNumber() {
        assertEquals(0,  new Page<String>(items(10), 1, 10, 50).getOffset());
        assertEquals(20, new Page<String>(items(10), 3, 10, 50).getOffset());
    }

    @Test
    public void singlePageHasNoNavigation() {
        Page<String> page = new Page<String>(items(3), 1, 10, 3);
        assertEquals(1, page.getTotalPages());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }
}
