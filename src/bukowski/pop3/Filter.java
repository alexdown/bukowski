package bukowski.pop3;

import java.io.*;

class Filter implements FileFilter {
    
    /** accetta solo i file non nascosti, che posso scrivere e leggere
     * @param pathname
     * @return  */    
    public boolean accept(File pathname) {
        return  pathname.isFile() &&
            (!pathname.isHidden()) &&
            pathname.canRead() &&
            pathname.canWrite();
    }
}