<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Member Export — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Member Data Export</h1>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <p style="color:#6b7888">Enter a Member ID to download all claims and payments as CSV.</p>
    <div style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap">
        <div>
            <label style="font-size:12px;color:#6b7888">Member ID</label><br>
            <input type="number" id="memberId" style="width:160px" placeholder="e.g. 42">
        </div>
        <a id="claimsLink" href="#" class="btn" onclick="updateLinks(); return false;">Download Claims CSV</a>
        <a id="paymentsLink" href="#" class="btn">Download Payments CSV</a>
    </div>

    <script>
        function updateLinks() {
            var mid = document.getElementById('memberId').value;
            if (!mid) { alert('Enter a member ID first'); return; }
            var base = '${pageContext.request.contextPath}/finance/member-export';
            document.getElementById('claimsLink').href = base + '/claims?memberId=' + mid;
            document.getElementById('paymentsLink').href = base + '/payments?memberId=' + mid;
            document.getElementById('claimsLink').onclick = null;
            document.getElementById('paymentsLink').onclick = null;
        }
        document.getElementById('memberId').addEventListener('input', function() {
            var mid = this.value;
            var base = '${pageContext.request.contextPath}/finance/member-export';
            document.getElementById('claimsLink').href = base + '/claims?memberId=' + mid;
            document.getElementById('paymentsLink').href = base + '/payments?memberId=' + mid;
            document.getElementById('claimsLink').onclick = null;
            document.getElementById('paymentsLink').onclick = null;
        });
    </script>
</div>
</body>
</html>
