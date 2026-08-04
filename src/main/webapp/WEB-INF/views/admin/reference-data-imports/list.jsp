<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Reference Data Imports — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h1>Reference Data Imports</h1>
    <p style="color:#6b7888;font-size:13px">
        Bulk code-set/registry imports (ICD-10, CPT/HCPCS, X12 CARC/RARC, NPPES NPI validation).
        A dropped file is staged (parsed + diffed) automatically, but nothing is written to a
        live table until you review and click <strong>Apply</strong> on a batch.
        Showing most recent <c:out value="${limit}"/> records.
    </p>

    <c:choose>
        <c:when test="${empty batches}">
            <p style="color:#6b7888">No reference data imports have been staged yet.</p>
        </c:when>
        <c:otherwise>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Feed Type</th>
                        <th>File Name</th>
                        <th>Status</th>
                        <th>Total</th>
                        <th>Added</th>
                        <th>Changed</th>
                        <th>Flagged</th>
                        <th>Staged At</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="b" items="${batches}">
                        <tr>
                            <td><a href="${pageContext.request.contextPath}/admin/reference-data-imports/${b.id}"><c:out value="${b.id}"/></a></td>
                            <td><c:out value="${b.feedType}"/></td>
                            <td><c:out value="${b.fileName}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${b.status == 'APPLIED'}">
                                        <span class="badge badge-active">APPLIED</span>
                                    </c:when>
                                    <c:when test="${b.status == 'FAILED'}">
                                        <span class="badge badge-inactive">FAILED</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="badge badge-terminated">STAGED</span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${b.totalRecords}"/></td>
                            <td><c:out value="${b.addedCount}"/></td>
                            <td><c:out value="${b.changedCount}"/></td>
                            <td><c:out value="${b.flaggedCount}"/></td>
                            <td><fmt:formatDate value="${b.createdAt}" pattern="yyyy-MM-dd HH:mm"/></td>
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
