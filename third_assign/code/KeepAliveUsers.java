private class KeepAliveUsers implements Runnable {
    public void sendKeepAlive(HttpServletResponse res) throws IOException {
        String msg = "{\"success\": true}";

        res.setContentType("text/event-stream");
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Connection", "Keep-Alive");
        res.setHeader("Cache-Control", "no-cache");
        res.setCharacterEncoding("UTF-8");
        // res.getWriter().write("event: keep-alive");
        res.getWriter().write("data: " + msg + "\n\n");
        res.getWriter().flush();
    }

    @Override
    public void run(){
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("sending keep-alives...");
                Iterator<AsyncContext> iter = readers.values().iterator();
                while (iter.hasNext()) {
                    AsyncContext reader = iter.next();
                    try {
                        HttpServletResponse res = (HttpServletResponse) reader.getResponse();
                        sendKeepAlive(res);
                        System.out.println("Send keep-alive to user: " + ACToString(reader));
                    } catch (Exception e) {
                        System.out.println("error printing to " + ACToString(reader));
                        iter.remove();
                    }
                }
            }
        };
        Timer timer = new Timer();
        timer.schedule(task, 0l, 1*1000l);
    }
}