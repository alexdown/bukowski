package bukowski.pop3;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

class Protocol
{
    // Minimum commands
    public static final String USER_COMMAND = "USER";
    public static final String PASS_COMMAND = "PASS";
    public static final String STAT_COMMAND = "STAT";
    public static final String LIST_COMMAND = "LIST";
    public static final String RETR_COMMAND = "RETR";
    public static final String DELE_COMMAND = "DELE";
    public static final String RSET_COMMAND = "RSET";
    public static final String QUIT_COMMAND = "QUIT";
    public static final String NOOP_COMMAND = "NOOP";

    // Optional Commands
    public static final String UIDL_COMMAND = "UIDL";
    public static final String TOP_COMMAND = "TOP";
    public static final String APOP_COMMAND = "APOP";

    // States
    public static final int AUTHORIZATION1_STATE = 1;
    public static final int AUTHORIZATION2_STATE = 2;
    public static final int TRANSACTION_STATE = 3;
    public static final int UPDATE_STATE = 4;

    // Responses
    public static final String GOOD_RESPONSE = "+OK";
    public static final String BAD_RESPONSE = "-ERR";

    // Messages
    public static final String WELCOME_MESSAGE = GOOD_RESPONSE + " Welcome to Java POP3 Server";
    public static final String READY_MESSAGE = GOOD_RESPONSE + " Java POP3 Server Ready";

    // Errors
    public static final String ERROR_UNKNOWN_COMMAND = BAD_RESPONSE + " Unknown command";
    public static final String ERROR_COMMAND_NOT_VALID_IN_THIS_STATE = BAD_RESPONSE + " Command not valid in this state";
    public static final String ERROR_MISSING_USERNAME = BAD_RESPONSE + " Missing username";
    public static final String ERROR_INVALID_USERNAME = BAD_RESPONSE + " Invalid username";
    public static final String ERROR_MISSING_PASSWORD = BAD_RESPONSE + " Missing password";
    public static final String ERROR_INVALID_PASSWORD = BAD_RESPONSE + " Invalid password";
    public static final String ERROR_INVALID_MESSAGE_ID = BAD_RESPONSE + " Invalid message ID";

    private String output = null;
    private int state = 0;
    private File maildir = null;
    private File userdir = null;
    private Vector table = null;
    private String username = null;
    private String key = null;

    /** costruttore
     * @param maildir  */    
    public Protocol(File maildir) {
        this.maildir = maildir;
    }

    /** processa i comandi che riceve dal client
     * @param input
     * @return  */    
    public String processInput(String input) {
        output = null;

        //se riceve una riga vuota, siamo all'inizio della connessione
        if (input == null) {
            table = new Vector();
            state = AUTHORIZATION1_STATE;
            int hash = hashCode();
            long time = System.currentTimeMillis();
            String host = "unknown";

            try {
                host = InetAddress.getLocalHost().getHostName();
            }
            catch (Exception e) {
                host = "unknown";
            }
            key = "<" + hash + "." + time + "@" + host + ">";

            output = WELCOME_MESSAGE + " " + key;
        }
        //altrimenti cerca di riconoscere un comando
        else {
            StringTokenizer st = new StringTokenizer(input);
            String cmd = null;
            String arg1 = null;
            String arg2 = null;
            int id = 0;

            //comandi senza parametri
            if (st.countTokens() == 1) {
                cmd = st.nextToken().toUpperCase();

                if (cmd.equals(STAT_COMMAND))
                    output = processSTAT();
                else if (cmd.equals(LIST_COMMAND))
                    output = processLIST1();
                else if (cmd.equals(RSET_COMMAND))
                    output = processRSET();
                else if (cmd.equals(QUIT_COMMAND))
                    output = processQUIT();
                else if (cmd.equals(NOOP_COMMAND))
                    output = processNOOP();
                else if (cmd.equals(UIDL_COMMAND))
                    output = processUIDL1();
                else
                    output = ERROR_UNKNOWN_COMMAND;
            }
            //comandi con un parametro
            else if (st.countTokens() == 2) {
                cmd = st.nextToken().toUpperCase();
                arg1 = st.nextToken();

                if (cmd.equals(USER_COMMAND))
                    output = processUSER(arg1);
                else if (cmd.equals(PASS_COMMAND))
                    output = processPASS(arg1);
                else if (cmd.equals(LIST_COMMAND)) {
                    try {
                        id = Integer.parseInt(arg1);
                        output = processLIST2(id);
                    }
                    catch (Exception e) {
                        output = ERROR_INVALID_MESSAGE_ID;
                    }
                }
                else if (cmd.equals(UIDL_COMMAND)) {
                    try {
                        id = Integer.parseInt(arg1);
                        output = processUIDL2(id);
                    }
                    catch (Exception e) {
                        output = ERROR_INVALID_MESSAGE_ID;
                    }
                }
                else if (cmd.equals(RETR_COMMAND)) {
                    try {
                        id = Integer.parseInt(arg1);
                        output = processRETR(id);
                    }
                    catch (Exception e) {
                        output = ERROR_INVALID_MESSAGE_ID;
                    }
                }
                else if (cmd.equals(DELE_COMMAND)) {
                    try {
                        id = Integer.parseInt(arg1);
                        output = processDELE(id);
                    }
                    catch (Exception e) {
                        output = ERROR_INVALID_MESSAGE_ID;
                    }
                }
                else
                    output = ERROR_UNKNOWN_COMMAND;
            }
            //comandi con due parametri
            else if (st.countTokens() == 3) {
                cmd = st.nextToken().toUpperCase();
                arg1 = st.nextToken();
                arg2 = st.nextToken();

                if (cmd.equals(TOP_COMMAND)) {
                    try {
                        id = Integer.parseInt(arg1);
                        int lines = Integer.parseInt(arg2);
                        output = processTOP(id, lines);
                    }
                    catch (Exception e) {
                        output = ERROR_INVALID_MESSAGE_ID;
                    }
                }
                else if (cmd.equals(APOP_COMMAND))
                    output = processAPOP(arg1, arg2);
                else
                    output = ERROR_UNKNOWN_COMMAND;
            }
            else
                output = ERROR_UNKNOWN_COMMAND;
        }

        return output;
    }

    private String processUSER(String username) {
        if (state == AUTHORIZATION1_STATE) {
            userdir = new File(maildir, File.separator + username);

            if (userdir.exists()) {
                this.username = username;
                state = AUTHORIZATION2_STATE;
                output = GOOD_RESPONSE;
            }
            else {
                userdir = null;
                output = ERROR_INVALID_USERNAME;
            }
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processPASS(String password) {
        if (state == AUTHORIZATION2_STATE) {
            String pwd = null;

            try {
                FileInputStream fis = new FileInputStream(new File(maildir, File.separator + "passwd.properties"));
                Properties p = new Properties();
                p.load(fis);
                fis.close();
                pwd = p.getProperty(username);
                fis = null;
                p = null;
            }
            catch (Exception e) {}

            if (password.equals(pwd)) {
                File[] f = userdir.listFiles(new Filter());
                Arrays.sort(f, new Sorter());

                for (int i = 0; i < f.length; i++)
                    table.add(new Message(f[i]));

                state = TRANSACTION_STATE;
                output = GOOD_RESPONSE;
            }
            else
                output = ERROR_INVALID_PASSWORD;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processSTAT() {
        if (state == TRANSACTION_STATE) {
            Enumeration e = table.elements();
            Message msg = null;
            int i = 0;
            int size = 0;

            while (e.hasMoreElements()) {
                msg = (Message) e.nextElement();
                if (!msg.getDeleteFlag()) {
                    i++;
                    size += msg.getSize();
                }
            }
            output = GOOD_RESPONSE + " " + i + " " + size;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processLIST2(int messageID) {
        if (state == TRANSACTION_STATE) {
            Message msg = (Message) table.get(messageID - 1);

            if ((msg != null) && (!msg.getDeleteFlag())) {
                output = GOOD_RESPONSE + " " + messageID + " " + msg.getSize();
            }
            else
                output = ERROR_INVALID_MESSAGE_ID;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processLIST1() {
        if (state == TRANSACTION_STATE) {
            Object[] msgs = table.toArray();
            Message msg = null;
            String str = "";
            int undel = 0;

            for (int i = 0; i < msgs.length; i++) {
                msg = (Message) msgs[i];
                if (!msg.getDeleteFlag()) {
                    undel++;
                    str += (i + 1) + " " + msg.getSize() + "\r\n";
                }
            }

            output = GOOD_RESPONSE + " " + undel + " messages\r\n" + str + ".";
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processRETR(int messageID) {
        if (state == TRANSACTION_STATE) {
            Message msg = (Message) table.get(messageID - 1);

            if ((msg != null) && (!msg.getDeleteFlag())) {
                output = GOOD_RESPONSE + " " + messageID + " octets\r\n" + msg.getContent() + "\r\n.";
            }
            else
                output = ERROR_INVALID_MESSAGE_ID;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processDELE(int messageID) {
        if (state == TRANSACTION_STATE) {
            Message msg = (Message) table.get(messageID - 1);

            if ((msg != null) && (!msg.getDeleteFlag())) {
                msg.setDeleteFlag(true);
                output = GOOD_RESPONSE;
            }
            else
                output = ERROR_INVALID_MESSAGE_ID;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processRSET() {
        if (state == TRANSACTION_STATE) {
            Object[] msgs = table.toArray();
            Message msg = null;

            for (int i = 0; i < msgs.length; i++) {
                msg = (Message) msgs[i];
                msg.setDeleteFlag(false);
            }

            output = GOOD_RESPONSE;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processQUIT() {
        if ((state == TRANSACTION_STATE) || (state == AUTHORIZATION1_STATE) || (state == AUTHORIZATION2_STATE)) {
            Object[] msgs = table.toArray();
            Message msg = null;

            for (int i = 0; i < msgs.length; i++) {
                msg = (Message) msgs[i];
                if (msg.getDeleteFlag())
                    msg.getFile().delete();
            }

            userdir = null;
            username = null;
            table.clear();
            output = GOOD_RESPONSE;
            state = UPDATE_STATE;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processNOOP() {
        if (state == TRANSACTION_STATE)
            output = GOOD_RESPONSE;
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processUIDL2(int messageID) {
        if (state == TRANSACTION_STATE) {
            Message msg = (Message) table.get(messageID - 1);
            String uid = null;

            if ((msg != null) && (!msg.getDeleteFlag())) {
                uid = getMD5(msg.getContent());
                output = GOOD_RESPONSE + " " + messageID + " " + ((uid != null) ? uid : Integer.toString(messageID));
            }
            else
                output = ERROR_INVALID_MESSAGE_ID;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processUIDL1() {
        if (state == TRANSACTION_STATE) {
            Object[] msgs = table.toArray();
            Message msg = null;
            String str = "";
            int undel = 0;
            String uid = null;

            for (int i = 0; i < msgs.length; i++) {
                msg = (Message) msgs[i];
                if (!msg.getDeleteFlag()) {
                    undel++;
                    uid = getMD5(msg.getContent());
                    str += (i + 1) + " " + ((uid != null) ? uid : Integer.toString(i + 1)) + "\r\n";
                }
            }

            output = GOOD_RESPONSE + " " + undel + " messages\r\n" + str + ".";
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processTOP(int messageID, int lines) {
        if (state == TRANSACTION_STATE) {
            Message msg = (Message) table.get(messageID - 1);

            if ((msg != null) && (!msg.getDeleteFlag())) {
                StringBuffer buf = new StringBuffer();

                try {
                    BufferedReader in = new BufferedReader(new StringReader(msg.getContent()));
                    String str;

                    while ((str = in.readLine()) != null) {
                        if (!str.equals(""))
                            buf.append(str + "\r\n");
                        else
                            break;
                    }
                    buf.append(str + "\r\n");

                    for (int i = 0; i < lines; i++) {
                        str = in.readLine();
                        if (str != null)
                            buf.append(str + "\r\n");
                    }

                    in.close();
                }
                catch (Exception e) {
                }

                output = GOOD_RESPONSE + " " + messageID + " octets\r\n" + buf.toString() + "\r\n.";
            }
            else
                output = ERROR_INVALID_MESSAGE_ID;
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    private String processAPOP(String username, String digest) {
        if (state == AUTHORIZATION1_STATE) {
            userdir = new File(maildir, File.separator + username);
            if (userdir.exists()) {
                this.username = username;
                String pwd = null;

                try {
                    FileInputStream fis = new FileInputStream(new File(maildir, File.separator + "passwd"));
                    Properties p = new Properties();
                    p.load(fis);
                    fis.close();
                    pwd = p.getProperty(username);
                    fis = null;
                    p = null;
                }
                catch (Exception e) {}

                if (digest.equals(getMD5(key + pwd))) {
                    File[] f = userdir.listFiles(new Filter());
                    Arrays.sort(f, new Sorter());

                    for (int i = 0; i < f.length; i++)
                        table.add(new Message(f[i]));

                    state = TRANSACTION_STATE;
                    output = GOOD_RESPONSE;
                }
                else
                    output = ERROR_INVALID_PASSWORD;
            }
            else {
                userdir = null;
                output = ERROR_INVALID_USERNAME;
            }
        }
        else
            output = ERROR_COMMAND_NOT_VALID_IN_THIS_STATE;

        return output;
    }

    /** crea l'hash per ogni messaggio in casella */
    private String getMD5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(content.getBytes());
            StringBuffer buf = new StringBuffer();

            for (int i = 0; i < b.length; i++)
                buf.append(format(Integer.toHexString(b[i] & 0x000000FF)));

            return buf.toString();
        }
        catch (Exception e) {
            return null;
        }
    }

    /** utility di getMD5 */
    private String format(String str) throws Exception {
        if (str.length() == 2)
            return str;
        else if (str.length() == 1)
            return "0" + str;
        else
            throw new Exception("str = " + str);
    }

    /**
     * @return  */    
    public boolean shutdown() {
        return (state == UPDATE_STATE);
    }
}