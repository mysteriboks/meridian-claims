<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Service Types — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
        <h1 style="margin:0">Service Type Categories</h1>
        <form method="post" action="${pageContext.request.contextPath}/admin/lookups/refresh" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <input type="hidden" name="returnTo" value="/admin/lookups/service-types">
            <button type="submit" class="btn">&#8635; Refresh lookup caches</button>
        </form>
    </div>
    <p style="color:#6b7888;font-size:13px;margin-top:0">Lookups are cached in memory at startup. Use refresh after a direct database edit.</p>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h2 class="section-heading">Add / Update Category</h2>
    <form method="post" action="${pageContext.request.contextPath}/admin/lookups/service-types/save"
          style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:24px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group" style="margin:0">
            <label>Code *</label>
            <input type="text" name="code" required maxlength="50" placeholder="e.g. OFFICE_VISIT" style="width:160px">
        </div>
        <div class="form-group" style="margin:0;flex:1;min-width:220px">
            <label>Description *</label>
            <input type="text" name="description" required maxlength="200">
        </div>
        <div class="form-group" style="margin:0">
            <label><input type="checkbox" name="active" value="true" checked> Active</label>
        </div>
        <button type="submit" class="btn btn-primary">Save</button>
    </form>

    <table class="data-table">
        <thead><tr><th>Code</th><th>Description</th><th>Active</th></tr></thead>
        <tbody>
        <c:forEach var="c" items="${categories}">
            <tr class="${!c.active ? 'row-inactive' : ''}">
                <td><c:out value="${c.code}"/></td>
                <td><c:out value="${c.description}"/></td>
                <td>${c.active ? 'Yes' : 'No'}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty categories}"><tr><td colspan="3" style="color:#9aa6b4;text-align:center;padding:16px">No categories.</td></tr></c:if>
        </tbody>
    </table>
</div>
</body>
</html>
