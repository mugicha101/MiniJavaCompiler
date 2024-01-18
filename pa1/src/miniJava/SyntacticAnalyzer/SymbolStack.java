package miniJava.SyntacticAnalyzer;

public class SymbolStack {
    public final Symbol[] stack;
    public final int tokenIndex;
    public final int prodCount;
    public SymbolStack(Symbol[] stack, int tokenIndex, int prodCount) {
        this.stack = stack;
        this.tokenIndex = tokenIndex;
        this.prodCount = prodCount;
    }

    public Symbol top() {
        return stack[stack.length - 1];
    }

    public SymbolStack handleProduction(Symbol[] production) {
        if (stack.length == 0) throw new ArrayIndexOutOfBoundsException(); 
        Symbol[] newStack = new Symbol[stack.length + production.length - 1];
        System.arraycopy(stack, 0, newStack, 0, stack.length - 1);
        for (int i = 0; i < production.length; i++) {
            newStack[newStack.length - 1 - i] = production[i];
        }
        return new SymbolStack(newStack, tokenIndex, prodCount + 1);
    }

    public SymbolStack handleTerminal() {
        if (stack.length == 0) throw new ArrayIndexOutOfBoundsException();
        Symbol[] newStack = new Symbol[stack.length - 1];
        System.arraycopy(stack, 0, newStack, 0, stack.length - 1);
        return new SymbolStack(newStack, tokenIndex + 1, 0);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(tokenIndex);
        s.append(":");
        s.append(prodCount);
        s.append(" ");
        for (Symbol symbol : stack) {
            s.append(symbol.toString());
            s.append(" ");
        }
        if (stack.length > 0) s.setLength(s.length() - 1);
        return s.toString();
    }
}
