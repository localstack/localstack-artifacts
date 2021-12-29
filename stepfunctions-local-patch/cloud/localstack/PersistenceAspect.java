package cloud.localstack;

import com.amazonaws.stepfunctions.local.runtime.Log;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Aspect
public class PersistenceAspect {

    static Lock lock = new ReentrantLock();

    @After("execution(* com.amazonaws..StateMachineRepo.createStateMachine(..))")
    public void afterCreateStateMachine(JoinPoint joinPoint) {
        PersistenceContext.INSTANCE.writeState();
    }

    @After("execution(* com.amazonaws..StepFunctionsLocal.start(..))")
    public void afterStartup(JoinPoint joinPoint) {
        // StepFunctionsLocal.start is overloaded (once with String[] argument and once without arguments)
        if(joinPoint.getArgs().length == 0) {
            return;
        }

        lock.lock();
        try {
            Log.info("Loading initial state from LocalStack persistence.");
            PersistenceContext.INSTANCE.getKryo();
            PersistenceContext.INSTANCE.loadState();
        } catch (Exception e) {
            Log.error("Unable to initialize persistence context", e);
        } finally {
            lock.unlock();
        }
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

}
