<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Enrollment Batches — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Enrollment Batches</h1>
    <p style="color:#6b7888;font-size:13px">
        X12 834 enrollment/maintenance files processed by either poller.
        Showing most recent <c:out value="${limit}"/> records.
    </p>

    <c:choose>
        <c:when test="${empty batches}">
            <p style="color:#6b7888">No enrollment files have been processed yet.</p>
        </c:when>
        <c:otherwise>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>#</th>
                        <th>File Name</th>
                        <th>Status</th>
                        <th>Total</th>
                        <th>Succeeded</th>
                        <th>Quarantined</th>
                        <th>Processed At</th>
                        <th>Notes</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="b" items="${batches}">
                        <tr>
                            <td><c:out value="${b.id}"/></td>
                            <td><c:out value="${b.fileName}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${b.status == 'COMPLETED' and b.quarantined == 0}">
                                        <span class="badge badge-active">COMPLETED</span>
                                    </c:when>
                                    <c:when test="${b.status == 'COMPLETED' and b.quarantined > 0}">
                                        <span class="badge badge-expired">PARTIAL</span>
                                    </c:when>
                                    <c:when test="${b.status == 'FAILED'}">
                                        <span class="badge badge-inactive">FAILED</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="badge badge-terminated"><c:out value="${b.status}"/></span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${b.totalRecords}"/></td>
                            <td><c:out value="${b.succeeded}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${b.quarantined > 0}">
                                        <span style="color:#c0392b;font-weight:600"><c:out value="${b.quarantined}"/></span>
                                    </c:when>
                                    <c:otherwise>0</c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <fmt:formatDate value="${b.createdAt}" pattern="yyyy-MM-dd HH:mm"/>
                            </td>
                            <td style="font-size:12px;color:#6b7888;max-width:320px;word-break:break-word">
                                <c:if test="${not empty b.errorMessage}">
                                    <c:out value="${b.errorMessage}"/>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>

    <div style="margin-top:16px">
        <a href="${pageContext.request.contextPath}/admin/operations" class="btn">Operations &amp; Job Log</a>
    </div>
</div>
</body>
</html>
