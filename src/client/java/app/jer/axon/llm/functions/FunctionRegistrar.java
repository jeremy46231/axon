package app.jer.axon.llm.functions;

import io.github.sashirestela.openai.common.function.FunctionExecutor;

public class FunctionRegistrar {
    public static FunctionExecutor getFunctions() {
        FunctionExecutor functionExecutor = new FunctionExecutor();
        GeneralFunctions.enrollFunctions(functionExecutor);
        BaritoneFunctions.enrollFunctions(functionExecutor);
        return functionExecutor;
    }
}

