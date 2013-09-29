package bukowski.smtp;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.net.*;
import java.beans.*;

import bukowski.dns.*;
import bukowski.mail.*;

public class Mailbox extends Thread {
    
    private File maildir;
    private File vettoreSuDisco;
    public Vector messaggiInCoda;
    
    
    /** Costruttore: crea una nuova istanza di Mailbox
     * @param maildir
     * @throws Exception  */
    public Mailbox(File maildir) throws Exception {
        super();
        this.maildir = maildir;
        
        // leggiamo il vettore della coda di mail (deserializzazione)
        vettoreSuDisco = new File(maildir, "infocoda.xml");
        
        if (vettoreSuDisco.exists()) {
            XMLDecoder d = new XMLDecoder(new BufferedInputStream(new FileInputStream(vettoreSuDisco)));
            messaggiInCoda = (Vector) d.readObject();
            d.close();
        }
        else {
            messaggiInCoda = new Vector();
        }
    }
    
    public void run() {
        while (true) {
            try {
                Thread.sleep(10000);
            }
            catch (InterruptedException ie) {}
            spedisci();
        }
    }
    
    public synchronized void spedisci() {
        System.out.println("Il Postino sta cercando nella coda delle mail da spedire.");
        try {
            String mailfrom, destinatario, dominioDestinatario, username, messageId;
            Object[] elemento;
            InetAddress locale = InetAddress.getLocalHost();
            MimeMessage messaggioInSpedizione;
            File outbox = new File(maildir, "outbox");
            Properties props = new Properties();
            Session session;
            
            
            
            Object[] messaggiDaSpedire = messaggiInCoda.toArray();
            
            //cicla sui messaggi ancora in coda, li estrae dal file corrispondente, li spedisce
            for (int i = 0; i < messaggiDaSpedire.length; i++) {
                
                elemento = (Object[]) messaggiDaSpedire[i];
                mailfrom = ((InternetAddress) elemento[0]).getAddress();
                destinatario = ((InternetAddress) elemento[1]).getAddress();
                
                dominioDestinatario = destinatario.substring(destinatario.indexOf('@') + 1);
                username = destinatario.substring(0, destinatario.indexOf('@'));
                
                messageId = (String)elemento[2];
                
                FileInputStream fileMessaggio = new FileInputStream(new File(outbox, messageId));
                messaggioInSpedizione = new MyMimeMessage(null, fileMessaggio);
                
		boolean isDominioLocale = (dominioDestinatario.equals("localhost") || dominioDestinatario.equals(InetAddress.getLocalHost().getHostName()));
                
                String ipMXDestinatario = null;
                String nomeMXDestinatario = null;
                
                if (!isDominioLocale) {
                    String[] arrayServerMX = DNSQuery.queryMX(dominioDestinatario);
                    if ((arrayServerMX != null) && (arrayServerMX.length > 0)) {
                        ipMXDestinatario = InetAddress.getByName(arrayServerMX[0]).getHostAddress();
                        nomeMXDestinatario = InetAddress.getByName(arrayServerMX[0]).getHostName();
                    }
                }

                //se destinatario locale
                if (isDominioLocale || locale.getHostAddress().equals(ipMXDestinatario)) {
                    //accoda in casella locale

		    
		    File cartellaUtente = new File(maildir, username);
                    
                    //se casella esiste...
                    if (cartellaUtente.exists()) {
                        //aggiunge return-path
                        messaggioInSpedizione.setHeader("Return-Path", mailfrom);
                        
                        FileOutputStream out = new FileOutputStream(new File(cartellaUtente, messageId));
                        //scrive il messaggio nella casella utente
                        messaggioInSpedizione.writeTo(out);
                        out.close();
                        
                        //messaggio consegnato! lo rimuove dalla coda
                        messaggiInCoda.remove(elemento);
                        
                        System.out.println("\tMail consegnata in locale a " + username);
                    }
                    //altrimenti non esiste
                    else {
                        //spedisce un bounce di errore a "Return-path"
                        String mxBounce = DNSQuery.queryMX(mailfrom.substring(mailfrom.indexOf('@') + 1))[0];
                        //setta le proprieta' della sessione di spedizione della mail di bounce
                        props.clear();
                        props.put("mail.smtp.host", mxBounce);
                        props.put("mail.smtp.port", "25");
                        props.put("mail.host", dominioDestinatario);
                        props.put("mail.user", "postmaster");
                        session = Session.getDefaultInstance(props, null);
                        
                        MyMimeMessage messageBounce = new MyMimeMessage(session);
                        
                        try {
			    messageBounce.setSubject("Messaggio non recapitato: account inesistente");
                            messageBounce.setText("Messaggio da postmaster@"+dominioDestinatario+":\r\n\r\nMessaggio non recapitato. L`account "+username+" non esiste.");
                            
                            Transport.send(messageBounce, new Address [] { new InternetAddress(mailfrom) });
                            
                            messaggiInCoda.remove(elemento);
                            
                            System.out.println("\tUtente "+username+" non esiste. Spedito il bounce");
                        }
                        catch (SendFailedException sfe) {
                            sfe.printStackTrace();
                        }
                        catch (MessagingException me) {
                            me.printStackTrace();
                        }
                        
                    }
                }
                //se destinatario non locale
                else if (ipMXDestinatario != null) {
                    props.clear();
                    props.put("mail.smtp.host", ipMXDestinatario);
                    props.put("mail.smtp.port", "25");
                    
                    props.put("mail.host", mailfrom.substring(mailfrom.indexOf('@') + 1));
                    props.put("mail.user", mailfrom.substring(0, mailfrom.indexOf('@')));
                    
                    session = Session.getDefaultInstance(props, null);
                    
                    try {
                        Transport.send(messaggioInSpedizione, new Address[] { new InternetAddress(destinatario) });
                        messaggiInCoda.remove(elemento);
                        
                        System.out.println("\tDestinatario non locale (" + destinatario + "): spedito attraverso il server mx: "   +  nomeMXDestinatario + " (" + ipMXDestinatario + ")");
                    }
                    catch (SendFailedException sfe) {
                        System.out.println("\tERRORE: SendFailedException con " + destinatario);
                        sfe.printStackTrace();
                        
                        //se relaying not allowed, manda un bounce al mittente (mailfrom)
                        Address[] validUnsent = sfe.getValidUnsentAddresses();
                        
                        //se il messaggio non e' stato recapitato
                        if (((InternetAddress) validUnsent[0]).getAddress().equals(destinatario)) {
                            
                            String mxBounce = DNSQuery.queryMX(mailfrom.substring(mailfrom.indexOf('@') + 1))[0];
                            
                            props.clear();
                            props.put("mail.smtp.host", mxBounce);
                            props.put("mail.smtp.port", "25");
                            props.put("mail.host", locale.getHostName());
                            props.put("mail.user", "postmaster");
                            session = Session.getDefaultInstance(props, null);
                            
                            MyMimeMessage messageBounce = new MyMimeMessage(session);
                            
                            try {
                                messageBounce.setText("Messaggio da postmaster@"+locale.getHostName()+":\r\n\r\nRelaying not allowed.");
                                
                                Transport.send(messageBounce, new Address [] { new InternetAddress(mailfrom) });
                                
                                messaggiInCoda.remove(elemento);
                                
                                System.out.println("\tRelaying not allowed. Spedito il bounce");
                            }
                            catch (SendFailedException sfe2) {
                                sfe2.printStackTrace();
                            }
                            catch (MessagingException me) {
                                me.printStackTrace();
                            }
                        }
                        
                    }
                    catch (MessagingException me) {
                        System.out.println("\tERRORE: MessagingException con " + destinatario);
                        me.printStackTrace();
                        
                    }
                    
                }
                else {
                /*dns lookup fallito: non e' riuscito a ricavare l'mx a cui recapitare il messaggio,
                quindi lo lascia in coda per riprovare dopo*/
                    System.out.println("\tERRORE: DNSQuery con " + destinatario);
                }
                fileMessaggio.close();
            }
            
            //salva la coda dei messaggi non ancora recapitati
            XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(
                new FileOutputStream(vettoreSuDisco)));
            System.out.println("Eventuali messaggi spediti. Sto scrivendo infocoda.xml");
            System.out.println();
            e.writeObject(messaggiInCoda);
            e.close();
       

            //ricava i nomi dei file dei messaggi gia' consegnati a tutti i loro destinatari
            File[] fileDaCancellare = outbox.listFiles(
            new FileFilter() {
                public boolean accept(File pathname) {
                    Iterator i = messaggiInCoda.iterator();
                    
                    while (i.hasNext()) {
                        if (((String)((Object[])i.next())[2]).equals(pathname.getName())) {
                            return false;
                        }
                    }
                    
                    return true;
                }
            }
            );
            
            //...e li cancella dall'outbox
            for (int i = 0; i < fileDaCancellare.length; i++) {
                if (!fileDaCancellare[i].delete()) {
                    System.out.println("Non sono riuscito a cancellare il file "+ fileDaCancellare[i]);
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        //avviato = false;
    }
}
