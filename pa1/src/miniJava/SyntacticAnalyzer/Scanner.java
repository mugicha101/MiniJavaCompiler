package miniJava.SyntacticAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import miniJava.ErrorReporter;

public class Scanner {
	int line;
	int offset;
	final static char EOF = '\u001a';
	private static class KeywordTrieNode {
		private HashMap<Character, KeywordTrieNode> next = new HashMap<>();
		private TokenType tokenType = null; // null if not end of keyword
		private void insert(String keyword, TokenType tokenType, int index) {
			if (index == keyword.length()) {
				this.tokenType = tokenType;
				return;
			}
			char c = keyword.charAt(index);
			if (!next.containsKey(c))
				next.put(c, new KeywordTrieNode());
			next.get(c).insert(keyword, tokenType, index + 1);
		}
		public void insert(String keyword, TokenType tokenType) {
			insert(keyword, tokenType, 0);
		}

		public KeywordTrieNode step(char nextChar) {
			return next.get(nextChar);
		}

		public TokenType getTokenType() {
			return tokenType;
		}
	}
	private InputStream in;
	private ErrorReporter errors;
	private StringBuilder currText;
	private static KeywordTrieNode keywordTrieRoot;
	private char currChar;

	static {
		keywordTrieRoot = new KeywordTrieNode();
		keywordTrieRoot.insert("class", TokenType.Class);
		keywordTrieRoot.insert("this", TokenType.This);
		keywordTrieRoot.insert("new", TokenType.New);
		keywordTrieRoot.insert("if", TokenType.If);
		keywordTrieRoot.insert("else", TokenType.Else);
		keywordTrieRoot.insert("for", TokenType.For);
		keywordTrieRoot.insert("while", TokenType.While);
		keywordTrieRoot.insert("do", TokenType.Do);
		keywordTrieRoot.insert("switch", TokenType.Switch);
		keywordTrieRoot.insert("public", TokenType.Visibility);
		keywordTrieRoot.insert("private", TokenType.Visibility);
		keywordTrieRoot.insert("protected", TokenType.Visibility);
		keywordTrieRoot.insert("default", TokenType.Visibility);
		keywordTrieRoot.insert("static", TokenType.Static);
		keywordTrieRoot.insert("void", TokenType.VoidType);
		keywordTrieRoot.insert("boolean", TokenType.BooleanType);
		keywordTrieRoot.insert("byte", TokenType.ByteType);
		keywordTrieRoot.insert("char", TokenType.CharType);
		keywordTrieRoot.insert("int", TokenType.IntType);
		keywordTrieRoot.insert("long", TokenType.LongType);
		keywordTrieRoot.insert("float", TokenType.FloatType);
		keywordTrieRoot.insert("double", TokenType.DoubleType);
		keywordTrieRoot.insert("return", TokenType.Return);
		keywordTrieRoot.insert("true", TokenType.BooleanLiteral);
		keywordTrieRoot.insert("false", TokenType.BooleanLiteral);
	}
	
	public Scanner( InputStream in, ErrorReporter errors ) {
		this.in = in;
		this.errors = errors;
		currText = new StringBuilder();
		line = 0;
		offset = -1;

		nextChar();
	}
	
	public Token scan() {
		clearCurrText();

		// define states
		enum State {Unknown, Token, TokenEnd, SingleLineComment, MultiLineComment}
		State state = State.Unknown;
		TokenType tokenType = TokenType.End;
		KeywordTrieNode keywordTrieNode = keywordTrieRoot;
		int startLine = line;
		int startOffset = offset;

		// define state transitions
		while (state != State.TokenEnd) {
			switch (state) {
				case Unknown -> {
					startLine = line;
					startOffset = offset;
					switch (currChar) {
						case EOF -> state = State.TokenEnd;
						case '/' -> {
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
								tokenType = TokenType.ArithmeticBinOp;
							}
						}
						case '+', '-', '*' -> {
							takeCurr();
							tokenType = TokenType.ArithmeticBinOp;
							state = State.TokenEnd;
						}
						case '=' -> {
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = TokenType.RelationalBinOp;
							} else tokenType = TokenType.AssignmentOp;
							state = State.TokenEnd;
						}
						case '<', '>' -> {
							takeCurr();
							if (currChar == '=') takeCurr();
							tokenType = TokenType.RelationalBinOp;
							state = State.TokenEnd;
						}
						case '"' -> {
							takeCurr();
							tokenType = TokenType.StringLiteral;
							state = State.Token;
						}
						case '\'' -> {
							takeCurr();
							tokenType = TokenType.CharLiteral;
							state = State.Token;
						}
						case '&', '|' -> {
							char c = currChar;
							takeCurr();
							if (currChar == c) {
								takeCurr();
								tokenType = TokenType.LogicalBinOp;
							} else tokenType = TokenType.BitwiseBinOp;
							state = State.TokenEnd;
						}
						case '!' -> {
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = TokenType.RelationalBinOp;
							} else tokenType = TokenType.LogicalUnOp;
							state = State.TokenEnd;
						}
						case '^' -> {
							takeCurr();
							tokenType = TokenType.BitwiseBinOp;
							state = State.TokenEnd;
						}
						case '~' -> {
							takeCurr();
							tokenType = TokenType.BitwiseUnOp;
							state = State.TokenEnd;
						}
						case ',' -> {
							takeCurr();
							tokenType = TokenType.Comma;
							state = State.TokenEnd;
						}
						case ';' -> {
							takeCurr();
							tokenType = TokenType.Semicolon;
							state = State.TokenEnd;
						}
						case ':' -> {
							takeCurr();
							tokenType = TokenType.Colon;
							state = State.TokenEnd;
						}
						case '.' -> {
							takeCurr();
							tokenType = TokenType.Dot;
							state = State.TokenEnd;
						}
						case '(' -> {
							takeCurr();
							tokenType = TokenType.LParen;
							state = State.TokenEnd;
						}
						case ')' -> {
							takeCurr();
							tokenType = TokenType.RParen;
							state = State.TokenEnd;
						}
						case '[' -> {
							takeCurr();
							tokenType = TokenType.LBracket;
							state = State.TokenEnd;
						}
						case ']' -> {
							takeCurr();
							tokenType = TokenType.RBracket;
							state = State.TokenEnd;
						}
						case '{' -> {
							takeCurr();
							tokenType = TokenType.LCurly;
							state = State.TokenEnd;
						}
						case '}' -> {
							takeCurr();
							tokenType = TokenType.RCurly;
							state = State.TokenEnd;
						}
						default -> {
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
						}
					}
				}
				case SingleLineComment -> {
					if (currIsNewline()) state = State.Unknown;
					skipCurr();
				}
				case MultiLineComment -> {
					if (currText.charAt(0) == '*' && currChar == '/') {
						clearCurrText();
						state = State.Unknown;
					} else currText.setCharAt(0, currChar);
					skipCurr();
				}
				case Token -> {
					if (currIsNewline()) {
						state = State.TokenEnd;
						break;
					}
					boolean backslash = !currText.isEmpty() && currText.charAt(currText.length()-1) == '\\';
					switch (tokenType) {
						case Identifier -> {
							// check for invalid identifier characters (all keywords must also comply with this)
							if (!currIsLetter() && !currIsDigit() && currChar != '_') {
								state = State.TokenEnd;
								break;
							}

							// check for keyword match
							if (keywordTrieNode != null) {
								keywordTrieNode = keywordTrieNode.step(currChar);
								if (keywordTrieNode != null && keywordTrieNode.getTokenType() != null) {
									tokenType = keywordTrieNode.getTokenType();
									state = State.TokenEnd;
								}
							}
							takeCurr();
						}
						case IntLiteral -> {
							if (currIsDigit()) {
								takeCurr();
							} else if (currChar == '.') {
								takeCurr();
								tokenType = TokenType.DoubleLiteral;
							}
							else if (currChar == 'f' || currChar == 'F') {
								skipCurr();
								tokenType = TokenType.FloatLiteral;
								state = State.TokenEnd;
							} else {
								state = State.TokenEnd;
							}
						}
						case DoubleLiteral -> {
							if (currIsDigit()) {
								takeCurr();
							} else if (currChar == 'f' || currChar == 'F') {
								skipCurr();
								tokenType = TokenType.FloatLiteral;
								state = State.TokenEnd;
							} else {
								state = State.TokenEnd;
							}
						}
						case StringLiteral -> {
							if (currIsNewline()) {
								state = State.TokenEnd;
								tokenType = TokenType.IncompleteStringLiteral;
							} if (!backslash && currChar == '"') {
								state = State.TokenEnd;
								takeCurr();
							} else {
								takeCurr();
							}
						}
						case CharLiteral -> {
							if (currIsNewline()) {
								state = State.TokenEnd;
								tokenType = TokenType.IncompleteCharLiteral;
							} if (!backslash && currChar == '\'') {
								state = State.TokenEnd;
								takeCurr();
							} else takeCurr();
						}
						default -> {
							if (currIsWhitespace()) {
								state = State.TokenEnd;
							} else takeCurr();
						}
					}
				}
			}
		}
		if (tokenType != TokenType.End && currText.isEmpty()) {
			errors.reportError(line, offset, String.format("invalid symbol %c", currChar));
			tokenType = TokenType.End;
			currText.append(currChar);
		}
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

	private void clearCurrText() {
		currText.setLength(0);
	}
	
	private void nextChar() {
		try {
			// handle end of file
			if (in.available() == 0) {
				currChar = EOF;
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
