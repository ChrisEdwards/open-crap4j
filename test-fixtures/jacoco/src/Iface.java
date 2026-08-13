import java.util.function.*;
public interface Iface {
    default void doIt() { Runnable r = () -> System.out.println("default"); r.run(); }
    static void stat() { Runnable r = () -> System.out.println("iface static"); r.run(); }
}
