package org.keinus.logparser.util;

public class ThreadUtil {
    private ThreadUtil() {
        throw new IllegalStateException("Utility class");
    }
      
    public static void sleep(long millis) {
        try { 
            Thread.sleep(millis); 
        } catch (InterruptedException e) { 
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
}
