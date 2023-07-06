package searchengine.dto.indexing;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RefsList {
    private final Set<String> workRefs = Collections.synchronizedSet(new HashSet<>());

    public void addRef(String refToAdd) {
        workRefs.add(refToAdd);
    }

    public boolean isRefPresent(String refToCheck) {
        return workRefs.contains(refToCheck);
    }
}
