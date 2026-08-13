import java.util.function.*;
import java.util.*;
public record Rec(List<String> items) {
    public Rec {
        items = items.stream().filter(s -> !s.isEmpty()).toList();
    }
    public long count() { return items.stream().filter(s -> s.length() > 2).count(); }
}
