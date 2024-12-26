package it.unitn.marrocco;

import it.unitn.ronchet.Spreadsheet.Cell;
import it.unitn.ronchet.Spreadsheet.SSEngine;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@WebServlet(name = "server", value = "/server")
public class Server extends HttpServlet {
    SSEngine engine = null;
    @Override
    public void init() {
        engine = SSEngine.getSSEngine();
    }

    public void sendMessage(HttpServletResponse res, String msg, String contentType) throws IOException {
        res.setContentType(contentType);

        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Connection", "Keep-Alive");
        res.setHeader("Cache-Control", "no-cache");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(msg);
        res.getWriter().flush();
    }

    public void sendErrorResponse(HttpServletResponse res, String reason) throws IOException {
        String msg = "{\"reason\": \"" + reason + "\"}";
        res.setStatus(400);
        sendMessage(res, msg, "text/application-json");
    }

    public String modifiedCellsToString(Set<Cell> modified_cells) {
        Iterator<Cell> cells_iterator = modified_cells.iterator();
        int counter = 0;

        StringBuilder msg = new StringBuilder();
        msg.append("{\"changes\": [");
        while(cells_iterator.hasNext()) {
            Cell cell = cells_iterator.next();
            String cell_id = cell.getId();
            int value = cell.getValue();
            String formula = cell.getFormula();

            if (counter>0) msg.append(", ");
            msg.append("{\"cell\": \"");
            msg.append(cell_id);
            msg.append("\", \"value\": \"");
            msg.append(value);
            msg.append("\", \"formula\": \"");
            msg.append(formula);
            msg.append("\"}");

            counter++;
        }
        msg.append("]}");

        /* String msg_to_log = msg.toString()
                .replaceAll("\\{\"cell\"","\n\t{\"cell\"")
                .replaceAll("]}","\n]}");
        System.out.println(msg_to_log); */

        return msg.toString();
    }

    public void sendUpdate(HttpServletResponse res, Set<Cell> modified_cells) throws IOException {
        String msg = modifiedCellsToString(modified_cells);
        sendMessage(res, msg, "text/application-json");
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        System.out.println("\nNew user accessing");
        Set<Cell> modified_cells = engine.getUsedCells();
        sendUpdate(res, modified_cells);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        System.out.println("\nNew update: ");

        String json_string = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        Pattern p = Pattern.compile("\\{\"cell\":\"(\\S+)\",\"formula\":\"(\\S*)\"}");
        Matcher m = p.matcher(json_string);

        if (!m.find()) {
            System.out.println("Incorrect JSON format. Regex not correct");
            sendErrorResponse(res, "Incorrect JSON format");
            return;
        }

        String cell = m.group(1);
        String formula = m.group(2);

        if (cell.contains("\"") || formula.contains("\"")) {
            System.out.println("Incorrect JSON format. It contains other fields as well");
            sendErrorResponse(res, "Incorrect JSON format. add only a cell and a formula field");
            return;
        }

        System.out.println("cell: "+cell+", formula: "+formula);
        Set<Cell> modified_cells = engine.modifyCell(cell, formula);
        if (modified_cells == null) {
            System.out.println("Circular Dependencies");
            sendErrorResponse(res, "Circular Dependencies");
            return;
        }

        sendUpdate(res, modified_cells);
        System.out.println();
    }
}
