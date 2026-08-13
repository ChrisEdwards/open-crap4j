import java.util.function.*;
public enum Color {
    RED, GREEN;
    public Runnable go() { return () -> System.out.println("enum lambda"); }
}
