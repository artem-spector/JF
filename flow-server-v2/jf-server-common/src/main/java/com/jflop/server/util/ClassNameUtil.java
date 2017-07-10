package com.jflop.server.util;

/**
 * TODO: Document!
 *
 * @author artem on 10/07/2017.
 */
public class ClassNameUtil {

    public static String replaceSlashWithDot(String className) {
        return className.indexOf("/") == -1 ? className : className.replace('/', '.');
    }

}
