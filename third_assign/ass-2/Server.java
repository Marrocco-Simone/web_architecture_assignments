package it.unitn.marrocco;

import it.unitn.ronchet.Spreadsheet.Cell;
import it.unitn.ronchet.Spreadsheet.SSEngine;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@WebServlet(name = "server", value = "/server", asyncSupported=true)
public class Server extends HttpServlet {
    SSEngine engine = null;
    private HashMap<String, AsyncContext> readers;

    @Override
    public void init() {
        engine = SSEngine.getSSEngine();
        readers = new HashMap<>();
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
        String msg = "{\"success\": false, \"reason\": \"" + reason + "\"}";
        res.setStatus(400);
        sendMessage(res, msg, "text/application-json");
    }

    public void sendOkResponse(HttpServletResponse res) throws IOException {
        String msg = "{\"success\": true}";
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
        String msg = "data: " + modifiedCellsToString(modified_cells) + "\n\n";
        sendMessage(res, msg, "text/event-stream");
    }

    public String ACToString(AsyncContext ac) {
        return ac.toString().replaceAll("org.apache.catalina.core.AsyncContextImpl", "");
    }

    public void sendUpdateToAllReaders(Set<Cell> modified_cells) {
        Iterator<AsyncContext> iter = readers.values().iterator();
        while (iter.hasNext()) {
            AsyncContext reader = iter.next();
            try {
                HttpServletResponse res = (HttpServletResponse) reader.getResponse();
                sendUpdate(res, modified_cells);
                System.out.println("Send message to user: " + ACToString(reader));
            } catch (Exception e) {
                System.out.println("error printing to " + ACToString(reader));
                iter.remove();
            }
        }
    }

    public AsyncListener getAsyncListener(String id) {
        return new AsyncListener() {
            // implement all methods required by the AsyncListener interface
            @Override
            public void onStartAsync(AsyncEvent event) {}
            //in case of completion, error ot timeout remove client
            @Override
            public void onComplete(AsyncEvent event) {
                readers.remove(id);
            }
            @Override
            public void onError(AsyncEvent event) {
                readers.remove(id);
            }
            @Override
            public void onTimeout(AsyncEvent event) {
                readers.remove(id);
            }
        };
    }

    public void addReader(HttpServletRequest req, HttpServletResponse res) {
        // This a Tomcat specific - makes request asynchronous
        req.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);

        final String id = UUID.randomUUID().toString();
        final AsyncContext ac = req.startAsync(req, res);
        ac.addListener(getAsyncListener(id));

        readers.put(id, ac);
        System.out.println("Added new client: " + ACToString(ac));
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // System.out.println("\nNew user accessing");
        addReader(req, res);
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

        sendOkResponse(res);
        sendUpdateToAllReaders(modified_cells);
        System.out.println();
    }
}
