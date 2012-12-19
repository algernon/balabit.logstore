import clojure.lang.LazySeq;
import BalaBit.LogStore;
import BalaBit.LogStoreMap;
import java.util.Map;

public class LGSCat {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: LGSCat files...");
            System.exit (1);
        }

        for (String fn : args) {
            LogStore lgs = new BalaBit.LogStore (fn);

            for (Object m : lgs.messages ()) {
                LogStoreMap msg = new LogStoreMap (m);

                System.out.println (msg.get ("MESSAGE"));

                for (Object o: msg.entrySet()) {
                    Map.Entry<String, Object> e = (Map.Entry) o;

                    if (e.getKey () == "MESSAGE" ||
                        e.getKey () == "meta")
                        continue;

                    System.out.println ("\t=> " + e.getKey() + ": " + e.getValue());
                }
            }
        }
    }
}
