<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty auth ? 'New Prior Auth' : 'Edit Prior Auth'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content" style="max-width:680px">
    <h1>${empty auth ? 'New Prior Authorization' : 'Edit Prior Authorization'}</h1>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty auth}">
            <form method="post" action="${pageContext.request.contextPath}/prior-auth/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/prior-auth/${auth.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <c:if test="${empty auth}">
        <div class="form-group">
            <label for="memberId">Member *</label>
            <select id="memberId" name="memberId" required>
                <option value="">— Select member —</option>
                <c:forEach var="m" items="${members}">
                    <option value="${m.id}" ${preselectedMemberId == m.id ? 'selected' : ''}><c:out value="${m.fullName}"/> (<c:out value="${m.memberNumber}"/>)</option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group">
            <label for="providerId">Authorizing Provider *</label>
            <select id="providerId" name="providerId" required>
                <option value="">— Select provider —</option>
                <c:forEach var="p" items="${providers}">
                    <option value="${p.id}"><c:out value="${p.name}"/> (<c:out value="${p.npi}"/>)</option>
                </c:forEach>
            </select>
        </div>
    </c:if>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="procedureCode">Procedure Code *</label>
            <input type="text" id="procedureCode" name="procedureCode" required maxlength="10"
                   value="<c:out value='${auth.procedureCode}'/>">
        </div>
        <div class="form-group">
            <label for="serviceType">Service Type</label>
            <select id="serviceType" name="serviceType">
                <option value="">— None —</option>
                <c:forEach var="st" items="${serviceTypes}">
                    <option value="${st.code}" ${auth.serviceType == st.code ? 'selected' : ''}><c:out value="${st.description}"/></option>
                </c:forEach>
            </select>
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="authorizedFrom">Authorized From *</label>
            <input type="date" id="authorizedFrom" name="authorizedFrom" required
                   value="<c:if test='${not empty auth.authorizedFrom}'><fmt:formatDate value='${auth.authorizedFrom}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
        <div class="form-group">
            <label for="authorizedTo">Authorized To *</label>
            <input type="date" id="authorizedTo" name="authorizedTo" required
                   value="<c:if test='${not empty auth.authorizedTo}'><fmt:formatDate value='${auth.authorizedTo}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="approvedUnits">Approved Units</label>
            <input type="number" id="approvedUnits" name="approvedUnits" min="1" value="${not empty auth ? auth.approvedUnits : 1}">
        </div>
        <c:if test="${not empty auth}">
            <div class="form-group">
                <label for="status">Status</label>
                <select id="status" name="status">
                    <c:forEach var="s" items="${statuses}">
                        <option value="${s}" ${auth.status == s ? 'selected' : ''}><c:out value="${s}"/></option>
                    </c:forEach>
                </select>
            </div>
        </c:if>
    </div>

    <div class="form-group">
        <label for="notes">Notes</label>
        <input type="text" id="notes" name="notes" maxlength="500" value="<c:out value='${auth.notes}'/>">
    </div>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty auth ? 'Create Authorization' : 'Save Changes'}</button>
        <a href="${pageContext.request.contextPath}/prior-auth" class="btn">Cancel</a>
    </div>
    </form>
</div>
</body>
</html>
