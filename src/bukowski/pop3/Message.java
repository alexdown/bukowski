package bukowski.pop3;

import java.io.*;

/** crea l'oggetto messaggio a partire dal File file che gli viene passato */
class Message
{
    private long size = 0;
    private String content = null;
    private boolean delete = false;
    private File file = null;

    /** cotruttore
     * @param file  */    
    public Message(File file) {
        this.file = file;

        try {
            FileInputStream fis = new FileInputStream(file);
            StringBuffer buf = new StringBuffer();
            int c;

            while ((c = fis.read()) != -1)
                buf.append((char) c);

            fis.close();
            fis = null;

            this.content = buf.toString();
            this.size = buf.toString().length();
        }
        catch (Exception e) {}
    }

    /**
     * @return  */    
    public long getSize() {
        return size;
    }

    /**
     * @return  */    
    public String getContent() {
        return content;
    }

    /**
     * @return  */    
    public boolean getDeleteFlag() {
        return delete;
    }
    
    /**
     * @param delete  */    
    public void setDeleteFlag(boolean delete) {
        this.delete = delete;
    }

    /**
     * @return  */    
    public File getFile() {
        return file;
    }
}