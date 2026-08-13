import java.util.function.*;
import java.util.stream.*;
import java.util.*;

public class Sample {
    private Runnable field = () -> System.out.println("field init");
    private static Runnable sfield = () -> System.out.println("static field init");
    static { Runnable r = () -> System.out.println("static block"); r.run(); }

    public Sample() { Runnable r = () -> System.out.println("ctor"); r.run(); }
    public Sample(int x) { Runnable r = () -> System.out.println("ctor int"); r.run(); }

    public void ordinary() {
        Runnable r = () -> System.out.println("ordinary");
        r.run();
    }

    public void over(String s) { Runnable r = () -> System.out.println("over s"); r.run(); }
    public void over(int i) { Runnable r = () -> System.out.println("over i"); r.run(); }

    public void nested() {
        Supplier<Runnable> s = () -> () -> System.out.println("inner");
        s.get().run();
    }

    public int sw(int day) {
        return switch (day) {
            case 1, 2 -> 10;
            case 3 -> { yield 20; }
            default -> 0;
        };
    }
}
