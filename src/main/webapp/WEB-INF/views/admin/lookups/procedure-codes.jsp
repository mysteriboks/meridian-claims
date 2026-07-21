<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Procedure Codes — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <h1>Procedure Codes (CPT)</h1>
    <div class="alert alert-info">
        CPT codes are AMA-licensed. Enter only the codes your organisation is licensed to use.
        Do not enter codes copied from unlicensed sources.
    </div>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h2 class="section-heading">Add / Update Code</h2>
    <form method="post" action="${pageContext.request.contextPath}/admin/lookups/procedure-codes/save"
          style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:24px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group" style="margin:0">
            <label>CPT Code *</label>
            <input type="text" name="code" required maxlength="10" style="width:100px">
        </div>
        <div class="form-group" style="margin:0;flex:1;min-width:200px">
            <label>Description *</label>
            <input type="text" name="description" required maxlength="500">
        </div>
        <div class="form-group" style="margin:0">
            <label>Service Type</label>
            <select name="serviceType">
                <option value="">— None —</option>
                <c:forEach var="st" items="${serviceTypes}">
                    <option value="${st.code}"><c:out value="${st.description}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group" style="margin:0">
            <label><input type="checkbox" name="active" value="true" checked> Active</label>
        </div>
        <button type="submit" class="btn btn-primary">Save</button>
    </form>

    <table class="data-table">
        <thead><tr><th>Code</th><th>Description</th><th>Service Type</th><th>Active</th></tr></thead>
        <tbody>
        <c:forEach var="c" items="${codes}">
            <tr class="${!c.active ? 'row-inactive' : ''}">
                <td><c:out value="${c.code}"/></td>
                <td><c:out value="${c.description}"/></td>
                <td><c:out value="${c.serviceType}"/></td>
                <td>${c.active ? 'Yes' : 'No'}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty codes}"><tr><td colspan="4" style="color:#9aa6b4;text-align:center;padding:24px">No procedure codes entered yet.</td></tr></c:if>
        </tbody>
    </table>
</div>
</body>
</html>
