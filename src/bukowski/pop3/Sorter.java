package bukowski.pop3;

import java.io.*;
import java.util.*;

class Sorter implements Comparator {
    
    /** ordina i file per data
     * @param obj1
     * @param obj2
     * @return  */    
    public int compare(Object obj1, Object obj2) {
        long tmp = ((File) obj1).lastModified() - ((File) obj2).lastModified();
        if (tmp > 0)
            return 1;
        else if (tmp < 0)
            return -1;
        else
            return 0;
    }

    /**
     * @param obj
     * @return  */    
    public boolean equals(Object obj) {
        return obj.equals(this);
    }
}