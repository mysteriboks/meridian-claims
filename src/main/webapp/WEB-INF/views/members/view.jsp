<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title><c:out value="${member.fullName}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">

    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px"><c:out value="${member.fullName}"/></h1>
            <span style="color:#6b7888;font-size:13px">Member # <c:out value="${member.memberNumber}"/></span>
            &nbsp;<span class="badge badge-${member.status == 'ACTIVE' ? 'active' : 'inactive'}"><c:out value="${member.status}"/></span>
        </div>
        <div style="display:flex;gap:8px">
            <a class="btn" href="${pageContext.request.contextPath}/members/${member.id}/edit">Edit</a>
            <a class="btn" href="${pageContext.request.contextPath}/prior-auth/new?memberId=${member.id}">+ Prior Auth</a>
            <a class="btn" href="${pageContext.request.contextPath}/referrals/new?memberId=${member.id}">+ Referral</a>
            <c:if test="${member.status == 'ACTIVE'}">
                <form method="post" action="${pageContext.request.contextPath}/members/${member.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn btn-danger" onclick="return confirm('Deactivate this member?')">Deactivate</button>
                </form>
            </c:if>
        </div>
    </div>

    <h2 class="section-heading">Personal Information</h2>
    <table class="data-table" style="max-width:600px">
        <tr><td style="color:#6b7888;width:160px">Date of Birth</td><td><fmt:formatDate value="${member.dob}" pattern="MMM d, yyyy"/></td></tr>
        <tr><td style="color:#6b7888">Address</td><td><c:out value="${member.address}"/></td></tr>
        <tr><td style="color:#6b7888">Phone</td><td><c:out value="${member.phone}"/></td></tr>
        <tr><td style="color:#6b7888">Email</td><td><c:out value="${member.email}"/></td></tr>
    </table>

    <h2 class="section-heading">Coverage Records</h2>
    <table class="data-table">
        <thead>
            <tr><th>Plan</th><th>Order</th><th>Effective</th><th>Terminates</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="cov" items="${coverageRecords}">
            <tr>
                <td><c:out value="${cov.planName}"/></td>
                <td><c:out value="${cov.coverageOrder}"/></td>
                <td><fmt:formatDate value="${cov.effectiveDate}" pattern="yyyy-MM-dd"/></td>
                <td>
                    <c:choose>
                        <c:when test="${not empty cov.terminationDate}"><fmt:formatDate value="${cov.terminationDate}" pattern="yyyy-MM-dd"/></c:when>
                        <c:otherwise><span style="color:#9aa6b4">—</span></c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <form method="post" action="${pageContext.request.contextPath}/members/${member.id}/coverage/${cov.id}/remove" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                        <button type="submit" class="btn-link btn-danger" onclick="return confirm('Remove this coverage record?')">Remove</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty coverageRecords}">
            <tr><td colspan="5" style="color:#9aa6b4;text-align:center;padding:16px">No coverage records.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2 class="section-heading">Add Coverage</h2>
    <form method="post" action="${pageContext.request.contextPath}/members/${member.id}/coverage/add" style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group" style="margin:0">
            <label>Plan</label>
            <select name="planId" style="min-width:180px">
                <c:forEach var="p" items="${plans}">
                    <option value="${p.id}"><c:out value="${p.planName}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group" style="margin:0">
            <label>Order</label>
            <select name="coverageOrder">
                <c:forEach var="o" items="${coverageOrders}">
                    <option value="${o}"><c:out value="${o}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group" style="margin:0">
            <label>Effective Date</label>
            <input type="date" name="effectiveDate" required style="width:150px">
        </div>
        <div class="form-group" style="margin:0">
            <label>Termination Date</label>
            <input type="date" name="terminationDate" style="width:150px">
        </div>
        <button type="submit" class="btn btn-primary">Add Coverage</button>
    </form>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/members">&larr; Back to Members</a></p>
</div>
</body>
</html>
