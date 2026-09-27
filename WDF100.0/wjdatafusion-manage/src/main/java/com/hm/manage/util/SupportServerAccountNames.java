package com.hm.manage.util;

/** The fixed device account is hik; other usernames and all passwords retain their case. */
public final class SupportServerAccountNames
{
    private SupportServerAccountNames() {}

    public static String normalize(String username)
    {
        return username != null && "hik".equalsIgnoreCase(username.trim()) ? "hik" : username;
    }
}
