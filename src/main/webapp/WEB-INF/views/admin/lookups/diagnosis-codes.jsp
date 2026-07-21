<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Diagnosis Codes — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <h1>Diagnosis Codes (ICD-10)</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h2 class="section-heading">Add / Update Code</h2>
    <form method="post" action="${pageContext.request.contextPath}/admin/lookups/diagnosis-codes/save"
          style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:24px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group" style="margin:0">
            <label>ICD-10 Code *</label>
            <input type="text" name="code" required maxlength="10" style="width:100px">
        </div>
        <div class="form-group" style="margin:0;flex:1;min-width:240px">
            <label>Description *</label>
            <input type="text" name="description" required maxlength="500">
        </div>
        <div class="form-group" style="margin:0">
            <label><input type="checkbox" name="active" value="true" checked> Active</label>
        </div>
        <button type="submit" class="btn btn-primary">Save</button>
    </form>

    <p style="color:#6b7888;font-size:13px">Showing first 500 codes. ICD-10 bulk import via Flyway migration; use this form to add individual codes or toggle active status.</p>

    <table class="data-table">
        <thead><tr><th>Code</th><th>Description</th><th>Active</th></tr></thead>
        <tbody>
        <c:forEach var="c" items="${codes}" end="499">
            <tr class="${!c.active ? 'row-inactive' : ''}">
                <td><c:out value="${c.code}"/></td>
                <td><c:out value="${c.description}"/></td>
                <td>${c.active ? 'Yes' : 'No'}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty codes}"><tr><td colspan="3" style="color:#9aa6b4;text-align:center;padding:24px">No diagnosis codes loaded yet.</td></tr></c:if>
        </tbody>
    </table>
</div>
</body>
</html>
