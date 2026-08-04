<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Import Batch #<c:out value="${batch.id}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h1 style="margin:0">Import Batch #<c:out value="${batch.id}"/> — <c:out value="${batch.feedType}"/></h1>
        <div style="display:flex;gap:8px;align-items:center">
            <span class="badge badge-${batch.status == 'APPLIED' ? 'active' : (batch.status == 'FAILED' ? 'inactive' : 'terminated')}"><c:out value="${batch.status}"/></span>
            <c:if test="${batch.status == 'STAGED'}">
                <form method="post" action="${pageContext.request.contextPath}/admin/reference-data-imports/${batch.id}/apply" style="display:inline">
                    <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn btn-primary" onclick="return confirm('Apply this import? This writes every row below to its live table.')">Apply</button>
                </form>
            </c:if>
        </div>
    </div>

    <table class="data-table" style="max-width:600px">
        <tr><td style="color:#6b7888;width:160px">File Name</td><td><c:out value="${batch.fileName}"/></td></tr>
        <tr><td style="color:#6b7888">Total Records</td><td><c:out value="${batch.totalRecords}"/></td></tr>
        <tr><td style="color:#6b7888">Added / Changed / Flagged</td><td><c:out value="${batch.addedCount}"/> / <c:out value="${batch.changedCount}"/> / <c:out value="${batch.flaggedCount}"/></td></tr>
        <tr><td style="color:#6b7888">Staged At</td><td><fmt:formatDate value="${batch.createdAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr>
        <c:if test="${not empty batch.appliedAt}">
            <tr><td style="color:#6b7888">Applied At</td><td><fmt:formatDate value="${batch.appliedAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr>
        </c:if>
        <c:if test="${not empty batch.errorMessage}">
            <tr><td style="color:#6b7888">Error</td><td><c:out value="${batch.errorMessage}"/></td></tr>
        </c:if>
    </table>

    <h2 class="section-heading">Diff (<c:out value="${rowCount}"/> rows)</h2>
    <table class="data-table">
        <thead>
            <tr><th>Type</th><th>Code</th><th>New Value</th><th>Prior Value</th><th>Applied</th></tr>
        </thead>
        <tbody>
        <c:forEach var="r" items="${rows}">
            <tr>
                <td><c:out value="${r.rowType}"/></td>
                <td><c:out value="${r.code}"/></td>
                <td><c:out value="${r.description}"/></td>
                <td><c:out value="${r.extra}"/></td>
                <td><c:out value="${r.applied}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty rows}">
            <tr><td colspan="5" style="color:#9aa6b4;text-align:center;padding:16px">No rows — every value in the file already matched current data.</td></tr>
        </c:if>
        </tbody>
    </table>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/admin/reference-data-imports">&larr; Back to Reference Data Imports</a></p>
</div>
</body>
</html>
