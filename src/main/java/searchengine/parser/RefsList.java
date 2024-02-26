package searchengine.parser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class RefsList {
    private final Set<String> workRefs = Collections.synchronizedSet(new HashSet<>());
//    private final Set<String> workRefs = new ConcurrentSkipListSet<>();
    public void addRef(String refToAdd) {
        workRefs.add(refToAdd);
    }

    public boolean isRefPresent(String refToCheck) {
        return workRefs.contains(refToCheck);
    }
}
