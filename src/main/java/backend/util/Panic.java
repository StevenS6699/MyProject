package backend.util;

public class Panic {
    public static void panic(Exception error){
        error.printStackTrace();
        System.exit(1);
    }
}
