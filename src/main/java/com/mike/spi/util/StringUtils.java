package com.mike.spi.util;

import java.io.PrintWriter;

/**
 * @Author: MikeWang
 * @Date: 2019/12/19 2:29 PM
 * @Description:
 */
public final class StringUtils {
    public static boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

}
