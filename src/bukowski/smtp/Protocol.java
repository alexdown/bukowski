package bukowski.smtp;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.text.*;
import javax.mail.*;
import javax.mail.internet.*;
import bukowski.mail.*;
import bukowski.dns.*;

/** gestione centralizzata del protocollo smtp  */
class Protocol {
    private File maildir = null;
    private Socket client;
    
    //stati in cui puo' trovarsi il server
    //necessari: MAIL FROM, RCPT TO, DATA (nell'ordine)
    //opzionali: helo (se nn c'e' il primo Received va con fuori dalla parentesi il dominio ottenuto risolvendo l'ip)
    //messaggi
    
    // priorita':
    //0 aspetta helo ehlo
    //1 aspetta mail
    //2 aspetta rcpt
    //3 aspetta data
    private int Sequenza = 0;
    private String output = null;
    private boolean devoChiudere = false;
    private StringBuffer IdUnivoco;
    private boolean messageIdInserito = true;
    private boolean dateInserito = true;
    private String risultatoSPF;
    
    //campi della mail
    private String Helo = null;
    private InternetAddress MailFrom = null;
    private Vector RcptTo = new Vector();
    private StringBuffer Data = new StringBuffer();
    
    //messaggio!!
    private MyMimeMessage Messaggio = null;
    
    //nome del server
    private static String LOCALNAME = null;
    static {
        try {
            LOCALNAME = InetAddress.getLocalHost().getHostName();;
        }
        catch (UnknownHostException ukhe) {}
    }
    
    //reply codes
    private static final String REPLY_CODE_221 = "221 Sayonara";
    
    private static final String REPLY_CODE_250 = "250 ";
    private static final String REPLY_CODE_250_OK = "250 Ok";
    private static final String REPLY_CODE_252 = "252 address verification is not available";
    private static final String REPLY_CODE_QUEUED = REPLY_CODE_250 + " Message queued";
    private static final String REPLY_CODE_354 = "354 Send mail. End with <CRLF>.<CRLF>";
    
    //errori
    private static final String ERROR_501 = "501 Error: bad syntax";
    private static final String ERROR_501_BAD_MAILFROM_ADDRESS = ERROR_501 + " - the destination address is not valid";
    
    private static final String ERROR_502 = "502 Error: command not implemented";
    
    private static final String ERROR_503 = "503 Error: "; //bad sequence
    private static final String ERROR_SEQUENZA_RCPT = ERROR_503 + "need RCPT command";
    private static final String ERROR_SEQUENZA_EHLO = ERROR_503 + "wrong state for EHLO command";
    private static final String ERROR_SEQUENZA_HELO = ERROR_503 + "wrong state for HELO command";
    private static final String ERROR_SEQUENZA_MAIL = ERROR_503 + "nested MAIL command";
    private static final String ERROR_SEQUENZA_MAIL2 = ERROR_503 + "wrong state for MAIL command";
    private static final String ERROR_SEQUENZA_RCPT2 = ERROR_503 + "wrong state for RCPT command";
    
    private static final String ERROR_504 = "504 Error: ";
    private static final String ERROR_504_EHLO_NOT_SUPPORTED = ERROR_504 + " extended SMTP command set not supported";
    
    private static final String ERROR_550 = "550 Error: requested action not taken";
    private static final String ERROR_550_SPF = "550 Error: spf query returns fail";
    private static final String ERROR_450_SPF = "450 Error: spf query returns error";
    
    
    /**
     * @param client socket della connessione
     * @param maildir directory dove sono conservati i messaggi */
    public Protocol(Socket client, File maildir) {
        this.maildir = maildir;
        this.client = client;
    }
    
    /**
     * riconosce il comando e chiama il metodo del comando
     * HELO identifica il dominio da cui proviene il msg
     * EHLO come sopra solo se il server supporta il set di cmd estesi (=NO)
     * MAIL FROM mittente
     * RCPT TO destinatario
     * DATA dati (e header) (a noi server solo cambiare To conformemente a from e dominio, cc e bcc,
     * e inserire gli SPF
     * RSET resetta la transazione, riporta allo stato subito dopo helo (o ehlo)
     * NOOP niente
     * QUIT sayonara
     * VRFY chiede se la casella (VRFY utente) esiste sul sistema locale. Per pratica un server non
     * conferma mai l'esistenza o meno di caselle, manda solo un bounce di errore se la casella
     * nn esiste
     * @param in comandi inviati dall'utente
     * @param out risposte del server
     * @return  la riga di risposta del server*/
    public String processInput(BufferedReader in, BufferedWriter out) {
        try {
            String inputLine = in.readLine();
            if (inputLine != null) {
                System.err.println(inputLine);
                if(inputLine.toUpperCase().startsWith("HELO ")) {
                    if (Sequenza == 0) return processHELO(inputLine);
                    else return ERROR_SEQUENZA_HELO;
                }
                else if(inputLine.toUpperCase().startsWith("EHLO ")) {
                    if (Sequenza == 0) return processEHLO(inputLine);
                    else return ERROR_SEQUENZA_EHLO;
                }
                else if (inputLine.toUpperCase().startsWith("MAIL ")) {
                    if (Sequenza == 1) return processMAIL(inputLine);
                    else if (Sequenza > 1) {
                        return ERROR_SEQUENZA_MAIL;
                    }
                    else {
                        return ERROR_SEQUENZA_MAIL2;
                    }
                }
                else if(inputLine.toUpperCase().startsWith("RCPT ")) {
                    if (Sequenza >= 2) return processRCPT(inputLine);
                    else {
                        return ERROR_SEQUENZA_RCPT2;
                    }
                }
                else if(inputLine.equalsIgnoreCase("DATA")) {
                    if (Sequenza > 2) {
                        //risponde "ok, sono pronto a ricevere il messaggio"
                        out.write(REPLY_CODE_354);
                        out.write("\r\n");
                        out.flush();
                        return processDATA(in);
                    }
                    else {
                        return ERROR_SEQUENZA_RCPT;
                    }
                }
                else if(inputLine.equalsIgnoreCase("NOOP")) {
                    return REPLY_CODE_250_OK;
                }
                else if(inputLine.equalsIgnoreCase("RSET")) {
                    return processRSET();
                }
                else if(inputLine.equalsIgnoreCase("QUIT")) {
                    return processQUIT();
                }
                else if(inputLine.toUpperCase().startsWith("VRFY ")) {
                    return processVRFY();   ///accetta, poi ritorna in ogni caso 252
                }
                else return ERROR_502;
            }
            else {
                return processQUIT();
            }
        }
        catch (IOException e) {
            return ERROR_502;
        }
    }
    
    /**
     * @param inputLine comando inserito dall'utente (su una riga)
     * @return  risposta del server */
    public String processHELO(String inputLine) {
        Sequenza++;
        int separator = inputLine.indexOf(" ");
        Helo = inputLine.substring(separator + 1);
        //fully qualified domain name del server locale, se esiste, oppure
        //indirizzo ip tra parentesi quadre
        return REPLY_CODE_250 + LOCALNAME;
    }
    
    /**
     * @param inputLine comando inserito dall'utente (su una riga)
     * @return risposta del server */
    public String processEHLO(String inputLine) {
        return ERROR_504_EHLO_NOT_SUPPORTED;
    }
    
    /**
     * @param inputLine comando inserito dall'utente (su una riga)
     * @return risposta del server */
    public String processMAIL(String inputLine) {
        int separator = inputLine.indexOf(":");
        if (separator != -1) {
            String cmd = inputLine.substring(0, separator);
            String arg = inputLine.substring(separator + 1);
            if (cmd.equalsIgnoreCase("MAIL FROM")) {
                try {
                    InternetAddress indirizzo = new InternetAddress(arg);
                    if (indirizzo.getType().equals("rfc822")) {
                        //query spf
                        risultatoSPF = DNSQuery.verifySPF(client.getInetAddress(), indirizzo.getAddress().substring(indirizzo.getAddress().indexOf("@") + 1));
                        if (risultatoSPF.equals("fail")) {
                            return ERROR_550_SPF;
                        }
                        if (risultatoSPF.equals("error")) {
                            return ERROR_450_SPF;
                        }
                        MailFrom = indirizzo;
                        Sequenza++;
                        return REPLY_CODE_250_OK;
                    }
                    else {
                        return ERROR_501_BAD_MAILFROM_ADDRESS;
                    }
                }
                catch (AddressException ae) {
                    return ERROR_501_BAD_MAILFROM_ADDRESS;
                }
            }
            else {
                return ERROR_501;
            }
        }
        else {
            return ERROR_501;
        }
    }
    
    /**
     * @param inputLine comando inserito dall'utente (su una riga)
     * @return risposta del server */
    public String processRCPT(String inputLine) {
        int separator = inputLine.indexOf(":");
        if (separator != -1) {
            //separa comando e argomento
            String cmd = inputLine.substring(0, separator);
            String arg = inputLine.substring(separator + 1);
            if (cmd.equalsIgnoreCase("RCPT TO")) {
                try {
                    InternetAddress indirizzo = new InternetAddress(arg);
                    if (indirizzo.getType().equals("rfc822")) {
                        //se indirizzo valido (conforme alla rfc822)
                        RcptTo.add(indirizzo);
                        Sequenza++;
                        return REPLY_CODE_250_OK;
                    }
                    else {
                        //indirizzo non valido
                        return ERROR_501_BAD_MAILFROM_ADDRESS;
                    }
                }
                catch (AddressException ae) {
                    return ERROR_501_BAD_MAILFROM_ADDRESS;
                }
            }
            else {
                return ERROR_501;
            }
        }
        else {
            //errore se la sintassi del comando e' sbagliata
            return ERROR_501;
        }
    }
    
    /**
     * @param in socket della connessione (perche' il messaggio occupa piu' righe)
     * @return risposta del server */
    public String processDATA(BufferedReader in) {
        
        String riga;
        try {
            //legge tutto il msg fino a <CR><LF>.<CR><LF>
            while (((riga = in.readLine()) != null)  &&  !riga.equals(".")) {
                //stampa la riga appena ricevuta, per debug
                System.err.println(riga);
                //e la accoda in un buffer
                Data.append(riga);
                Data.append("\r\n");
            }
            try {
                //salva il messaggio appena ricevuto in un oggetto MyMimeMessage (vd bukowski.mail.MyMimeMessage)
                Messaggio = new MyMimeMessage(null, new ByteArrayInputStream(Data.toString().getBytes()));
                Date Adesso = new Date();
                
                if (Messaggio.getSentDate() == null) {
                    //se non c'e' una data settata, la inserisco
                    Messaggio.setSentDate(Adesso);
                }
                //salvo il messaggio
                Messaggio.saveChanges();
            }
            
            catch (MessagingException me) {}
            return putInOutbox();
        }
        catch (IOException ioe) {
            //non ci sono piu' dati da leggere ma non e' ancora arrivato <CR><LF>.<CR><LF>... e' caduta la connessione
            return "readLine non legge piu'... e' caduta la connessione.";
        }
    }
    
    /** resetta lo stato del server
     * @return risposta del server */
    private String processRSET() {
        //nn zero perche' deve conservare un eventuale helo precedente!!!
        Sequenza = 1;
        RcptTo.clear();
        Data = new StringBuffer();
        return REPLY_CODE_250_OK;
    }
    
    /** verifica se un indirizzo esiste
     * good practice e' non rispondere a richieste del genere
     * @return risposta del server */
    private String processVRFY() {
        return REPLY_CODE_252;
    }
    
    /** scrive il messaggio nella cartella outbox (= lo accoda per la spedizione)
     * @return risposta del server (messaggio accodato) */
    private String putInOutbox() { //chiamato da data alla fine dell'inserimento
        try {
            //ricavo il messageID senza <>, per usarlo come nome del file che contiene il messaggio
            String messageid = Messaggio.getMessageID().substring(1, Messaggio.getMessageID().length() - 1);

            //istanzio lo stream per scrivere il file del messaggio su disco
            FileOutputStream out = new FileOutputStream(new File(new File(maildir, "outbox"), messageid));
            //setto l'header "Received" che mi compete (ogni smtp server che riceve il messaggio aggiunge il suo)
            Messaggio.setHeader("Received",
            "from "+ Helo + " (" + client.getInetAddress().getHostName() + " [" + client.getInetAddress().getHostAddress() + "]) " +
            "by " + LOCALNAME + " (Bukowski) with SMTP id " + "bukowski" + "; "+new Date());
            //scrivo l'header con il risultato della query spf (come da internet draft spf-draft-20040209)
            Messaggio.setHeader("Received-SPF", risultatoSPF);
            //salva il messaggio su disco
            Messaggio.writeTo(out);
            out.close();
            
            //aggiunge i destinatari del messaggio che sto processando alla coda dei messaggi da spedire
            Iterator x = RcptTo.iterator();
            while (x.hasNext()) {
                InternetAddress dest = (InternetAddress)x.next();
                SMTPMain.mailbox.messaggiInCoda.add(new Object[] { MailFrom, dest, messageid });
            }
            
            /*resetta le variabili di stato del server (Sequenza, rcpt e data):
            il server e' ora pronto per ricevere un nuovo MAIL FROM: */
            Sequenza = 1;
            RcptTo.clear();
            Data = new StringBuffer();
            
            /*
            try {
                //chiama il metodo di spedizione
                SMTPMain.mailbox.notify();
            }
            catch (Exception e) {
                System.err.println("ERRORE in SMTPMain.mailbox.spedisci();");
                e.printStackTrace();
            }
             */
            
            return REPLY_CODE_QUEUED + ": " + Messaggio.getMessageID();
        }
        catch (MessagingException me) {
            return ERROR_550;
        }
        catch (IOException ioe) {
            return ERROR_550;
        }
    }
    
    /** chiude la connessione
     * @return  */
    public String processQUIT() {
        devoChiudere = true;
        return REPLY_CODE_221;
    }
    
    /** ritorna true se il server e' in stato di chiusura
     * @return  */
    public boolean shutdown() {
        return devoChiudere;
    }
}