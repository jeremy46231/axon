package app.jer.axon.agent.functions;

import io.github.sashirestela.openai.common.function.FunctionExecutor;

public class FunctionRegistrar {
    public static FunctionExecutor getFunctions() {
        FunctionExecutor functionExecutor = new FunctionExecutor();
        GeneralFunctions.enrollFunctions(functionExecutor);
        PlayerFunctions.enrollFunctions(functionExecutor);
        BaritoneFunctions.enrollFunctions(functionExecutor);
        return functionExecutor;
    }
}

