<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty provider ? 'New Provider' : 'Edit Provider'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content" style="max-width:680px">
    <h1>${empty provider ? 'New Provider' : 'Edit Provider'}</h1>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty provider}">
            <form method="post" action="${pageContext.request.contextPath}/providers/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/providers/${provider.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="npi">NPI *</label>
            <input type="text" id="npi" name="npi" required maxlength="10"
                   value="<c:out value='${provider.npi}'/>">
        </div>
        <div class="form-group">
            <label for="providerType">Provider Type *</label>
            <select id="providerType" name="providerType">
                <c:forEach var="t" items="${providerTypes}">
                    <option value="${t}" ${provider.providerType == t ? 'selected' : ''}><c:out value="${t}"/></option>
                </c:forEach>
            </select>
        </div>
    </div>

    <div class="form-group">
        <label for="name">Provider Name *</label>
        <input type="text" id="name" name="name" required maxlength="200"
               value="<c:out value='${provider.name}'/>">
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="specialty">Specialty</label>
            <input type="text" id="specialty" name="specialty" maxlength="100"
                   value="<c:out value='${provider.specialty}'/>">
        </div>
        <div class="form-group">
            <label for="networkStatus">Network Status *</label>
            <select id="networkStatus" name="networkStatus">
                <c:forEach var="ns" items="${networkStatuses}">
                    <option value="${ns}" ${provider.networkStatus == ns ? 'selected' : ''}><c:out value="${ns}"/></option>
                </c:forEach>
            </select>
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="phone">Phone</label>
            <input type="text" id="phone" name="phone" maxlength="20"
                   value="<c:out value='${provider.phone}'/>">
        </div>
        <div class="form-group">
            <label for="address">Address</label>
            <input type="text" id="address" name="address" maxlength="255"
                   value="<c:out value='${provider.address}'/>">
        </div>
    </div>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty provider ? 'Create Provider' : 'Save Changes'}</button>
        <c:choose>
            <c:when test="${not empty provider}">
                <a href="${pageContext.request.contextPath}/providers/${provider.id}" class="btn">Cancel</a>
            </c:when>
            <c:otherwise>
                <a href="${pageContext.request.contextPath}/providers" class="btn">Cancel</a>
            </c:otherwise>
        </c:choose>
    </div>
    </form>
</div>
</body>
</html>
