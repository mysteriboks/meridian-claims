<%-- Shared CSV export + Print buttons for all report pages.
     Expects: reportCsvUrl (String with ?export=csv appended) --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<div style="display:flex;gap:8px;margin-bottom:16px;align-items:center">
    <a href="${reportCsvUrl}" class="btn">&#x2193; Export CSV</a>
    <button onclick="window.print()" class="btn">&#x1F5A8; Print</button>
</div>
