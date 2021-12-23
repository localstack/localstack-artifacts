package cloud.localstack;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClient;
import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import com.amazonaws.services.stepfunctions.model.StateMachineStatus;
import com.amazonaws.stepfunctions.local.StepFunctionsLocal;
import com.amazonaws.stepfunctions.local.dagger.ClientModule;
import com.amazonaws.stepfunctions.local.dagger.DaggerSfnLocalComponent;
import com.amazonaws.stepfunctions.local.dagger.SfnLocalComponent;
import com.amazonaws.stepfunctions.local.http.HttpRequestHandlers;
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
import com.amazonaws.stepfunctions.local.runtime.Log;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InterruptiveArgsException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InvalidArgsException;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.objenesis.strategy.StdInstantiatorStrategy;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class StepFunctionsStarter {
    static StepFunctionsLocal INSTANCE;

    // maps region names to RequestHandlers instances
    static final Map<String, SfnLocalComponent> COMPONENT_PER_REGION = new HashMap<>();

    // original command line arguments
    static String[] ARGS;

    static class PersistenceState {
        Map<String, StateMachineModel> stateMachines;
        Map<String, ExecutionModel> executions;
        Map<String, ActivityModel> activities;
    }

    // TODO: update storing/loading of persistence state to loop over all entries in REQUEST_HANDLERS (for each region)
    static class PersistenceContext {
        static final PersistenceContext INSTANCE = new PersistenceContext();
        Kryo kryo;
        RequestHandlers handlers;

        synchronized Kryo getKryo() {
            if (kryo == null) {
                kryo = new Kryo();
                kryo.register(java.util.HashMap.class);
                kryo.register(java.util.Date.class);
                kryo.register(PersistenceState.class);
                // register state machine classes
                kryo.register(StateMachineModel.class);
                kryo.register(StateMachineStatus.class);
                // register activity classes
                kryo.register(ActivityModel.class);
                kryo.register(LinkedBlockingQueue.class);
                // register execution classes
                kryo.register(ExecutionModel.class);
                kryo.register(ExecutionStatus.class);

                // using obnesis StdInstantiatorStrategy to allow creating objects
                // from classes without default constructors
                kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
            }
            return kryo;
        }

        PersistenceState getState() throws Exception {
            PersistenceState state = new PersistenceState();

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
            if(handlers == null) {
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
                PersistenceState state = kryo.readObject(input, PersistenceState.class);
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
            // Note: simply using "/" as separator, assuming that we're on Unix
            String baseDir = dataDir + "/api_states/stepfunctions/_";
            new File(baseDir).mkdirs();
            return baseDir + "/backend_state";
        }
    }

    @Aspect
    public static class PersistenceAspect {
        @After("execution(* com.amazonaws..StateMachineRepo.createStateMachine(..))")
        public void afterCreateStateMachine(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }
        @After("execution(* com.amazonaws..StateMachineRepo.updateStateMachine(..))")
        public void afterUpdateStateMachine(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }
        @After("execution(* com.amazonaws..StateMachineRepo.deleteStateMachine(..))")
        public void afterDeleteStateMachine(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }

        @After("execution(* com.amazonaws..ExecutionRepo.createExecution(..))")
        public void afterCreateExecution(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }
        @After("execution(* com.amazonaws..ExecutionRepo.updateExecution(..))")
        public void afterUpdateExecution(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }
        @After("execution(* com.amazonaws..ExecutionRepo.deleteExecution(..))")
        public void afterDeleteExecution(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }

        @After("execution(* com.amazonaws..ActivityRepo.createActivity(..))")
        public void afterCreateActivity(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }
        @After("execution(* com.amazonaws..ActivityRepo.deleteActivity(..))")
        public void afterDeleteActivity(JoinPoint joinPoint) {
            PersistenceContext.INSTANCE.writeState();
        }

        @Around("execution(* com.amazonaws..HttpRequestHandlers.handle(..))")
        public void aroundHttpHandle(ProceedingJoinPoint joinPoint) throws Throwable {
            HttpRequestHandlers httpHandlers = (HttpRequestHandlers)joinPoint.getTarget();
            Field f = httpHandlers.getClass().getDeclaredField("requestHandler");
            f.setAccessible(true);

            // extract region from request
            HttpServletRequest request = (HttpServletRequest) joinPoint.getArgs()[2];
            String authHeader = request.getHeader("Authorization");
            String region = authHeader.split("Credential=")[1].split("/")[2];

            // determine request handler for region
            SfnLocalComponent component = COMPONENT_PER_REGION.get(region);
            if (component == null) {
                component = DaggerSfnLocalComponent.builder().build();

                // initialize config from cmd line args
                component.config().parseArgs(ARGS);

                // adjust region in handler
                component.config().getOptionRegion().setValue(region);
                COMPONENT_PER_REGION.put(region, component);
            }

            // update requestHandler for this request, then proceed with invocation
            f.set(httpHandlers, component.requestHandlers());
            joinPoint.proceed();
        }

    }

    public static void main(String[] args) {
        INSTANCE = new StepFunctionsLocal();
        ARGS = args;

        // load state from persistence files
        try {
            PersistenceContext.INSTANCE.getKryo();
            PersistenceContext.INSTANCE.loadState();
        } catch (Exception e) {
            System.out.println("Unable to initialize persistence context: " + e);
        }

        try {
            INSTANCE.config().printVersion();
            INSTANCE.config().readEnv();
            INSTANCE.start(args);
            INSTANCE.join();
        } catch (InvalidArgsException var3) {
            Log.info("Exit: " + var3.getMessage());
            System.exit(1);
        } catch (InterruptiveArgsException var4) {
            System.exit(0);
        }
    }
}
