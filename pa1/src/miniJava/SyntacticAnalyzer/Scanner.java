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

	private enum State { Unknown, Token, TokenEnd, SingleLineComment, MultiLineComment }
	
	public Token scan() {
		clearCurrText();
		State state = State.Unknown;
		TokenType tokenType = TokenType.End;
		KeywordTrieNode keywordTrieNode = keywordTrieRoot;
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
								tokenType = TokenType.ArithmeticBinOp;
							}
						} break;
						case '+': case '*': {
							takeCurr();
							tokenType = TokenType.ArithmeticBinOp;
							state = State.TokenEnd;
						} break;
						case '-': {
							takeCurr();
							tokenType = TokenType.Minus;
							state = State.TokenEnd;
						} break;
						case '=': {
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = TokenType.RelationalBinOp;
							} else tokenType = TokenType.AssignmentOp;
							state = State.TokenEnd;
						} break;
						case '<': case '>': {
							takeCurr();
							if (currChar == '=') takeCurr();
							tokenType = TokenType.RelationalBinOp;
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
								tokenType = TokenType.LogicalBinOp;
							} else tokenType = TokenType.BitwiseBinOp;
							state = State.TokenEnd;
						} break;
						case '!': {
							takeCurr();
							if (currChar == '=') {
								takeCurr();
								tokenType = TokenType.RelationalBinOp;
							} else tokenType = TokenType.LogicalUnOp;
							state = State.TokenEnd;
						} break;
						case '^': {
							takeCurr();
							tokenType = TokenType.BitwiseBinOp;
							state = State.TokenEnd;
						} break;
						case '~': {
							takeCurr();
							tokenType = TokenType.BitwiseUnOp;
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
					if (currText.charAt(0) == '*' && currChar == '/') {
						clearCurrText();
						state = State.Unknown;
					} else currText.setCharAt(0, currChar);
					skipCurr();
				} break;
				case Token: {
					if (currIsNewline()) {
						state = State.TokenEnd;
						break;
					}
					boolean backslash = currText.length() != 0 && currText.charAt(currText.length()-1) == '\\';
					switch (tokenType) {
						case Identifier: {
							// check for invalid identifier characters (all keywords must also comply with this)
							if (!currIsLetter() && !currIsDigit() && currChar != '_') {
								// check for keyword match
								if (keywordTrieNode != null && keywordTrieNode.getTokenType() != null)
									tokenType = keywordTrieNode.getTokenType();
								state = State.TokenEnd;
								break;
							}

							// update keyword trie
							if (keywordTrieNode != null) keywordTrieNode = keywordTrieNode.step(currChar);
							takeCurr();
						} break;
						case IntLiteral: {
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
						} break;
						case DoubleLiteral: {
							if (currIsDigit()) {
								takeCurr();
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
								state = State.TokenEnd;
								tokenType = TokenType.IncompleteStringLiteral;
							} if (!backslash && currChar == '"') {
								state = State.TokenEnd;
								takeCurr();
							} else {
								takeCurr();
							}
						} break;
						case CharLiteral: {
							if (currIsNewline()) {
								state = State.TokenEnd;
								tokenType = TokenType.IncompleteCharLiteral;
							} if (!backslash && currChar == '\'') {
								state = State.TokenEnd;
								takeCurr();
							} else takeCurr();
						} break;
						default: {
							if (currIsWhitespace()) {
								state = State.TokenEnd;
							} else takeCurr();
						} break;
					}
				} break;
			}
		}
		if (tokenType != TokenType.End && currText.length() == 0) {
			errors.reportError(line, offset, String.format("Invalid symbol %c", currChar));
			tokenType = TokenType.Error;
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
