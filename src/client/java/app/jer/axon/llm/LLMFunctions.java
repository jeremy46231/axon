package app.jer.axon.llm;

import io.github.sashirestela.openai.common.function.FunctionExecutor;

public class LLMFunctions {
    public static FunctionExecutor getFunctions() {
        FunctionExecutor functionExecutor = new FunctionExecutor();
        BaritoneFunctions.enrollFunctions(functionExecutor);
        return functionExecutor;
    }
}

