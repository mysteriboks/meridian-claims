<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Claims — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
    <style>
        .row-sla-breached { background:#fff3cd; }
    </style>
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
        <h1 style="margin:0">Claims</h1>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/claims/new">+ New Claim</a>
    </div>

    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <form method="get" action="${pageContext.request.contextPath}/claims" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
        <input type="text" name="q" value="<c:out value='${query}'/>" placeholder="Search claim #, member name…" style="flex:1;min-width:180px">
        <select name="status" style="width:150px">
            <option value="">All Statuses</option>
            <c:forEach var="s" items="${statuses}">
                <option value="${s}" ${s.name() == statusFilter ? 'selected' : ''}><c:out value="${s}"/></option>
            </c:forEach>
        </select>
        <c:if test="${currentUser.role == 'ADMIN' or currentUser.role == 'REVIEWER'}">
            <select name="assignee" style="width:160px">
                <option value="">Any Assignee</option>
                <c:if test="${currentUser != null}">
                    <option value="${currentUser.id}" ${assigneeId == currentUser.id ? 'selected' : ''}>Mine</option>
                </c:if>
            </select>
            <label style="display:flex;align-items:center;gap:4px;font-size:13px">
                <input type="checkbox" name="unassigned" value="true" ${unassignedOnly ? 'checked' : ''}> Unassigned
            </label>
            <label style="display:flex;align-items:center;gap:4px;font-size:13px">
                <input type="checkbox" name="slaBreached" value="true" ${slaBreachedOnly ? 'checked' : ''}> SLA Breached
            </label>
        </c:if>
        <button type="submit" class="btn">Search</button>
        <c:if test="${not empty query or not empty statusFilter or unassignedOnly or slaBreachedOnly}">
            <a href="${pageContext.request.contextPath}/claims" class="btn">Clear</a>
        </c:if>
    </form>

    <table class="data-table">
        <thead>
            <tr>
                <th>Claim #</th><th>Type</th><th>Member</th><th>Provider</th>
                <th>DOS</th><th>Status</th><th>Assigned To</th><th>Actions</th>
            </tr>
        </thead>
        <tbody>
        <c:forEach var="c" items="${page.items}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/claims/${c.id}"><c:out value="${c.claimNumber}"/></a></td>
                <td><c:out value="${c.claimType}"/></td>
                <td><a href="${pageContext.request.contextPath}/members/<c:out value='${c.memberId}'/>"><c:out value="${c.memberId}"/></a></td>
                <td><c:out value="${c.providerId}"/></td>
                <td><c:out value="${c.dateOfService}"/></td>
                <td>
                    <span class="badge badge-${c.status == 'APPROVED' or c.status == 'PAID' ? 'active' : c.status == 'DENIED' or c.status == 'VOIDED' ? 'inactive' : 'pending'}">
                        <c:out value="${c.status}"/>
                    </span>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty c.assignedToUserId}"><c:out value="${c.assignedToUserId}"/></c:when>
                        <c:otherwise><span style="color:#9aa6b4">—</span></c:otherwise>
                    </c:choose>
                </td>
                <td><a href="${pageContext.request.contextPath}/claims/${c.id}" class="btn-link">View</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="8" style="color:#9aa6b4;text-align:center;padding:24px">No claims found.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/claims?q=${query}&status=${statusFilter}&"/>
    <jsp:include page="../fragments/pagination.jsp"/>
</div>
</body>
</html>
