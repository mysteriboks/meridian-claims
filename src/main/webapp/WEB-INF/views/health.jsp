<%@ page contentType="text/html;charset=UTF-8" language="java" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <title>Meridian Claims — Health</title>
    <meta charset="UTF-8"/>
    <style>
        body { font-family: Arial, sans-serif; margin: 2em; color: #222; }
        h1 { font-size: 1.3em; }
        table { border-collapse: collapse; margin-top: 1em; }
        th, td { border: 1px solid #ccc; padding: 6px 12px; text-align: left; }
        .up { color: #1a7f37; font-weight: bold; }
        .down { color: #b3261e; font-weight: bold; }
    </style>
</head>
<body>
    <h1>Meridian Claims &mdash; System Health</h1>

    <p>Status:
        <c:choose>
            <c:when test="${health.databaseUp}">
                <span class="up"><c:out value="${health.status}"/></span>
            </c:when>
            <c:otherwise>
                <span class="down"><c:out value="${health.status}"/></span>
            </c:otherwise>
        </c:choose>
    </p>

    <table>
        <tr><th>Database</th><td><c:out value="${health.databaseMessage}"/></td></tr>
        <tr><th>Pool — active connections</th><td><c:out value="${health.poolActive}"/></td></tr>
        <tr><th>Pool — idle connections</th><td><c:out value="${health.poolIdle}"/></td></tr>
        <tr><th>Pool — max total</th><td><c:out value="${health.poolMaxTotal}"/></td></tr>
    </table>
</body>
</html>
