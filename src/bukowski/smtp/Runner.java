package bukowski.smtp;

import java.io.*;
import java.net.*;

class Runner extends Thread {
    private Socket client;
    private Protocol p;
    private File maildir;
    
    /**
     * @param client socket della connessione
     * @param maildir directory delle caselle */    
    public Runner(Socket client, File maildir) {
        //costruttore della classe padre
        super();
        //setta le variabili passate
        this.client = client;
        this.maildir = maildir;
        //istanzia una Protocol (per gestire i comandi smtp)
        p = new Protocol(client, maildir);
    }
    
    /** Operazioni del thread  */    
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
            String inputLine = null;
            //benvenuti! ;o)
            String outputLine = "220 Welcome to java bukowski smtp server";
            out.write(outputLine);
            out.newLine();
            out.flush();
            
            while (true) {
                //legge la riga e la processa
                outputLine = p.processInput(in,out);
                //stampa la riga (per debug)
                System.err.println(outputLine);
                //risponde
                out.write(outputLine);
                out.write("\r\n");
                out.flush();
                //controlla che lo stato del server non sia: "in chiusura"
                if (p.shutdown())
                    break;
            }
            
            //chiude gli stream...
            out.close();
            in.close();
            //...e il socket
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
