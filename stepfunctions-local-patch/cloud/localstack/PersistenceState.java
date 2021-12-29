package cloud.localstack;

import java.util.HashMap;
import java.util.Map;

public class PersistenceState {
    Map<String, PersistenceRegionState> state = new HashMap<>();
}
