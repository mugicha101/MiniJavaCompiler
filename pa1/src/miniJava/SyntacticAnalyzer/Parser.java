package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

import java.util.*;

/* NOTE: Includes two different approaches
   - stable mode: uses a context free grammar parser to match tokens,
     requires a token history list and keeps track of many different possible paths at once
     which reduces performance, however is easier to show correctness
   - unstable mode: uses a large state machine to match tokens one at a time,
     token history handled by the state machine rather than storing tokens in a list
     and only keeps track of a single state which improves performance, however
     is hard to show correctness
*/

public class Parser {
	private static class UnitTestData {
		public final List<Token> tokenList;
		public final List<String> outputLines;
		private final ErrorReporter errors;
		UnitTestData(ErrorReporter errors) {
			tokenList = new ArrayList<>();
			outputLines = new ArrayList<>();
			this.errors = errors;
		}

		public void insertToken(Token token) {
			tokenList.add(token);
		}

		public void addOutputLine(String line) {
			outputLines.add(line);
		}

		@Override
		public String toString() {
			List<String> output = new ArrayList<>();
			String separator;
			{
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < 16; ++i) sb.append('=');
				separator = sb.toString();
			}
			output.add(separator + " Tokens " + separator);
			for (Token token : tokenList) {
				output.add(String.format("%d:%d %s{%s}", token.getLine(), token.getOffset(), token.getTokenType(), token.getTokenText()));
			}
			output.add(separator + " Debug " + separator);
			output.addAll(outputLines);
			output.add(separator + " Errors " + separator);
			List<String> errorList = errors.getErrors();
			output.addAll(errorList);
			return String.join("\n", output);
		}
	}

	private Scanner scanner;
	private ErrorReporter errors;
	private List<Token> tokenHistory;
	private Token currToken;
	private boolean unitTest;
	private boolean unstableMode;
	private UnitTestData testData;
	
	public Parser(Scanner scanner, ErrorReporter errors, boolean unstableMode) {
		this.unitTest = false;
		this.scanner = scanner;
		this.errors = errors;
		this.unstableMode = unstableMode;
		tokenHistory = new ArrayList<>();
		nextToken();
	}
	public Parser(Scanner scanner, ErrorReporter errors) {
		this(scanner, errors, false);
	}

	public void enableUnitTest() {
		unitTest = true;
		testData = new UnitTestData(errors);
	}

	public String getTestOutput() {
		if (!unitTest) return "unit test data collection inactive";
		return testData.toString();
	}
	
	static class SyntaxError extends Error {
		private static final long serialVersionUID = -6461942006097999362L;
	}

	public void debugPrintTokens() {
		try {
			while (currToken.getTokenType() != TokenType.End) {
				System.out.println(String.format("%d:%d %s %s", currToken.getLine(), currToken.getOffset(), currToken.getTokenType(), currToken.getTokenText()));
				nextToken();
			}
			System.out.println(currToken.getTokenType() + " " + currToken.getTokenText());
		} catch (SyntaxError e) { }
	}
	
	public void parse() {
		try {
			if (unstableMode) parseProgram();
			else parseGrammar();
		} catch( SyntaxError e ) { }
	}

	private class SymbolStackPriorityComparitor implements Comparator<SymbolStack> {
		@Override
		// prioritize stacks with maximal token index first then maximal prod count
		public int compare(SymbolStack s1, SymbolStack s2) {
			return s1.tokenIndex != s2.tokenIndex ? s2.tokenIndex - s1.tokenIndex : s2.prodCount - s1.prodCount;
		}
	}

	private void parseGrammar() throws SyntaxError {
		Comparator<SymbolStack> comparator = new SymbolStackPriorityComparitor();
		Queue<SymbolStack> stateQueue = new PriorityQueue<>(10, comparator);
		stateQueue.add(new SymbolStack(new Symbol[] { Symbol.getSymbol(SymbolType.Program) }, 0, 0));
		HashSet<TokenType> expectedTerminals = new HashSet<>();
		while (!stateQueue.isEmpty()) {
			SymbolStack symbolStack = stateQueue.remove();
			// System.out.println(symbolStack.toString());
			if (symbolStack.stack.length == 0) return; // finished parsing
			while (tokenHistory.get(tokenHistory.size()-1).getTokenType() != TokenType.End
					&& symbolStack.tokenIndex >= tokenHistory.size()) {
				nextToken();
				expectedTerminals.clear();
			}
			if (symbolStack.tokenIndex >= tokenHistory.size())
				continue;
			TokenType nextTerminal = tokenHistory.get(symbolStack.tokenIndex).getTokenType();
			Symbol top = symbolStack.top();
			if (top.isTerminal()) {
				if (top.getTerminalType() == nextTerminal) {
					stateQueue.add(symbolStack.handleTerminal());
				} else {
					expectedTerminals.add(top.getTerminalType());
				}
			} else {
				for (Symbol[] prod : Symbol.getProductions(top.getSymbolType())) {
					stateQueue.add(symbolStack.handleProduction(prod));
				}
			}
		}
		String[] expectedTerminalsStrArr = new String[expectedTerminals.size()];
		int i = 0;
		for (TokenType token : expectedTerminals) {
			expectedTerminalsStrArr[i++] = "{" + token.toString() + "}";
		}
		if (expectedTerminalsStrArr.length > 1) expectedTerminalsStrArr[expectedTerminalsStrArr.length-1] = "or " + expectedTerminalsStrArr[expectedTerminalsStrArr.length-1];
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Unexpected Token: Expected %s, but instead got {%s} matching the following text: \"%s\"", String.join(expectedTerminalsStrArr.length > 2 ? ", " : " ", expectedTerminalsStrArr), currToken.getTokenType(), currToken.getTokenText()));
		throw new SyntaxError();
	}
	
	// Program ::= (ClassDeclaration)* eot
	private void parseProgram() throws SyntaxError {
		while (currToken.getTokenType() != TokenType.End) {
			parseClassDeclaration();
		}
	}
	
	// ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
	// FieldDeclaration ::= Visibility Access Type id ;
	// MethodDeclaration ::= Visibility Access (Type|void) id \( ParameterList? \) { Statement* }
	// Visibility ::= (public|private)?
	private void parseClassDeclaration() throws SyntaxError {
		accept(TokenType.Class, TokenType.Identifier, TokenType.LCurly);
		while (!currTokenMatches(TokenType.RCurly)) {
			optionalAccept(TokenType.Visibility);
			parseAccess();
			boolean method = false;
			if (optionalAccept(TokenType.VoidType)) {
				method = true;
			} else parseType();
			accept(TokenType.Identifier);
			if (!method && optionalAccept(TokenType.Semicolon))
				continue;
			accept(TokenType.LParen);
			if (!currTokenMatches(TokenType.RParen)) parseParameterList();
			accept(TokenType.RParen, TokenType.LCurly);
			while (parseOptionalStatement());
			accept(TokenType.RCurly);
		}
		accept(TokenType.RCurly);
	}

	// Access ::= static?
	private void parseAccess() {
		optionalAccept(TokenType.Static);
	}

	// Type ::= int | boolean | id | (int|id)[]
	private boolean parseOptionalType() {
		if (optionalAccept(TokenType.BooleanType)) return true;
		if (!optionalAccept(TokenType.IntType) && !optionalAccept(TokenType.Identifier)) {
			return false;
		}
		if (optionalAccept(TokenType.LBracket)) accept(TokenType.RBracket);
		return true;
	}
	private void parseType() throws SyntaxError {
		if (parseOptionalType()) return;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected type, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	// ParameterList ::= Type id (, Type id)*
	private void parseParameterList() throws SyntaxError {
		do {
			parseType();
			accept(TokenType.Identifier);
		} while (optionalAccept(TokenType.Comma));
	}

	// ArgumentList ::= Expression (, Expression)*
	private boolean parseOptionalArgumentList() throws SyntaxError {
		if (!parseOptionalExpression()) return false;
		while (optionalAccept(TokenType.Comma)) parseExpression();
		return true;
	}
	private void parseArgumentList() throws SyntaxError {
		if (parseOptionalArgumentList()) return;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected expression, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	// Reference ::= id | this | Reference . id
	private boolean parseOptionalReference() throws SyntaxError {
		if (!optionalAccept(TokenType.Identifier) && !optionalAccept(TokenType.This)) return false;
		while (optionalAccept(TokenType.Dot)) accept(TokenType.Identifier);
		return true;
	}
	private void parseReference() throws SyntaxError {
		if (parseOptionalReference()) return;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected reference, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	// Statement ::=
	//     { Statement* }
	//     | Type id = Expression ;
	//     | Reference = Expression ;
	//     | Reference [ Expression ] = Expression ;
	//     | Reference \( ArgumentList? \) ;
	//     | return Expression? ;
	//     | if \( Expression \) Statement (else Statement)?
	//     | while \( Expression \) Statement
	private boolean parseOptionalStatement() {
		if (optionalAccept(TokenType.LCurly)) {
			while (parseOptionalStatement());
			accept(TokenType.RCurly);
			return true;
		}
		// starting with identifier can result in either type or reference
		if (optionalAccept(TokenType.Identifier)) {
			boolean doAssignment = true;
			if (optionalAccept(TokenType.LBracket)) {
				// type if [], after reference if [expression]
				boolean isRef = parseOptionalExpression();
				accept(TokenType.RBracket);
				if (!isRef) accept(TokenType.Identifier);
			} else if (currTokenMatches(TokenType.Dot)) {
				// reference
				while (optionalAccept(TokenType.Dot)) accept(TokenType.Identifier);
				if (optionalAccept(TokenType.LBracket)) {
					parseExpression();
					accept(TokenType.RBracket);
				} else if (optionalAccept(TokenType.LParen)) {
					doAssignment = false;
					parseOptionalArgumentList();
					accept(TokenType.RParen);
				}
			} else if (optionalAccept(TokenType.LParen)) {
				// reference
				doAssignment = false;
				parseOptionalArgumentList();
				accept(TokenType.RParen);
			} else {
				optionalAccept(TokenType.Identifier);
			}
			if (doAssignment) {
				accept(TokenType.AssignmentOp);
				parseExpression();
			}
			accept(TokenType.Semicolon);
			return true;
		}
		if (parseOptionalType()) {
			accept(TokenType.Identifier, TokenType.AssignmentOp);
			parseExpression();
			accept(TokenType.Semicolon);
			return true;
		}
		if (parseOptionalReference()) {
			if (optionalAccept(TokenType.LBracket)) {
				parseExpression();
				accept(TokenType.RBracket, TokenType.AssignmentOp);
				parseExpression();
			}
			else if (optionalAccept(TokenType.AssignmentOp)) parseExpression();
			else if (optionalAccept(TokenType.LParen)) {
				parseOptionalArgumentList();
				accept(TokenType.RParen);
			} else {
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("expected = or [ or ( after reference, but got %s", currToken.getTokenText()));
				throw new SyntaxError();
			}
			accept(TokenType.Semicolon);
			return true;
		}
		if (optionalAccept(TokenType.Return)) {
			parseOptionalExpression();
			accept(TokenType.Semicolon);
			return true;
		}
		if (optionalAccept(TokenType.If)) {
			accept(TokenType.LParen);
			parseExpression();
			accept(TokenType.RParen);
			parseStatement();
			if (optionalAccept(TokenType.Else)) parseStatement();
			return true;
		}
		if (optionalAccept(TokenType.While)) {
			accept(TokenType.LParen);
			parseExpression();
			accept(TokenType.RParen);
			parseStatement();
			return true;
		}
		return false;
	}
	private void parseStatement() throws SyntaxError {
		if (parseOptionalStatement()) return;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected start of statement, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	// Expression ::=
	//     Reference
	//     | Reference [ Expression ]
	//     | Reference \( ArgumentList? \)
	//     | unop Expression
	//     | Expression binop Expression
	//     | \( Expression \)
	//     | num | true | false
	//     | new ( id\(\) | int [ Expression ] | id [ Expression ] )
	private boolean parseOptionalExpression() throws SyntaxError {
		boolean ret = false;
		if (parseOptionalReference()) {
			if (optionalAccept(TokenType.LBracket)) {
				parseExpression();
				accept(TokenType.RBracket);
			} else if (optionalAccept(TokenType.LParen)) {
				parseOptionalArgumentList();
				accept(TokenType.RParen);
			}
			ret = true;
		} else if (parseOptionalUnop()) {
			parseExpression();
			ret = true;
		} else if (optionalAccept(TokenType.LParen)) {
			parseExpression();
			accept(TokenType.RParen);
			ret = true;
		} else if (parseOptionalNum() || optionalAccept(TokenType.BooleanLiteral)) {
			ret = true;
		} else if (optionalAccept(TokenType.New)) {
			if (optionalAccept(TokenType.Identifier)) {
				if (optionalAccept(TokenType.LParen)) accept(TokenType.RParen);
				else if (optionalAccept(TokenType.LBracket)) {
					parseExpression();
					accept(TokenType.RBracket);
				}
				else {
					errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected ( or [ after new identifier, but got %s", currToken.getTokenText()));
					throw new SyntaxError();
				}
			} else if (optionalAccept(TokenType.IntType)) {
				accept(TokenType.LBracket);
				parseExpression();
				accept(TokenType.RBracket);
			} else {
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected [ after new int, but got %s", currToken.getTokenText()));
				throw new SyntaxError();
			}
			ret = true;
		}
		if (!ret) return false;
		if (parseOptionalBinop()) {
			parseExpression();
		}
		return true;
	}
	private void parseExpression() throws SyntaxError {
		if (parseOptionalExpression()) return;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected start of expression, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	private boolean parseOptionalBinop() {
		TokenType[] binOpTokens = { TokenType.ArithmeticBinOp, TokenType.Minus, TokenType.RelationalBinOp, TokenType.BitwiseBinOp, TokenType.LogicalBinOp };
		for (TokenType token : binOpTokens) {
			if (optionalAccept(token)) return true;
		}
		return false;
	}

	private boolean parseOptionalUnop() {
		TokenType[] unOpTokens = { TokenType.BitwiseUnOp, TokenType.LogicalUnOp, TokenType.Minus };
		for (TokenType token : unOpTokens) {
			if (optionalAccept(token)) return true;
		}
		return false;
	}

	private boolean parseOptionalNum() {
		TokenType[] numTokens = { TokenType.ByteLiteral, TokenType.IntLiteral, TokenType.FloatLiteral, TokenType.DoubleLiteral };
		for (TokenType token : numTokens) {
			if (optionalAccept(token)) return true;
		}
		return false;
	}

	private void nextToken() throws SyntaxError {
		currToken = scanner.scan();
		if (unitTest) testData.tokenList.add(currToken);
		if (!unstableMode) tokenHistory.add(currToken);
		if (currTokenMatches(TokenType.Error)) {
			throw new SyntaxError();
		}
	}

	// This method will accept the token and retrieve the next token.
	//  Can be useful if you want to error check and accept all-in-one.
	private void accept(TokenType... expectedTypes) throws SyntaxError {
		for (TokenType expectedType : expectedTypes) {
			if (currToken.getTokenType() != expectedType) {
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected token %s, but got %s", expectedType, currToken.getTokenText()));
				throw new SyntaxError();
			}
			nextToken();
		}
	}

	private boolean optionalAccept(TokenType type) throws SyntaxError {
		if (currToken.getTokenType() != type) return false;
		nextToken();
		return true;
	}

	private boolean currTokenMatches(TokenType type) {
		return currToken.getTokenType() == type;
	}
}
