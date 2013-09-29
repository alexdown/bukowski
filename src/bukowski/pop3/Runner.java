package bukowski.pop3;

import java.io.*;
import java.net.*;

/** analogo al Runner di smtp */
class Runner extends Thread
{
    private Socket client;
    private Protocol p;
    private File maildir;

    /** costruttore del thread
     * @param client
     * @param maildir  */    
    public Runner(Socket client, File maildir) {
        super();
        this.client = client;
        this.maildir = maildir;
        p = new Protocol(maildir);
    }

    /** Operazioni del thread  */   
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
            String inputLine = null;
            String outputLine = null;

            outputLine = p.processInput(inputLine);
            out.write(outputLine);
            out.newLine();
            out.flush();

            while ((inputLine = in.readLine()) != null) {
                outputLine = p.processInput(inputLine);
                System.err.println(inputLine);
                System.err.println(outputLine);
                out.write(outputLine);
                out.newLine();
                out.flush();
                if (p.shutdown())
                    break;
            }

            out.close();
            in.close();
            client.close();

            out = null;
            in = null;
            client = null;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
