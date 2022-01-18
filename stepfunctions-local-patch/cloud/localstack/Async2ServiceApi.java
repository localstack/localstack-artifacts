package cloud.localstack;

import com.amazonaws.stepfunctions.local.runtime.executors.task.external.AsyncServiceAPI;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.Poller;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.SyncServiceAPI;

public class Async2ServiceApi extends AsyncServiceAPI {

    private final SyncServiceAPI caller;

    public String apiName() {
        return this.caller.apiName() + ".sync:2";
    }

    public Async2ServiceApi(SyncServiceAPI caller, Poller poller) {
        super(caller, poller);
        this.caller = caller;
    }
}
