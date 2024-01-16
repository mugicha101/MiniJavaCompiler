package miniJava.SyntacticAnalyzer;

public class Token {
	private final TokenType type;
	private final String text;
	private final int line;
	private final int offset;
	
	public Token(TokenType type, String text, int line, int offset) {
		this.type = type;
		this.text = text;
		this.line = line;
		this.offset = offset;
	}
	
	public TokenType getTokenType() {
		return type;
	}
	
	public String getTokenText() {
		return text;
	}

	public int getLine() {
		return line;
	}

	public int getOffset() {
		return offset;
	}
}
