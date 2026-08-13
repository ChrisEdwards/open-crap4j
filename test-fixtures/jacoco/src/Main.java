import java.util.List;
public class Main {
    public static void main(String[] args) {
        Sample s = new Sample();
        s.ordinary();
        s.over("x");
        s.nested();
        s.sw(1);
        Iface i = new Iface() {};
        i.doIt();
        Rec r = new Rec(List.of("ab", "abc", ""));
        r.count();
        new Anon().make().run();
    }
}
