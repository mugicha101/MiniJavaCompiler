package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

import java.util.ArrayList;
import java.util.List;

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
	private Token currToken;
	private boolean unitTest;
	private UnitTestData testData;
	
	public Parser(Scanner scanner, ErrorReporter errors) {
		this.unitTest = false;
		this.scanner = scanner;
		this.errors = errors;
		nextToken();
	}

	public void enableUnitTest() {
		unitTest = true;
		testData = new UnitTestData(errors);
	}

	public String getTestOutput() {
		if (!unitTest) return "unit test data collection inactive";
		return testData.toString();
	}
	
	class SyntaxError extends Error {
		private static final long serialVersionUID = -6461942006097999362L;
	}

	public void debugPrintTokens() {
		while (currToken.getTokenType() != TokenType.End) {
			System.out.println(String.format("%d:%d %s %s", currToken.getLine(), currToken.getOffset(), currToken.getTokenType(), currToken.getTokenText()));
			nextToken();
		}
		System.out.println(currToken.getTokenType() + " " + currToken.getTokenText());
	}
	
	public void parse() {
		try {
			parseProgram();
		} catch( SyntaxError e ) { }
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
	private void parseClassDeclaration() throws SyntaxError {
		accept(TokenType.Class, TokenType.Identifier, TokenType.LCurly);
		while (!currTokenMatches(TokenType.RCurly)) {
			accept(TokenType.Visibility);
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
	// { Statement* }
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
			if (optionalAccept(TokenType.LBracket)) {
				// type if [], after reference if [expression]
				parseOptionalExpression();
				accept(TokenType.RBracket);
			} else if (currTokenMatches(TokenType.Dot)) {
				// reference
				while (optionalAccept(TokenType.Dot)) accept(TokenType.Identifier);
				if (optionalAccept(TokenType.LBracket)) {
					parseExpression();
					accept(TokenType.RBracket);
				}
			} else {
				optionalAccept(TokenType.Identifier);
			}
			accept(TokenType.AssignmentOp);
			parseExpression();
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
				else if (optionalAccept(TokenType.LBracket)) accept(TokenType.RBracket);
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

	private void nextToken() {
		currToken = scanner.scan();
		if (unitTest) testData.tokenList.add(currToken);
	}

	// This method will accept the token and retrieve the next token.
	//  Can be useful if you want to error check and accept all-in-one.
	private void accept(TokenType... expectedTypes) throws SyntaxError {
		for (TokenType expectedType : expectedTypes) {
			if (currToken.getTokenType() != expectedType) {
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected token %s, but got %s", expectedType, currToken.getTokenText()));
				throw new SyntaxError();
			}
			currToken = scanner.scan();
		}
	}

	private boolean optionalAccept(TokenType type) {
		if (currToken.getTokenType() != type) return false;
		nextToken();
		return true;
	}

	private boolean currTokenMatches(TokenType type) {
		return currToken.getTokenType() == type;
	}
}
