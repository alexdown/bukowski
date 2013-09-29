package bukowski.dns;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import bukowski.spf.*;
import bukowski.smtp.*;

/** utility per effettuare query sul dns */
public class DNSQuery {
    
    private static String serverDNS;
    // legge dal file di configurazione il server dns da usare
    static {
        Properties p = new Properties();
        InputStream in = null;
        
        File settings = new File(System.getProperty("user.home"), "smtp.properties");
        
        
        if (!settings.exists()) {
            in = SMTPMain.class.getResourceAsStream("/smtp.properties");
            try {
                p.load(in);
            }
            catch (Exception e) {}
        }
        else {
            try {
                in = new FileInputStream(settings);
                p.load(in);
            }
            catch (Exception e) {}
        }
        
        serverDNS = "dns://" + p.getProperty("dns.server");
    }
    
    
    
    
    /** query che ritorna i record di tipo A
     * @param dominio
     * @return  array di stringhe con i record A */    
    public static String[] queryA(String dominio) {
        
        //inizializza la classe Factory di Java Naming and Directory Interface, per effettuare richieste al dns
        Properties jndiProperties = new Properties();
        jndiProperties.put("java.naming.provider.url", serverDNS);
        jndiProperties.put("java.naming.factory.initial","com.sun.jndi.dns.DnsContextFactory");
        
        try {
            DirContext jndiCtx = new InitialDirContext(jndiProperties);
            Attributes attrs;
            String name;
            
            //setta il tipo di record da richiedere
            attrs = jndiCtx.getAttributes(dominio, new String[] {"A"});
            Attribute records = attrs.get("A");
            
            //se ce ne sono
            if (records != null) {
                NamingEnumeration enum = records.getAll();
                ArrayList lista = new ArrayList();
                
                while (enum.hasMore()) {
                    lista.add(enum.next().toString());
                }
                Object[] tmp = lista.toArray();
                String[] risultato = new String[tmp.length];
                
                
                for (int i = 0; i < tmp.length; i++) {
                    risultato[i] = tmp[i].toString();
                }
                
                return risultato;
            }
            else {
                return null;
            }
        }
        catch (Exception e ) {
            return null;
        }
    }
    
    
    
    /** query che ritorna i record di tipo MX
     * @param dominio
     * @return  array di stringhe con i record MX */    
    public static String[] queryMX(String dominio) {
        
        Properties jndiProperties = new Properties();
        jndiProperties.put("java.naming.provider.url", serverDNS);
        jndiProperties.put("java.naming.factory.initial","com.sun.jndi.dns.DnsContextFactory");
        
        
        try {
            DirContext jndiCtx = new InitialDirContext(jndiProperties);
            Attributes attrs;
            String name;
            
            attrs = jndiCtx.getAttributes(dominio, new String[] {"MX"});
            Attribute records = attrs.get("MX");
            
            
            if (records != null) {
                NamingEnumeration enum = records.getAll();
                ArrayList lista = new ArrayList();
                
                while (enum.hasMore()) {
                    String dominioVero = enum.next().toString();
                    lista.add(dominioVero.substring(dominioVero.indexOf(' ') + 1));
                }
                
                
                Object[] tmp = lista.toArray();
                String[] risultato = new String[tmp.length];
                
                
                for (int i = 0; i < tmp.length; i++) {
                    risultato[i] = tmp[i].toString();
                }
                
                return risultato;
            }
            else {
                return null;
            }
        }
        catch (Exception e ) {
            return null;
        }
    }
    
    /**  query che ritorna i record di tipo TXT (solo stringhe SPF)
     * @param dominio
     * @throws Exception
     * @return  array di stringhe con i record TXT (solo quelli che cominciano con v=spf1 */    
    public static String querySPF(String dominio) throws Exception {
        
        Properties jndiProperties = new Properties();
        jndiProperties.put("java.naming.provider.url", serverDNS);
        jndiProperties.put("java.naming.factory.initial","com.sun.jndi.dns.DnsContextFactory");
        
        
        DirContext jndiCtx = new InitialDirContext(jndiProperties);
        Attributes attrs;
        String name;
        
        attrs = jndiCtx.getAttributes(dominio, new String[] {"TXT"});
        Attribute records = attrs.get("TXT");
        
        if (records != null) {
            NamingEnumeration enum = records.getAll();
            String campo;
            
            while (enum.hasMore()) {
                campo = enum.next().toString();
                if (campo.startsWith("\"v=spf")) {
                    return campo.substring(1, campo.length() - 1);
                }
            }
        }
        
        return null;
    }
    
    /** Avvia il parser della grammatica definita in spf.jj, per controllare la stringa ricevuta da querySPF
     * @param IpSottoEsame
     * @param dominio
     * @return  */    
    public static String verifySPF(InetAddress IpSottoEsame, String dominio) {
        try {
            String spfrecord = querySPF(dominio);
            if (spfrecord != null) {
                SPFParser parser = new SPFParser(new StringReader(spfrecord));
                return parser.Input(IpSottoEsame, dominio);
            }
            else {
                return "none";
            }
        }
        catch (ParseException pe) {
            //errore nella stringa spf -> ritorno come se il risultato fosse "unknown" (vedi internet draft spf-draft-20040209)
            return "unknown";
        }
        catch (NamingException ne) {
            //errore durante il lookup -> ritorna un errore (error 450)
            return "error";
        }
        catch (Exception e) {
            return "none";
        }
    }
}
