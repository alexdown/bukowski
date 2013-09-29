package bukowski.smtp;

import java.io.*;
import java.net.*;
import java.util.*;

// Main.java

public class SMTPMain {
    
    private static int PORT = 25;
    private static ServerSocket server = null;
    public static File maildir = null;
    public static Mailbox mailbox;
    
    /**
     * @param argv  */    
    public static void main(String[] argv) {
        
        // serve a leggere il file di configurazione
        Properties p = new Properties();
        try {
            InputStream in = null;
            
            //lo cerca nella home dell'utente...
            File settings = new File(System.getProperty("user.home"), "smtp.properties");
            
            //..altrimenti lo cerca nella directory del package
            if (!settings.exists())
                in = SMTPMain.class.getResourceAsStream("/smtp.properties");
            else
                in = new FileInputStream(settings);
            
            //carica le configurazioni
            p.load(in);
            maildir = new File(p.getProperty("mail.directory"));
            
            //se non esiste la directory dove salvare i messaggi, da' errore
            if (!maildir.exists()) {
                in.close();
                in = null;
                p = null;
                System.err.println("Unable to find mail directory.");
                System.exit(1);
            }
            
            in.close();
        }
        catch (Exception e) {
            System.err.println("Unable to find mail directory.");
            //chiude la virtual machine
            System.exit(1);
        }
        
        try {
            try {
                //cerca nel file di configurazione la porta sulla quale avviare il servizio
                PORT = Integer.parseInt(p.getProperty("service.port"));
            }
            catch (Exception e) {}
            
            //...se non la trova usa la porta 25
            server = new ServerSocket(PORT);
            Socket client;
            
            System.out.println("Server Bukowski SMTP+SPF avviato");
            
            //crea il gestore delle caselle
            mailbox = new Mailbox(maildir);
            mailbox.start();
                        
            while (true) {
                //si mette in ascolto
                client = server.accept();
                (new Runner(client, maildir)).start();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            try {
                server.close();
            }
            catch (IOException ioe) {}
        }
    }
}