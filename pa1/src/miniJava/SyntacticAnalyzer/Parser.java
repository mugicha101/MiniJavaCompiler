package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

public class Parser {
	private Scanner scanner;
	private ErrorReporter errors;
	private Token currToken;
	
	public Parser( Scanner scanner, ErrorReporter errors ) {
		this.scanner = scanner;
		this.errors = errors;
		this.currToken = this.scanner.scan();
	}
	
	class SyntaxError extends Error {
		private static final long serialVersionUID = -6461942006097999362L;
	}

	public void debugPrintTokens() {
		while (currToken.getTokenType() != TokenType.End) {
			System.out.println(String.format("%d:%d %s %s", currToken.getLine(), currToken.getOffset(), currToken.getTokenType(), currToken.getTokenText()));
			currToken = scanner.scan();
		}
		System.out.println(currToken.getTokenType() + " " + currToken.getTokenText());
	}
	
	public void parse() {
		try {
			// The first thing we need to parse is the Program symbol
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
			parseVisibility();
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

	// Visibility ::= (public|private)?
	private void parseVisibility() throws SyntaxError {
		TokenType[] visibilityTokens = { TokenType.Public, TokenType.Private };
		for (TokenType token : visibilityTokens) {
			if (optionalAccept(token)) return;
		}
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected visibility, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
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
			accept(TokenType.Equals);
			parseExpression();
			accept(TokenType.Semicolon);
			return true;
		}
		if (parseOptionalType()) {
			accept(TokenType.Identifier, TokenType.Equals);
			parseExpression();
			accept(TokenType.Semicolon);
			return true;
		}
		if (parseOptionalReference()) {
			if (optionalAccept(TokenType.LBracket)) {
				parseExpression();
				accept(TokenType.RBracket, TokenType.Equals);
				parseExpression();
			}
			else if (optionalAccept(TokenType.Equals)) parseExpression();
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
		TokenType[] binopTokens = {
				TokenType.Plus, TokenType.Minus, TokenType.Star, TokenType.FSlash,
				TokenType.DoubleEquals, TokenType.NotEquals, TokenType.LessThan,  TokenType.GreaterThan, TokenType.LessOrEquals, TokenType.GreaterOrEquals,
				TokenType.And, TokenType.Or, TokenType.Caret,
				TokenType.DoubleAnd, TokenType.DoubleOr
		};
		for (TokenType token : binopTokens) {
			if (optionalAccept(token)) return true;
		}
		return false;
	}

	private boolean parseOptionalUnop() {
		TokenType[] unopTokens = { TokenType.Not, TokenType.Tilde, TokenType.Minus };
		for (TokenType token : unopTokens) {
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
		currToken = scanner.scan();
		return true;
	}

	private boolean currTokenMatches(TokenType type) {
		return currToken.getTokenType() == type;
	}
}