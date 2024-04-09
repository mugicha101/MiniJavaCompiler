package miniJava.CodeGeneration;

import miniJava.AbstractSyntaxTrees.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class StackAllocTable {
    // local variables
    private long stackOffset;
    private final Map<String, Long> stackVarMap = new HashMap<>(); // maps local variable to stack position
    private final Stack<String> stackVars = new Stack<>();
    private final Stack<Integer> stackSizes = new Stack<>();
    public StackAllocTable() {}
    // call when entering method
    public void reset() {
        stackOffset = 0;
        stackVarMap.clear();
        stackVars.clear();
        stackSizes.clear();
    }
    // adds a stack variable (ensure no double inserts)
    public void pushStackVariable(String name, int byteSize) {
        if (stackVarMap.containsKey(name))
            throw new CodeGenerationError(String.format("Variable with name %s already on the stack", name));
        stackVarMap.put(name, stackOffset);
        stackVars.add(name);
        stackSizes.add(byteSize);
        stackOffset -= byteSize;
    }
    // removes a stack variable (ensure expected variable is popped)
    public void popStackVariable(String name, int byteSize) {
        if (!stackVars.lastElement().equals(name) || stackSizes.lastElement() != byteSize)
            throw new CodeGenerationError(String.format("Tried to pop variable with name %s with size %d but top element has name %s with size %d", name, byteSize, stackVars.lastElement(), stackSizes.lastElement()));
        stackVarMap.remove(name);
        stackVars.pop();
        stackSizes.pop();
        stackOffset += byteSize;
    }
    // finds offset of stack variable from rbp
    public long findStackVariableRBPOffset(String name) {
        if (!stackVarMap.containsKey(name))
            throw new CodeGenerationError(String.format("No variable with name %s exists on the stack", name));
        return stackVarMap.get(name) - stackOffset;
    }
}
