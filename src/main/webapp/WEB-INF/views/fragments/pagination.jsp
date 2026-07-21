<%-- Reusable pagination bar. Params: page (Page<?> object), baseUrl (String with ? or & query already appended) --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:if test="${page.totalPages > 1}">
<div class="pagination">
    <c:choose>
        <c:when test="${page.hasPrevious}">
            <a href="${baseUrl}page=${page.previousPage}">&laquo; Prev</a>
        </c:when>
        <c:otherwise><span class="disabled">&laquo; Prev</span></c:otherwise>
    </c:choose>

    <c:forEach begin="1" end="${page.totalPages}" var="p">
        <c:choose>
            <c:when test="${p == page.pageNumber}">
                <span class="current"><c:out value="${p}"/></span>
            </c:when>
            <c:otherwise>
                <a href="${baseUrl}page=${p}"><c:out value="${p}"/></a>
            </c:otherwise>
        </c:choose>
    </c:forEach>

    <c:choose>
        <c:when test="${page.hasNext}">
            <a href="${baseUrl}page=${page.nextPage}">Next &raquo;</a>
        </c:when>
        <c:otherwise><span class="disabled">Next &raquo;</span></c:otherwise>
    </c:choose>
    <span style="color:#9aa6b4;font-size:12px;margin-left:8px;">
        <c:out value="${page.totalItems}"/> total
    </span>
</div>
</c:if>
