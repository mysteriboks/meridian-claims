<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<nav class="main-nav">
    <span class="nav-brand">Meridian Claims</span>
    <ul>
        <li><a href="${pageContext.request.contextPath}/dashboard">Dashboard</a></li>
        <li><a href="${pageContext.request.contextPath}/members">Members</a></li>
        <li><a href="${pageContext.request.contextPath}/providers">Providers</a></li>
        <li><a href="${pageContext.request.contextPath}/prior-auth">Prior Auth</a></li>
        <li><a href="${pageContext.request.contextPath}/referrals">Referrals</a></li>
        <li><a href="${pageContext.request.contextPath}/claims">Claims</a></li>
        <c:if test="${sessionScope.MERIDIAN_USER.role == 'ADMIN'}">
            <li class="nav-dropdown-label">Admin &#9662;
                <ul class="nav-dropdown">
                    <li><a href="${pageContext.request.contextPath}/admin/users">Users</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/plans">Plans</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/lookups/procedure-codes">Procedure Codes</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/lookups/diagnosis-codes">Diagnosis Codes</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/lookups/denial-reasons">Denial Reasons</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/lookups/service-types">Service Types</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/intake-batches">Intake Batches</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/enrollment-batches">Enrollment Batches</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/reference-data-imports">Reference Data Imports</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/integrations">Integrations</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/trading-partners">Trading Partners</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/operations">Operations</a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/audit">Audit Log</a></li>
                </ul>
            </li>
        </c:if>
    </ul>
    <div class="nav-user">
        <span><c:out value="${sessionScope.MERIDIAN_USER.fullName}"/> (<c:out value="${sessionScope.MERIDIAN_USER.role}"/>)</span>
        <form method="post" action="${pageContext.request.contextPath}/logout" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <button type="submit" class="btn-link">Sign Out</button>
        </form>
    </div>
</nav>
