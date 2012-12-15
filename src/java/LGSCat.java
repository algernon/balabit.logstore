import BalaBit.LogStore;
import clojure.lang.LazySeq;
import clojure.lang.Keyword;
import java.util.Map;

public class LGSCat {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: LGSCat files...");
            System.exit (1);
        }

        for (String fn : args) {
            Object o = BalaBit.LogStore.fromFile (fn);
            LazySeq s = (LazySeq) BalaBit.LogStore.messages (o);

            for (Object x : s.toArray()) {
                Map m = (Map) x;
                Keyword k = BalaBit.LogStore.keyword("MESSAGE");

                System.out.println(m.get(k));
            }
        }
    }
}
