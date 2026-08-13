public class Anon {
    public Runnable make() {
        return new Runnable() {
            public void run() { Runnable r = () -> System.out.println("lambda in anon"); r.run(); }
        };
    }
}
