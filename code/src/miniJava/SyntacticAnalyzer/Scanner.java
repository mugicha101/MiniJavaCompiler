package miniJava.SyntacticAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import miniJava.ErrorReporter;

public class Scanner {
	int line;
	int offset;
	final static char EOF = '\u001a';
	private InputStream in;
	private ErrorReporter errors;
	private StringBuilder currText;
	private char currChar;
	private static final HashMap<String,TokenType> keywords = new HashMap<>();

	static {
		keywords.put("class", TokenType.Class);
		keywords.put("this", TokenType.This);
		keywords.put("new", TokenType.New);
		keywords.put("if", TokenType.If);
		keywords.put("else", TokenType.Else);
		keywords.put("for", TokenType.For);
		keywords.put("while", TokenType.While);
		keywords.put("do", TokenType.Do);
		keywords.put("switch", TokenType.Switch);
		keywords.put("public", TokenType.Visibility);
		keywords.put("private", TokenType.Visibility);
		keywords.put("protected", TokenType.Visibility);
		keywords.put("default", TokenType.Visibility);
		keywords.put("static", TokenType.Static);
		keywords.put("void", TokenType.VoidType);
		keywords.put("boolean", TokenType.BooleanType);
		keywords.put("byte", TokenType.ByteType);
		keywords.put("char", TokenType.CharType);
		keywords.put("int", TokenType.IntType);
		keywords.put("long", TokenType.LongType);
		keywords.put("float", TokenType.FloatType);
		keywords.put("double", TokenType.DoubleType);
		keywords.put("return", TokenType.Return);
		keywords.put("true", TokenType.BooleanLiteral);
		keywords.put("false", TokenType.BooleanLiteral);
		keywords.put("null", TokenType.NullLiteral);
	}
	
	public Scanner( InputStream in, ErrorReporter errors ) {
		this.in = in;
		this.errors = errors;
		currText = new StringBuilder();
		line = 0;
		offset = -1;

		nextChar();
	}

	private enum State { Unknown, Token, TokenEnd, SingleLineComment, MultiLineComment }
	
	public Token scan() {
		clearCurrText();
		State state = State.Unknown;
		TokenType tokenType = TokenType.End;
		int startLine = line;
		int startOffset = offset;

		while (state != State.TokenEnd) {
			switch (state) {
				case Unknown: {
					startLine = line;
					startOffset = offset;
					switch (currChar) {
						case EOF: state = State.TokenEnd; break;
						case '/': {
							takeCurr();
							if (currChar == '/') { // check for "//" comment start
								state = State.SingleLineComment;
								clearCurrText();
								skipCurr();
							} else if (currChar == '*') { // check for "/*" multiline comment start
								state = State.MultiLineComment;
								clearCurrText();
								skipCurr();
								currText.append(' ');
							} else { // check for "/" operator
								state = State.TokenEnd;
								tokenType = TokenType.Divide;
							}
						} break;
						case '+': {
							tokenType = TokenType.Add;
							takeCurr();
							state = State.TokenEnd;
						} break;
						case '-': {
							takeCurr();
							tokenType = TokenType.Minus;
							state = State.TokenEnd;
						} break;
						case '*': {
							takeCurr();
							tokenType = TokenType.Multiply;
							state = State.TokenEnd;
						} break;
						case '=': {
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = TokenType.RelEq;
							} else tokenType = TokenType.AssignmentOp;
							state = State.TokenEnd;
						} break;
						case '<': case '>': {
							char c = currChar;
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = c == '<' ? TokenType.RelLEq : TokenType.RelGEq;
							} else tokenType = c == '<' ? TokenType.RelLT : TokenType.RelGT;
							state = State.TokenEnd;
						} break;
						case '"': {
							takeCurr();
							tokenType = TokenType.StringLiteral;
							state = State.Token;
						} break;
						case '\'': {
							takeCurr();
							tokenType = TokenType.CharLiteral;
							state = State.Token;
						} break;
						case '&': case '|': {
							char c = currChar;
							takeCurr();
							if (currChar == c) {
								takeCurr();
								tokenType = c == '&' ? TokenType.LogAnd : TokenType.LogOr;
							} else tokenType = c == '&' ? TokenType.BitAnd : TokenType.BitOr;
							state = State.TokenEnd;
						} break;
						case '!': {
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = TokenType.RelNEq;
							} else tokenType = TokenType.LogNot;
							state = State.TokenEnd;
						} break;
						case '^': {
							takeCurr();
							tokenType = TokenType.BitXor;
							state = State.TokenEnd;
						} break;
						case '~': {
							takeCurr();
							tokenType = TokenType.BitComp;
							state = State.TokenEnd;
						} break;
						case ',': {
							takeCurr();
							tokenType = TokenType.Comma;
							state = State.TokenEnd;
						} break;
						case ';': {
							takeCurr();
							tokenType = TokenType.Semicolon;
							state = State.TokenEnd;
						} break;
						case ':': {
							takeCurr();
							tokenType = TokenType.Colon;
							state = State.TokenEnd;
						} break;
						case '.': {
							takeCurr();
							tokenType = TokenType.Dot;
							state = State.TokenEnd;
 						} break;
						case '(': {
							takeCurr();
							tokenType = TokenType.LParen;
							state = State.TokenEnd;
						} break;
						case ')': {
							takeCurr();
							tokenType = TokenType.RParen;
							state = State.TokenEnd;
						} break;
						case '[': {
							takeCurr();
							tokenType = TokenType.LBracket;
							state = State.TokenEnd;
						} break;
						case ']': {
							takeCurr();
							tokenType = TokenType.RBracket;
							state = State.TokenEnd;
						} break;
						case '{': {
							takeCurr();
							tokenType = TokenType.LCurly;
							state = State.TokenEnd;
						} break;
						case '}': {
							takeCurr();
							tokenType = TokenType.RCurly;
							state = State.TokenEnd;
						} break;
						case '_': {
							// invalid identifier (cannot start with _)
							state = State.TokenEnd;
							tokenType = TokenType.Identifier;
						} break;
						default: {
							if (currIsWhitespace()) {
								skipCurr();
							} else if (currIsDigit()) {
								state = State.Token;
								tokenType = TokenType.IntLiteral;
								break;
								// note: numeric types tagged as int literals until distinguishable
							} else {
								state = State.Token;
								tokenType = TokenType.Identifier;
								// note: primitive types tagged as identifiers until matches keyword
							}
						} break;
					}
				} break;
				case SingleLineComment: {
					if (currIsNewline()) state = State.Unknown;
					skipCurr();
				} break;
				case MultiLineComment: {
					if (currIsEnd()) {
						errors.reportError(startLine, startOffset, "Unclosed multiline comment.");
						return makeToken(TokenType.Error, startLine, startOffset);
					}
					if (currText.charAt(0) == '*' && currChar == '/') {
						clearCurrText();
						state = State.Unknown;
					} else currText.setCharAt(0, currChar);
					skipCurr();
				} break;
				case Token: {
					boolean backslash = currText.length() != 0 && currText.charAt(currText.length()-1) == '\\';
					switch (tokenType) {
						case Identifier: {
							// check for invalid identifier characters (all keywords must also comply with this)
							if (currIsNewline() || (!currIsLetter() && !currIsDigit() && currChar != '_')) {
								// check for keyword match
								String key = currText.toString();
								if (currText.length() < 16 && keywords.containsKey(key))
									tokenType = keywords.get(key);
								state = State.TokenEnd;
								break;
							}

							takeCurr();
						} break;
						case IntLiteral: {
							if (currIsDigit()) {
								takeCurr();
							} else if (currChar == '.') {
								takeCurr();
								tokenType = TokenType.DoubleLiteral;
							} else if (currChar == 'd' || currChar == 'D') {
								skipCurr();
								tokenType = TokenType.DoubleLiteral;
								state = State.TokenEnd;
							} else if (currChar == 'f' || currChar == 'F') {
								skipCurr();
								tokenType = TokenType.FloatLiteral;
								state = State.TokenEnd;
							} else if (currChar == 'l' || currChar == 'L') {
								skipCurr();
								tokenType = TokenType.LongLiteral;
								state = State.TokenEnd;
							} else {
								state = State.TokenEnd;
							}
						} break;
						case DoubleLiteral: {
							if (currIsDigit()) {
								takeCurr();
							} else if (currChar == 'd' || currChar == 'D') {
								skipCurr();
								state = State.TokenEnd;
							} else if (currChar == 'f' || currChar == 'F') {
								skipCurr();
								tokenType = TokenType.FloatLiteral;
								state = State.TokenEnd;
							} else {
								state = State.TokenEnd;
							}
						} break;
						case StringLiteral: {
							if (currIsNewline()) {
								errors.reportError(startLine, startOffset, "Unclosed string literal.");
								return makeToken(TokenType.Error, startLine, startOffset);
							} else if (!backslash && currChar == '"') {
								state = State.TokenEnd;
								takeCurr();
							} else {
								takeCurr();
							}
						} break;
						case CharLiteral: {
							if (currIsNewline()) {
								errors.reportError(startLine, startOffset, "Unclosed char literal.");
								return makeToken(TokenType.Error, startLine, startOffset);
							} else if (!backslash && currChar == '\'') {
								state = State.TokenEnd;
								takeCurr();
							} else takeCurr();
						} break;
						default: {
							if (currIsNewline() || currIsWhitespace()) {
								state = State.TokenEnd;
							} else takeCurr();
						} break;
					}
				} break;
			}
		}
		if (tokenType == TokenType.StringLiteral || tokenType == TokenType.CharLiteral) {
			currText = new StringBuilder(currText.substring(1, currText.length()-1).replaceAll("\\\\n", "\n"));
			if (tokenType == TokenType.CharLiteral && currText.length() != 1) {
				errors.reportError(String.format("Invalid char literal '%s'", currText));
			}
		}
		else if (tokenType != TokenType.End && currText.length() == 0) {
			errors.reportError(line, offset, String.format("Invalid symbol %c", currChar));
			tokenType = TokenType.Error;
			currText.append(currChar);
		}
		if (tokenType == TokenType.End) currText = new StringBuilder("EOF");
		return makeToken(tokenType, startLine, startOffset);
	}
	
	private void takeCurr() {
		currText.append(currChar);
		nextChar();
	}
	
	private void skipCurr() {
		nextChar();
	}

	private boolean currIsNewline() {
		return currChar == '\n' || currChar == EOF;
	}

	private boolean currIsWhitespace() {
		return currChar == ' ' || currChar == '\t' || currChar == '\r' || currIsNewline();
	}

	private boolean currIsDigit() {
		return currChar >= '0' && currChar <= '9';
	}

	private boolean currIsLetter() {
		return (currChar >= 'a' && currChar <= 'z') || (currChar >= 'A' && currChar <= 'Z');
	}

	private boolean currIsEnd() { return (currChar == EOF); }

	private void clearCurrText() {
		currText.setLength(0);
	}
	
	private void nextChar() {
		try {
			// handle end of file
			if (in.available() == 0) {
				currChar = EOF;
				offset += 1;
				return;
			}

			int c = in.read();
			if (c < 0 || c > 255) throw new IOException(); // catch non-ascii
			currChar = (char)c;
		} catch( IOException e ) {
			currChar = EOF;
			errors.reportError(line, offset+1, String.format("Lexical Error after %d:%d"));
		}
		
		if (currIsNewline()) {
			line += 1;
			offset = -1;
		} else if (currChar != '\r') {
			offset += 1;
		}
	}
	
	private Token makeToken( TokenType tokenType, int line, int offset ) {
		return new Token(tokenType, currText.toString(), line, offset);
	}
}
