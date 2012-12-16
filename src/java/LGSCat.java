import BalaBit.LogStore;
import clojure.lang.LazySeq;
import clojure.lang.Keyword;
import java.util.Map;

public class LGSCat {
    public static void main(String[] args) {
        Keyword k = BalaBit.LogStore.keyword("MESSAGE");

        if (args.length < 1) {
            System.out.println("Usage: LGSCat files...");
            System.exit (1);
        }

        for (String fn : args) {
            Object o = BalaBit.LogStore.fromFile (fn);
            LazySeq s = (LazySeq) BalaBit.LogStore.messages (o);

            for (Object m : s) {
                Map msg = (Map) m;

                System.out.println (msg.get (k));
            }
        }
    }
}
