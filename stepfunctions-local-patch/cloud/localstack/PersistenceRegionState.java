package cloud.localstack;

import com.amazonaws.stepfunctions.local.model.ActivityModel;
import com.amazonaws.stepfunctions.local.model.ExecutionModel;
import com.amazonaws.stepfunctions.local.model.StateMachineModel;

import java.util.Map;

public class PersistenceRegionState {
    Map<String, StateMachineModel> stateMachines;
    Map<String, ExecutionModel> executions;
    Map<String, ActivityModel> activities;
}
