package foo.collections;

import java.util.*;

public class NestedCollections {
    private static List<List<Integer>> x = List.of(List.of(1), List.of(1,2), List.of(1,2,3));

    public static boolean returnFalsy() {
        Set<Set<Set<String>>> y = new HashSet<>();
        Set<Set<String>> y1 = new HashSet<>();
        Set<String> y2 = new HashSet<>();
        y2.add("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        y1.add(y2);
        y.add(y1);
        return false;
    }
}
