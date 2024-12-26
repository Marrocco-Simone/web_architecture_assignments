<!DOCTYPE html>
<%@ page import="it.unitn.ronchet.Spreadsheet.SSEngine" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>JSP Spreadsheet</title>
    <link rel="stylesheet" href="styles.css" />
    <script defer>var n_rows = <%=SSEngine.NROWS%>;</script>
    <script src="index.js" defer></script>
</head>
<body>
<form onsubmit="submitInput(event)">
    <label for="modify-cell" id="modify-cell-label">
        Select a cell to modify it
    </label>
    <br />
    <input id="modify-cell" disabled oninput="inputChanged()"/>
    <button
            id="modify-cell-submit"
            type="submit"
            disabled
    >
        Submit
    </button>
</form>
<table id="main-table">
<% for (int i = 1; i<= SSEngine.NROWS; i++){ %>
    <tr>
    <% for (int j = 0; j< SSEngine.NROWS; j++) {
        char a = 'A';
        a += j;
        String id = Character.toString(a) + i;
    %>
        <td id="<%=id%>" onclick="clickedCell('<%=id%>')"></td>
    <% } %>
    </tr>
<% } %>
</table>
</body>
</html>
