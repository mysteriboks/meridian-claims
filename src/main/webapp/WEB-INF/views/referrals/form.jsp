<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty referral ? 'New Referral' : 'Edit Referral'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content" style="max-width:680px">
    <h1>${empty referral ? 'New Referral' : 'Edit Referral'}</h1>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty referral}">
            <form method="post" action="${pageContext.request.contextPath}/referrals/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/referrals/${referral.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <c:if test="${empty referral}">
        <div class="form-group">
            <label for="memberId">Member *</label>
            <select id="memberId" name="memberId" required>
                <option value="">— Select member —</option>
                <c:forEach var="m" items="${members}">
                    <option value="${m.id}" ${preselectedMemberId == m.id ? 'selected' : ''}><c:out value="${m.fullName}"/> (<c:out value="${m.memberNumber}"/>)</option>
                </c:forEach>
            </select>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
            <div class="form-group">
                <label for="referringProviderId">Referring Provider (PCP) *</label>
                <select id="referringProviderId" name="referringProviderId" required>
                    <option value="">— Select —</option>
                    <c:forEach var="p" items="${providers}">
                        <option value="${p.id}"><c:out value="${p.name}"/></option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group">
                <label for="referredToProviderId">Referred-To Provider *</label>
                <select id="referredToProviderId" name="referredToProviderId" required>
                    <option value="">— Select —</option>
                    <c:forEach var="p" items="${providers}">
                        <option value="${p.id}"><c:out value="${p.name}"/></option>
                    </c:forEach>
                </select>
            </div>
        </div>
    </c:if>

    <div class="form-group">
        <label for="serviceType">Service Type *</label>
        <select id="serviceType" name="serviceType" required>
            <option value="">— Select —</option>
            <c:forEach var="st" items="${serviceTypes}">
                <option value="${st.code}" ${referral.serviceType == st.code ? 'selected' : ''}><c:out value="${st.description}"/></option>
            </c:forEach>
        </select>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="validFrom">Valid From *</label>
            <input type="date" id="validFrom" name="validFrom" required
                   value="<c:if test='${not empty referral.validFrom}'><fmt:formatDate value='${referral.validFrom}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
        <div class="form-group">
            <label for="validTo">Valid To *</label>
            <input type="date" id="validTo" name="validTo" required
                   value="<c:if test='${not empty referral.validTo}'><fmt:formatDate value='${referral.validTo}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
    </div>

    <c:if test="${not empty referral}">
        <div class="form-group">
            <label for="status">Status</label>
            <select id="status" name="status">
                <c:forEach var="s" items="${statuses}">
                    <option value="${s}" ${referral.status == s ? 'selected' : ''}><c:out value="${s}"/></option>
                </c:forEach>
            </select>
        </div>
    </c:if>

    <div class="form-group">
        <label for="notes">Notes</label>
        <input type="text" id="notes" name="notes" maxlength="500" value="<c:out value='${referral.notes}'/>">
    </div>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty referral ? 'Create Referral' : 'Save Changes'}</button>
        <a href="${pageContext.request.contextPath}/referrals" class="btn">Cancel</a>
    </div>
    </form>
</div>
</body>
</html>
