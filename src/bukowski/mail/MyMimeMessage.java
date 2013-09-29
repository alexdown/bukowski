package bukowski.mail;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.text.*;
import javax.mail.*;
import javax.mail.internet.*;

/** estendo MimeMessage perche' devo poter impostare il message-id del messaggio che sto inviando
    (diverso da quello automaticamente aggiunto da javamail) */
public class MyMimeMessage extends MimeMessage {
    
    /** costruttore
     * @param source
     * @throws MessagingException  */    
    public MyMimeMessage (MimeMessage source) throws MessagingException {
        super(source);
    }
 
    /** costruttore
     * @param session
     * @throws MessagingException  */    
    public MyMimeMessage (Session session) throws MessagingException {
        super(session);
    }
    
    /** costruttore
     * @param session
     * @param is
     * @throws MessagingException  */    
    public MyMimeMessage (Session session, java.io.InputStream is) throws MessagingException {
        super(session, is);
    }
    
    /** setta il mio header
     * @throws MessagingException  */    
    protected void updateHeaders() throws MessagingException {
        super.updateHeaders();
        Date Adesso = new Date();
        String LOCALNAME = null;
        try {
            LOCALNAME = InetAddress.getLocalHost().getHostName();;
        }
        catch (UnknownHostException ukhe) {}
        
        SimpleDateFormat AdessoPattern = new SimpleDateFormat("yyyyMMddHHmmss");
        SecureRandom tmp = new SecureRandom();
        StringBuffer IdUnivoco = new StringBuffer();
        byte[] tmp2 = new byte[10];
        tmp.nextBytes(tmp2);
        for (int i=0; i<10; i++) {
            IdUnivoco.append(Integer.toHexString(tmp2[i]).substring(0,1));
        }
        setHeader("Message-ID", "<"+AdessoPattern.format(Adesso)+"."+IdUnivoco + "@" +LOCALNAME+">");
        
    }
}
