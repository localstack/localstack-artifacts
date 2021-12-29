package cloud.localstack;

import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import com.amazonaws.services.stepfunctions.model.StateMachineStatus;
import com.amazonaws.stepfunctions.local.http.RequestHandlers;
import com.amazonaws.stepfunctions.local.model.ActivityModel;
import com.amazonaws.stepfunctions.local.model.ExecutionModel;
import com.amazonaws.stepfunctions.local.model.StateMachineModel;
import com.amazonaws.stepfunctions.local.operation.CreateActivity;
import com.amazonaws.stepfunctions.local.operation.CreateStateMachine;
import com.amazonaws.stepfunctions.local.operation.StartExecution;
import com.amazonaws.stepfunctions.local.repo.ActivityRepo;
import com.amazonaws.stepfunctions.local.repo.ExecutionRepo;
import com.amazonaws.stepfunctions.local.repo.StateMachineRepo;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

// TODO: update storing/loading of persistence state to loop over all entries in REQUEST_HANDLERS (for each region)
class PersistenceContext {
    static final PersistenceContext INSTANCE = new PersistenceContext();
    Kryo kryo;
    RequestHandlers handlers;

    synchronized Kryo getKryo() {
        if (kryo == null) {
            kryo = new Kryo();
            kryo.register(java.util.HashMap.class);
            kryo.register(java.util.Date.class);
            kryo.register(StepFunctionsStarter.PersistenceState.class);
            // register state machine classes
            kryo.register(StateMachineModel.class);
            kryo.register(StateMachineStatus.class);
            // register activity classes
            kryo.register(ActivityModel.class);
            kryo.register(LinkedBlockingQueue.class);
            // register execution classes
            kryo.register(ExecutionModel.class);
            kryo.register(ExecutionStatus.class);

            // using objenesis StdInstantiatorStrategy to allow creating objects
            // from classes without default constructors
            kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        }
        return kryo;
    }

    StepFunctionsStarter.PersistenceState getState() throws Exception {
        StepFunctionsStarter.PersistenceState state = new StepFunctionsStarter.PersistenceState();

        // get state machines
        Pair<Field, StateMachineRepo> smField = getStateMachinesField();
        state.stateMachines = (Map<String, StateMachineModel>) smField.getLeft().get(smField.getRight());

        // get executions
        Pair<Field, ExecutionRepo> exField = getExecutionsField();
        state.executions = (Map<String, ExecutionModel>) exField.getLeft().get(exField.getRight());

        // get activities
        Pair<Field, ActivityRepo> actField = getActivitiesField();
        state.activities = (Map<String, ActivityModel>) actField.getLeft().get(actField.getRight());

        return state;
    }

    Pair<Field, StateMachineRepo> getStateMachinesField() throws Exception {
        CreateStateMachine csm = ((CreateStateMachine)
                getHandlers().getHandlerByOperationName(CreateStateMachine.class.getSimpleName()));
        Field smrField = csm.getClass().getDeclaredField("stateMachineRepo");
        smrField.setAccessible(true);
        StateMachineRepo repo = (StateMachineRepo) smrField.get(csm);
        Field smField = repo.getClass().getDeclaredField("stateMachines");
        smField.setAccessible(true);
        return new ImmutablePair<>(smField, repo);
    }

    Pair<Field, ExecutionRepo> getExecutionsField() throws Exception {
        StartExecution csm = ((StartExecution)
                getHandlers().getHandlerByOperationName(StartExecution.class.getSimpleName()));
        Field exrField = csm.getClass().getDeclaredField("executionRepo");
        exrField.setAccessible(true);
        ExecutionRepo repo = (ExecutionRepo) exrField.get(csm);
        Field exField = repo.getClass().getDeclaredField("executions");
        exField.setAccessible(true);
        return new ImmutablePair<>(exField, repo);
    }

    Pair<Field, ActivityRepo> getActivitiesField() throws Exception {
        CreateActivity csm = ((CreateActivity)
                getHandlers().getHandlerByOperationName(CreateActivity.class.getSimpleName()));
        Field actrField = csm.getClass().getDeclaredField("activityRepo");
        actrField.setAccessible(true);
        ActivityRepo repo = (ActivityRepo) actrField.get(csm);
        Field actField = repo.getClass().getDeclaredField("activities");
        actField.setAccessible(true);
        return new ImmutablePair<>(actField, repo);
    }

    RequestHandlers getHandlers() throws Exception {
        if (handlers == null) {
            Method m = StepFunctionsStarter.INSTANCE.getClass().getDeclaredMethod("handlers");
            m.setAccessible(true);
            handlers = (RequestHandlers) m.invoke(StepFunctionsStarter.INSTANCE);
        }
        return handlers;
    }

    void loadState() {
        try {
            String stateFile = getStateFile();
            if (stateFile == null || !new File(stateFile).exists()) return;

            Kryo kryo = PersistenceContext.INSTANCE.getKryo();
            Input input = new Input(new FileInputStream(stateFile));
            StepFunctionsStarter.PersistenceState state = kryo.readObject(input, StepFunctionsStarter.PersistenceState.class);
            input.close();

            // set state machines
            Pair<Field, StateMachineRepo> smField = getStateMachinesField();
            smField.getLeft().set(smField.getRight(), state.stateMachines);

            // set executions
            Pair<Field, ExecutionRepo> exField = getExecutionsField();
            exField.getLeft().set(exField.getRight(), state.executions);

            // set activities
            Pair<Field, ActivityRepo> actField = getActivitiesField();
            actField.getLeft().set(actField.getRight(), state.activities);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeState() {
        try {
            PersistenceContext context = PersistenceContext.INSTANCE;
            String stateFile = context.getStateFile();
            if (stateFile == null) {
                return;
            }

            Kryo kryo = context.getKryo();
            StepFunctionsStarter.PersistenceState state = context.getState();
            Output output = new Output(new FileOutputStream(stateFile));
            kryo.writeObject(output, state);
            output.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    String getStateFile() {
        String dataDir = System.getenv("DATA_DIR");
        if (dataDir == null || dataDir.trim().isEmpty()) return null;
        // Note: simply using "/" as separator, assuming that we're on Unix
        String baseDir = dataDir + "/api_states/stepfunctions/_";
        new File(baseDir).mkdirs();
        return baseDir + "/backend_state";
    }
}
