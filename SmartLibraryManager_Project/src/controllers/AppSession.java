package controllers;

public final class AppSession {
    private AppSession() {}          

    public static String userName;   
    public static String userEmail;  

    public static void clear() {
        userName = null;
        userEmail = null;
    }
}
