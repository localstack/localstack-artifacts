package cloud.localstack;

import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import com.amazonaws.services.stepfunctions.model.LoggingConfiguration;
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
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

// TODO: update storing/loading of persistence state to loop over all entries in REQUEST_HANDLERS (for each region)
public class PersistenceContext {
    static final PersistenceContext INSTANCE = new PersistenceContext();
    Kryo kryo;

    synchronized Kryo getKryo() {
        if (kryo == null) {
            kryo = new Kryo();
            kryo.register(java.util.HashMap.class);
            kryo.register(java.util.Date.class);
            kryo.register(PersistenceState.class);
            kryo.register(PersistenceRegionState.class);
            // register state machine classes
            kryo.register(StateMachineModel.class);
            kryo.register(StateMachineStatus.class);
            // register activity classes
            kryo.register(ActivityModel.class);
            kryo.register(LinkedBlockingQueue.class);
            // register execution classes
            kryo.register(ExecutionModel.class);
            kryo.register(ExecutionStatus.class);
            // register LoggingConfiguration class
            kryo.register(LoggingConfiguration.class);

            // using objenesis StdInstantiatorStrategy to allow creating objects
            // from classes without default constructors
            kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        }
        return kryo;
    }

    PersistenceState getState() {
        PersistenceState state = new PersistenceState();
        RegionAspect.COMPONENT_PER_REGION.keySet().forEach(region -> {
            RequestHandlers handlers = RegionAspect.COMPONENT_PER_REGION.get(region).requestHandlers();
            PersistenceRegionState regionState = state.state.getOrDefault(region, new PersistenceRegionState());
            try {
                // get state machines
                Pair<Field, StateMachineRepo> smField = getStateMachinesField(handlers);
                regionState.stateMachines = (Map<String, StateMachineModel>) smField.getLeft().get(smField.getRight());

                // get executions
                Pair<Field, ExecutionRepo> exField = getExecutionsField(handlers);
                regionState.executions = (Map<String, ExecutionModel>) exField.getLeft().get(exField.getRight());

                // get activities
                Pair<Field, ActivityRepo> actField = getActivitiesField(handlers);
                regionState.activities = (Map<String, ActivityModel>) actField.getLeft().get(actField.getRight());

            } catch (Exception e) {
                e.printStackTrace(); // TODO: :)
            }
            state.state.putIfAbsent(region, regionState);
        });
        return state;
    }

    Pair<Field, StateMachineRepo> getStateMachinesField(RequestHandlers handlers) throws Exception {
        CreateStateMachine csm = ((CreateStateMachine)
                handlers.getHandlerByOperationName(CreateStateMachine.class.getSimpleName()));
        Field smrField = csm.getClass().getDeclaredField("stateMachineRepo");
        smrField.setAccessible(true);
        StateMachineRepo repo = (StateMachineRepo) smrField.get(csm);
        Field smField = repo.getClass().getDeclaredField("stateMachines");
        smField.setAccessible(true);
        return new ImmutablePair<>(smField, repo);
    }

    Pair<Field, ExecutionRepo> getExecutionsField(RequestHandlers handlers) throws Exception {
        StartExecution csm = ((StartExecution)
                handlers.getHandlerByOperationName(StartExecution.class.getSimpleName()));
        Field exrField = csm.getClass().getDeclaredField("executionRepo");
        exrField.setAccessible(true);
        ExecutionRepo repo = (ExecutionRepo) exrField.get(csm);
        Field exField = repo.getClass().getDeclaredField("executions");
        exField.setAccessible(true);
        return new ImmutablePair<>(exField, repo);
    }

    Pair<Field, ActivityRepo> getActivitiesField(RequestHandlers handlers) throws Exception {
        CreateActivity csm = ((CreateActivity)
                handlers.getHandlerByOperationName(CreateActivity.class.getSimpleName()));
        Field actrField = csm.getClass().getDeclaredField("activityRepo");
        actrField.setAccessible(true);
        ActivityRepo repo = (ActivityRepo) actrField.get(csm);
        Field actField = repo.getClass().getDeclaredField("activities");
        actField.setAccessible(true);
        return new ImmutablePair<>(actField, repo);
    }

    void loadState() {
        try {
            String stateFile = getStateFile();
            if (stateFile == null || !new File(stateFile).exists()) return;

            Kryo kryo = PersistenceContext.INSTANCE.getKryo();
            Input input = new Input(new FileInputStream(stateFile));
            PersistenceState state = kryo.readObject(input, PersistenceState.class);
            input.close();

            state.state.keySet().forEach(region -> {
                try {
                    RequestHandlers handlers = RegionAspect.getComponentForRegion(region).requestHandlers();
                    PersistenceRegionState regionState = state.state.getOrDefault(region, new PersistenceRegionState());

                    // set state machines
                    Pair<Field, StateMachineRepo> smField = getStateMachinesField(handlers);
                    smField.getLeft().set(smField.getRight(), regionState.stateMachines);

                    // set executions
                    Pair<Field, ExecutionRepo> exField = getExecutionsField(handlers);
                    exField.getLeft().set(exField.getRight(), regionState.executions);

                    // set activities
                    Pair<Field, ActivityRepo> actField = getActivitiesField(handlers);
                    actField.getLeft().set(actField.getRight(), regionState.activities);

                } catch (Exception e) {
                    e.printStackTrace(); // TODO: :)
                }
            });

        } catch (Exception e) {
            e.printStackTrace(); // TODO: :)
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
            PersistenceState state = context.getState();
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
        String baseDir = Paths.get(dataDir, "stepfunctions").toString();
        new File(baseDir).mkdirs();
        return Paths.get(baseDir, "backend_state").toString();
    }
}
