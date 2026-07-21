package com.meridian.claims.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralised, externally-configurable page sizes for list and report screens.
 *
 * Replaces the page-size literals that were previously duplicated across the
 * controllers. Overridable via application.properties so the row count per page
 * can be tuned without a recompile. Defaults match the original literals
 * (20 for list/worklist screens, 50 for reports and bulk operations).
 */
@Component
public class PaginationConfig {

    @Value("${claims.ui.page-size.list:20}")
    private int listPageSize;

    @Value("${claims.ui.page-size.report:50}")
    private int reportPageSize;

    /** Rows per page on standard list and worklist screens. */
    public int getListPageSize() {
        return listPageSize;
    }

    /** Rows per page on report screens and bulk operations. */
    public int getReportPageSize() {
        return reportPageSize;
    }
}
