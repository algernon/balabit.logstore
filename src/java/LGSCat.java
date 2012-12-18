import clojure.lang.LazySeq;
import BalaBit.LogStore;
import BalaBit.LogStoreMap;

public class LGSCat {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: LGSCat files...");
            System.exit (1);
        }

        for (String fn : args) {
            LogStore lgs = new BalaBit.LogStore (fn);
            LazySeq s = lgs.messages ();

            for (Object m : s) {
                LogStoreMap msg = new LogStoreMap (m);

                System.out.println (msg.get ("MESSAGE"));
            }
        }
    }
}
