package cloud.localstack;

import com.amazonaws.stepfunctions.local.StepFunctionsLocal;
import com.amazonaws.stepfunctions.local.runtime.Log;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InterruptiveArgsException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InvalidArgsException;

public class StepFunctionsStarter {
    static StepFunctionsLocal INSTANCE;

    // original command line arguments
    static String[] ARGS;

    public static void main(String[] args) {
        INSTANCE = new StepFunctionsLocal();
        ARGS = args;
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
