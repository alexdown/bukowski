package bukowski.pop3;

import java.io.*;
import java.net.*;
import java.util.*;

/** analogo al Main di smtp */
public class POP3Main {
    
private static int PORT = 5110;
    private static ServerSocket server = null;
    public static File maildir = null;
    
    /**
     * @param argv  */    
    public static void main(String[] argv) {
        
        Properties p = new Properties();
        try {
            InputStream in = null;
            
            File settings = new File(System.getProperty("user.home"), "pop3.properties");
            
            if (!settings.exists())
                in = POP3Main.class.getResourceAsStream("/pop3.properties");
            else
                in = new FileInputStream(settings);
            
            p.load(in);
            maildir = new File(p.getProperty("mail.directory"));
            
            if (!maildir.exists()) {
                in.close();
                in = null;
                p = null;
                System.err.println("Unable to find mail directory.");
                System.exit(1);
            }
            
            
            in.close();
            
            in = null;
            p = null;
        }
        catch (Exception e) {
            System.err.println("Unable to find mail directory.");
            System.exit(1);
        }
        
        try {
            try {
                //cerca nel file di configurazione la porta sulla quale avviare il servizio
                PORT = Integer.parseInt(p.getProperty("service.port"));
            }
            catch (Exception e) {}
            
            //...se non la trova usa la porta 110
            server = new ServerSocket(PORT);
            Socket client;
            
            System.out.println("Server Bukowski POP3 avviato");
            
            while (true) {
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