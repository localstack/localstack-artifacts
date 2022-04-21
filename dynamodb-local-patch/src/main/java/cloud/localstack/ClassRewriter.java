package cloud.localstack;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Opcode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class ClassRewriter {
    static String CLASS_NAME = "com.amazonaws.services.dynamodbv2.local.shared.access.api.cp.CreateTableFunction";

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass,
                                    ProtectionDomain protectionDomain, byte[] bytes)
                    throws IllegalClassFormatException {

                if (s.endsWith("api/cp/CreateTableFunction")) {
                    try {
                        ClassPool cp = ClassPool.getDefault();
                        CtClass cc = cp.get(CLASS_NAME);
                        CtMethod m = cc.getDeclaredMethod("apply");

                        CodeIterator codeIterator = m.getMethodInfo().getCodeAttribute().iterator();
                        while (codeIterator.hasNext()) {
                            int pos = codeIterator.next();
                            int opcode = codeIterator.byteAt(pos);

                            if (opcode == Opcode.BIPUSH && codeIterator.byteAt(pos + 1) == 20) {
                                // Change "if(gsiIndexes.size() > 20)" to "if(gsiIndexes.size() > 100)"
                                codeIterator.writeByte(100, pos + 1);
                            }
                        }
                        byte[] byteCode = cc.toBytecode();
                        cc.detach();
                        return byteCode;
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                    }
                }
                return null;
            }
        });
    }
}
