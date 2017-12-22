package io.codepace.coffeecoin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is just a bunch of utility methods
 */
public class Util {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    private final static Logger logger = Logger.getLogger(Util.class.getName());

    private Util(){}

    public static void logInfoAndPrint(String msg){
        logger.log(Level.INFO, msg);
        System.out.println(msg);
    }

    public static Logger getLogger(){
        return logger;
    }

    /**
     * Checks if data is all zeroes
     * @param data The data to check
     * @return boolean Whether or not the data is all zeroes
     */
    public static boolean isAllZeroes(String data){
        for (char c: data.toCharArray()) {
            if (c != '0'){
                return false;
            }
        }
        return true;
    }
}


