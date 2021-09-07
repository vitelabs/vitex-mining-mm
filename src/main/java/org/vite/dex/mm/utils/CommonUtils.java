package org.vite.dex.mm.utils;

import java.util.Calendar;

public class CommonUtils {
    // Get the timestamp at 12:30 pm every day
    public static Long getFixedTime() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 30);
        c.set(Calendar.SECOND, 0);

        return c.getTime().getTime() / 1000; // seconds
    }
}
