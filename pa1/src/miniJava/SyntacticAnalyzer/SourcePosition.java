package miniJava.SyntacticAnalyzer;

public class SourcePosition {
    public final int line;
    public final int offset;
    public SourcePosition(int line, int offset) {
        this.line = line;
        this.offset = offset;
    }

    @Override
    public String toString() {
        return String.format("%d:%d", line, offset);
    }

    public int compareTo(SourcePosition other) {
        return line != other.line ? line < other.line ? -1 : 1
                : offset != other.offset ? offset < other.offset ? -1 : 1
                : 0;
    }
}
