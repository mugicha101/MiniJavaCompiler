package miniJava.SyntacticAnalyzer;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;

import javax.xml.transform.Source;
import java.util.*;

/* NOTE: Includes two different approaches
   - push-down mode (default): uses a push-down automata to match tokens
   - recursive descent mode: uses recursive descent to match tokens
*/

public class Parser {
	public static enum Mode { PushDown, RecursiveDescent }
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
	private Mode mode;
	private UnitTestData testData;

	public Parser(Scanner scanner, ErrorReporter errors, Mode mode) {
		this.unitTest = false;
		this.scanner = scanner;
		this.errors = errors;
		this.mode = mode;
	}
	public Parser(Scanner scanner, ErrorReporter errors) {
		this(scanner, errors, Mode.RecursiveDescent);
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

	public AST parse() {
		try {
			switch (mode) {
				case PushDown: parseGrammar(); return null;
				case RecursiveDescent: return parseProgram();
			}
		} catch( SyntaxError e ) { }
		return null;
	}

	private void applyProductions(Queue<SymbolStack> stateQueue, List<TokenType> expectedTerminals, SymbolStack state) {
		Symbol top = state.top();
		if (top.isTerminal()) {
			if (top.getTerminalType() == currToken.getTokenType()) {
				stateQueue.add(state.handleTerminal());
			} else {
				expectedTerminals.add(top.getTerminalType());
			}
			return;
		}
		for (Symbol[] prod : Symbol.getProductions(top.getSymbolType())) {
			applyProductions(stateQueue, expectedTerminals, state.handleProduction(prod));
		}
	}

	private void parseGrammar() throws SyntaxError {
		Queue<SymbolStack> stateQueue = new LinkedList<>();
		stateQueue.add(new SymbolStack(new Symbol[] { Symbol.getSymbol(SymbolType.Program) }, 0, 0));
		List<TokenType> expectedTerminals = new ArrayList<>();
		while (!currTokenMatches(TokenType.End)) {
			nextToken();
			if (stateQueue.isEmpty()) {
				Collections.sort(expectedTerminals);
				String[] expectedTerminalsStrArr = new String[expectedTerminals.size()];
				int i = 0;
				for (TokenType token : expectedTerminals) {
					expectedTerminalsStrArr[i++] = "{" + token.toString() + "}";
				}
				if (expectedTerminalsStrArr.length > 1) expectedTerminalsStrArr[expectedTerminalsStrArr.length-1] = "or " + expectedTerminalsStrArr[expectedTerminalsStrArr.length-1];
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Unexpected Token: Expected %s, but instead got {%s} matching the following text: \"%s\"", String.join(expectedTerminalsStrArr.length > 2 ? ", " : " ", expectedTerminalsStrArr), currToken.getTokenType(), currToken.getTokenText()));
				throw new SyntaxError();
			}
			expectedTerminals.clear();
			for (int qi = stateQueue.size(); qi > 0; qi--) {
				applyProductions(stateQueue, expectedTerminals, stateQueue.poll());
			}
		}
		// check state queue for valid ending
		boolean foundEnd = false;
		for (SymbolStack symbolStack : stateQueue) {
			if (symbolStack.stack.length == 1 && symbolStack.top().isTerminal() && symbolStack.top().getTerminalType() == TokenType.ParseEnd)
				foundEnd = true;
		}
		if (!foundEnd) {
			errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Unexpected End of File"));
			throw new SyntaxError();
		}
	}

	// Program ::= (ClassDeclaration)* eot
	private Package parseProgram() throws SyntaxError {
		nextToken();
		Package astPackage = new Package(new ClassDeclList(), currToken.getTokenPosition());
		while (currToken.getTokenType() != TokenType.End) {
			astPackage.classDeclList.add(parseClassDeclaration());
		}
		return astPackage;
	}

	// ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
	// FieldDeclaration ::= Visibility Access Type id ;
	// MethodDeclaration ::= Visibility Access (Type|void) id \( ParameterList? \) { Statement* }
	// Visibility ::= (public|private)?
	// Access ::= static?
	private ClassDecl parseClassDeclaration() throws SyntaxError {
		ClassDecl classDecl = new ClassDecl("", new FieldDeclList(), new MethodDeclList(), currToken.getTokenPosition());
		accept(TokenType.Class);
		classDecl.name = currToken.getTokenText();
		accept(TokenType.Identifier, TokenType.LCurly);
		while (!currTokenMatches(TokenType.RCurly)) {
			// either method or field
			FieldDecl fieldDecl = new FieldDecl(false, false, null, null, currToken.getTokenPosition());
			fieldDecl.isPrivate = currToken.getTokenText().equals("private") & optionalAccept(TokenType.Visibility);
			fieldDecl.isStatic = optionalAccept(TokenType.Static);
			boolean method = false;
			TypeDenoter typeDenoter;
			if (currTokenMatches(TokenType.VoidType)) {
				fieldDecl.type = new BaseType(TypeKind.VOID, currToken.getTokenPosition());
				nextToken();
				method = true;
			} else fieldDecl.type = parseType();
			fieldDecl.name = currToken.getTokenText();
			accept(TokenType.Identifier);
			if (!method && optionalAccept(TokenType.Semicolon)) {
				classDecl.fieldDeclList.add(fieldDecl);
				continue;
			}

			// is a method
			MethodDecl methodDecl = new MethodDecl(fieldDecl, new ParameterDeclList(), new StatementList(), fieldDecl.posn);
			accept(TokenType.LParen);
			if (!currTokenMatches(TokenType.RParen)) methodDecl.parameterDeclList = parseParameterList();
			accept(TokenType.RParen, TokenType.LCurly);
			Statement statement = parseOptionalStatement();
			while (statement != null) {
				methodDecl.statementList.add(statement);
				statement = parseOptionalStatement();
			}
			accept(TokenType.RCurly);
			classDecl.methodDeclList.add(methodDecl);
		}
		accept(TokenType.RCurly);
		return classDecl;
	}

	// Type ::= (int | boolean | ... | id)([])?
	private TypeDenoter parseOptionalType() {
		// get type
		SourcePosition typePosition = currToken.getTokenPosition();
		TypeKind type = null;
		switch (currToken.getTokenType()) {
			case BooleanType:
				type = TypeKind.BOOLEAN;
				break;
			case IntType:
				type = TypeKind.INT;
				break;
			case LongType:
				type = TypeKind.LONG;
				break;
			case FloatType:
				type = TypeKind.FLOAT;
				break;
			case DoubleType:
				type = TypeKind.DOUBLE;
				break;
			case CharType:
				type = TypeKind.CHAR;
				break;
		}
		TypeDenoter typeDenoter;
		if (type != null) {
			// base type
			typeDenoter = new BaseType(type, typePosition);
		} else if (currTokenMatches(TokenType.Identifier)) {
			// class type
			typeDenoter = new ClassType(new Identifier(currToken), typePosition);
		} else return null; // unknown type
		nextToken();

		// handle array types
		if (optionalAccept(TokenType.LBracket)) {
			typeDenoter = new ArrayType(typeDenoter, typePosition);
			accept(TokenType.RBracket);
		}
		return typeDenoter;
	}

	private TypeDenoter parseType() throws SyntaxError {
		TypeDenoter typeDenoter = parseOptionalType();
		if (typeDenoter != null) return typeDenoter;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected type, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	// ParameterList ::= Type id (, Type id)*
	private ParameterDeclList parseParameterList() throws SyntaxError {
		ParameterDeclList parameterDeclList = new ParameterDeclList();
		do {
			ParameterDecl parameterDecl = new ParameterDecl(null, null, currToken.getTokenPosition());
			parameterDecl.type = parseType();
			parameterDecl.name = currToken.getTokenText();
			accept(TokenType.Identifier);
			parameterDeclList.add(parameterDecl);
		} while (optionalAccept(TokenType.Comma));
		return parameterDeclList;
	}

	// ArgumentList ::= Expression (, Expression)*
	private ExprList parseOptionalArgumentList() throws SyntaxError {
		ExprList argList = new ExprList();
		Expression expr = parseOptionalExpression();
		if (expr == null) return null;
		argList.add(expr);
		while (optionalAccept(TokenType.Comma)) {
			argList.add(parseExpression());
		}
		return argList;
	}
	private ExprList parseArgumentList() throws SyntaxError {
		ExprList argList = parseOptionalArgumentList();
		if (argList != null) return argList;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected expression, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	// Reference ::= id | this | Reference . id
	private Reference parseOptionalReference() throws SyntaxError {
		Reference ref;
		if (currTokenMatches(TokenType.Identifier))
			ref = new IdRef(new Identifier(currToken), currToken.getTokenPosition());
		else if (currTokenMatches(TokenType.This))
			ref = new ThisRef(currToken.getTokenPosition());
		else return null;
		nextToken();
		while (optionalAccept(TokenType.Dot)) {
			ref = new QualRef(ref, new Identifier(currToken), ref.posn);
			accept(TokenType.Identifier);
		}
		return ref;
	}
	private Reference parseReference() throws SyntaxError {
		Reference ref = parseOptionalReference();
		if (ref != null) return ref;
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
	private Statement parseOptionalStatement(boolean isShort) {
		SourcePosition stmtPos = currToken.getTokenPosition();
		if (!isShort && optionalAccept(TokenType.LCurly)) {
			StatementList stmtList = new StatementList();
			Statement nestedStmt = parseOptionalStatement();
			while (nestedStmt != null) {
				stmtList.add(nestedStmt);
				nestedStmt = parseOptionalStatement();
			}
			accept(TokenType.RCurly);
			return new BlockStmt(stmtList, stmtPos);
		}
		// starting with identifier can result in either type or reference
		if (currTokenMatches(TokenType.Identifier)) {
			Token id = currToken;
			nextToken();
			if (optionalAccept(TokenType.LBracket)) {
				// type if [ ], after reference if [ expr ]
				Expression ixExpr = parseOptionalExpression();
				accept(TokenType.RBracket);
				if (ixExpr == null) {
					// id [ ] id = expr ;
					TypeDenoter type = new ArrayType(new ClassType(new Identifier(id), id.getTokenPosition()), id.getTokenPosition());
					Token varName = currToken;
					accept(TokenType.Identifier);
					accept(TokenType.AssignmentOp);
					Expression assignExpr = parseExpression();
					accept(TokenType.Semicolon);
					return new VarDeclStmt(new VarDecl(type, varName.getTokenText(), varName.getTokenPosition()), assignExpr, stmtPos);
				}
				// id [ expr ] = expr ;
				accept(TokenType.AssignmentOp);
				Expression assignExpr = parseExpression();
				if (!isShort) accept(TokenType.Semicolon);
				return new IxAssignStmt(new IdRef(new Identifier(id), id.getTokenPosition()), ixExpr, assignExpr, stmtPos);
			} else if (currTokenMatches(TokenType.Dot)) {
				// ref = id (. ref)+
				Reference ref = new IdRef(new Identifier(id), id.getTokenPosition());
				while (optionalAccept(TokenType.Dot)) {
					ref = new QualRef(ref, new Identifier(currToken), ref.posn);
					accept(TokenType.Identifier);
				}
				if (optionalAccept(TokenType.LBracket)) {
					// id (. id)+ [ expr ] = expr ;
					Expression ixExpr = parseExpression();
					accept(TokenType.RBracket);
					accept(TokenType.AssignmentOp);
					Expression assignExpr = parseExpression();
					if (!isShort) accept(TokenType.Semicolon);
					return new IxAssignStmt(ref, ixExpr, assignExpr, stmtPos);
				}
				if (optionalAccept(TokenType.LParen)) {
					// id (. id)+ ( argList? ) ;
					ExprList argList = parseOptionalArgumentList();
					if (argList == null) argList = new ExprList();
					accept(TokenType.RParen);
					if (!isShort) accept(TokenType.Semicolon);
					return new CallStmt(ref, argList, stmtPos);
				}
				// id (. id)+ = expr ;
				accept(TokenType.AssignmentOp);
				Expression assignExpr = parseExpression();
				if (!isShort) accept(TokenType.Semicolon);
				return new AssignStmt(ref, assignExpr, stmtPos);
			} else if (optionalAccept(TokenType.LParen)) {
				// id ( argList ) ;
				Reference ref = new IdRef(new Identifier(id), id.getTokenPosition());
				ExprList argList = parseOptionalArgumentList();
				if (argList == null) argList = new ExprList();
				accept(TokenType.RParen);
				if (!isShort) accept(TokenType.Semicolon);
				return new CallStmt(ref, argList, stmtPos);
			}
			// id id? = expr ;
			Token id2 = null;
			if (currTokenMatches(TokenType.Identifier)) {
				id2 = currToken;
				nextToken();
			}
			accept(TokenType.AssignmentOp);
			Expression assignExpr = parseExpression();
			if (!isShort) accept(TokenType.Semicolon);
			if (id2 == null)
				return new AssignStmt(new IdRef(new Identifier(id), id.getTokenPosition()), assignExpr, stmtPos);
			return new VarDeclStmt(new VarDecl(new ClassType(new Identifier(id), id.getTokenPosition()), id2.getTokenText(), id.getTokenPosition()), assignExpr, stmtPos);
		}
		TypeDenoter type = parseOptionalType();
		if (type != null) {
			// type id = expr ;
			Token id = currToken;
			accept(TokenType.Identifier, TokenType.AssignmentOp);
			Expression assignExpr = parseExpression();
			if (!isShort) accept(TokenType.Semicolon);
			return new VarDeclStmt(new VarDecl(type, id.getTokenText(), id.getTokenPosition()), assignExpr, stmtPos);
		}
		Reference ref = parseOptionalReference();
		if (ref != null) {
			// ref ...
			if (optionalAccept(TokenType.LBracket)) {
				// ref [ expr ] = expr ;
				Expression ixExpr = parseExpression();
				accept(TokenType.RBracket, TokenType.AssignmentOp);
				Expression assignExpr = parseExpression();
				if (!isShort) accept(TokenType.Semicolon);
				return new IxAssignStmt(ref, ixExpr, assignExpr, stmtPos);
			} else if (optionalAccept(TokenType.AssignmentOp)) {
				// ref = expr ;
				Expression assignExpr = parseExpression();
				if (!isShort) accept(TokenType.Semicolon);
				return new AssignStmt(ref, assignExpr, stmtPos);
			} else if (optionalAccept(TokenType.LParen)) {
				// ref ( argList? ) ;
				ExprList argList = parseOptionalArgumentList();
				if (argList == null) argList = new ExprList();
				accept(TokenType.RParen);
				if (!isShort) accept(TokenType.Semicolon);
				return new CallStmt(ref, argList, stmtPos);
			}
			errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("expected = or [ or ( after reference, but got %s", currToken.getTokenText()));
			throw new SyntaxError();
		}
		if (optionalAccept(TokenType.Return)) {
			// return expr? ;
			Expression expr = parseOptionalExpression();
			if (!isShort) accept(TokenType.Semicolon);
			return new ReturnStmt(expr, stmtPos);
		}
		if (isShort) return null;

		if (optionalAccept(TokenType.If)) {
			// if ( expr ) stmt (else stmt)?
			accept(TokenType.LParen);
			Expression condExpr = parseExpression();
			accept(TokenType.RParen);
			Statement ifBodyStmt = parseStatement();
			if (optionalAccept(TokenType.Else)) {
				Statement elseBodyStmt = parseStatement();
				return new IfStmt(condExpr, ifBodyStmt, elseBodyStmt, stmtPos);
			}
			return new IfStmt(condExpr, ifBodyStmt, stmtPos);
		}
		if (optionalAccept(TokenType.While)) {
			// while ( expr ) stmt
			accept(TokenType.LParen);
			Expression condExpr = parseExpression();
			accept(TokenType.RParen);
			Statement whileBodyStmt = parseStatement();
			return new WhileStmt(condExpr, whileBodyStmt, stmtPos);
		}
		if (optionalAccept(TokenType.For)) {
			// for ( shortstmt? ; expr? ; shortstmt? ) stmt
			accept(TokenType.LParen);
			Statement initStmt = parseOptionalStatement(true);
			accept(TokenType.Semicolon);
			Expression condExpr = parseOptionalExpression();
			accept(TokenType.Semicolon);
			Statement incrStmt = parseOptionalStatement(true);
			accept(TokenType.RParen);
			Statement body = parseStatement();
			return new ForStmt(initStmt, condExpr, incrStmt, body, stmtPos);
		}
		return null;
	}
	private Statement parseStatement(boolean isShort) throws SyntaxError {
		Statement stmt = parseOptionalStatement(isShort);
		if (stmt != null) return stmt;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected start of statement, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	private Statement parseOptionalStatement() throws SyntaxError {
		return parseOptionalStatement(false);
	}

	private Statement parseStatement() throws SyntaxError {
		return parseStatement(false);
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
	private Expression parseOptionalExpressionTerm() throws SyntaxError {
		SourcePosition exprPos = currToken.getTokenPosition();
		Token startToken = currToken;
		Reference ref = parseOptionalReference();
		if (ref != null) {
			// ref ...
			if (optionalAccept(TokenType.LBracket)) {
				// ref [ expr ]
				Expression ixExpr = parseExpression();
				accept(TokenType.RBracket);
				return new IxExpr(ref, ixExpr, exprPos);
			} else if (optionalAccept(TokenType.LParen)) {
				// ref ( argList? )
				ExprList argList = parseOptionalArgumentList();
				if (argList == null) argList = new ExprList();
				accept(TokenType.RParen);
				return new CallExpr(ref, argList, exprPos);
			}
			// ref
			return new RefExpr(ref, exprPos);
		} else if (parseOptionalUnOp() != null) {
			// unop expr
			Expression nestedExpr = parseOptionalExpressionTerm();
			if (nestedExpr == null) {
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected expression start after unary operator, but got %s", currToken.getTokenText()));
				throw new SyntaxError();
			}
			return new UnaryExpr(new Operator(startToken), nestedExpr, exprPos);
		} else if (optionalAccept(TokenType.LParen)) {
			Expression expr = parseOptionalExpression();
			if (expr != null) {
				// ( type ) expr | ( expr ) needs to be resolved outside
				// for now assume ( expr )
				accept(TokenType.RParen);
				return expr;
			} else {
				// ( type ) expr
				TypeDenoter type = parseType();
				accept(TokenType.RParen);
				expr = parseExpressionTerm();
				return new CastExpr(type, expr, exprPos);
			}
		} else if (parseOptionalNum()) {
			// num
			Terminal terminal;
			switch (startToken.getTokenType()) {
				case IntLiteral:
					terminal = new IntLiteral(startToken);
					break;
				case LongLiteral:
					terminal = new LongLiteral(startToken);
					break;
				case FloatLiteral:
					terminal = new FloatLiteral(startToken);
					break;
				case DoubleLiteral:
					terminal = new DoubleLiteral(startToken);
					break;
				case CharLiteral:
					terminal = new CharLiteral(startToken);
					break;
				default:
					throw new RuntimeException(String.format("unknown num type %s", startToken.getTokenType()));
			}
			return new LiteralExpr(terminal, exprPos);
		} else if (optionalAccept(TokenType.BooleanLiteral)) {
			// true | false
			return new LiteralExpr(new BooleanLiteral(startToken), exprPos);
		} else if (optionalAccept(TokenType.NullLiteral)) {
			// null
			return new LiteralExpr(new NullLiteral(startToken), exprPos);
		} else if (optionalAccept(TokenType.New)) {
			// new ...
			if (currTokenMatches(TokenType.Identifier)) {
				ClassType type = new ClassType(new Identifier(currToken), currToken.getTokenPosition());
				nextToken();
				// new id ...
				if (optionalAccept(TokenType.LParen)) {
					// new id \( \)
					accept(TokenType.RParen);
					return new NewObjectExpr(type, exprPos);
				} else if (optionalAccept(TokenType.LBracket)) {
					// new id [ expr ]
					Expression sizeExpr = parseExpression();
					accept(TokenType.RBracket);
					return new NewArrayExpr(type, sizeExpr, exprPos);
				} else {
					errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected ( or [ after new identifier, but got %s", currToken.getTokenText()));
					throw new SyntaxError();
				}
			} else {
				// num [ expr ]
				TypeKind typeKind = null;
				switch (currToken.getTokenType()) {
					case CharType:
						typeKind = TypeKind.CHAR;
						break;
					case IntType:
						typeKind = TypeKind.INT;
						break;
					case LongType:
						typeKind = TypeKind.LONG;
						break;
					case FloatType:
						typeKind = TypeKind.FLOAT;
						break;
					case DoubleType:
						typeKind = TypeKind.DOUBLE;
						break;
					default:
						errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected type after new, but got %s", currToken.getTokenText()));
						throw new SyntaxError();
				}
				BaseType type = new BaseType(typeKind, currToken.getTokenPosition());
				nextToken();
				accept(TokenType.LBracket);
				Expression sizeExpr = parseExpression();
				accept(TokenType.RBracket);
				return new NewArrayExpr(type, sizeExpr, exprPos);
			}
		}
		return null;
	}

	private Expression parseExpressionTerm() throws SyntaxError {
		Expression expr = parseOptionalExpressionTerm();
		if (expr != null) return expr;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected start of expression term, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	private Expression parseOptionalExpression() throws SyntaxError {
		// doubly linked list of single term expressions
		class Term {
			public Expression expr;
			public int prev; // points to last non-null index in chain (-1 if none)
			public int next; // points to next non-null index in chain (binOps.size() if none)
			public Term(Expression expr, int prev, int next) {
				this.expr = expr;
				this.prev = prev;
				this.next = next;
			}
		}

		List<Term> termChain = new ArrayList<>();
		List<Operator> binOps = new ArrayList<>();
		boolean chainHasNext = true;
		while (chainHasNext) {
			Expression term = parseOptionalExpressionTerm();
			if (term == null) {
				if (termChain.isEmpty()) return null;
				errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected start of an expression following a binary operator, but got %s", currToken.getTokenText()));
				throw new SyntaxError();
			}
			Operator operator = parseOptionalBinOp();
			if (operator == null) {
				Expression castExpr;
				if (
						term instanceof RefExpr
						&& ((RefExpr)term).ref instanceof IdRef
						&& (castExpr = parseOptionalExpressionTerm()) != null
				) {
					// retroactively try to change ( expr ) to ( type ) exprTerm
					Identifier id = ((IdRef)((RefExpr)term).ref).id;
					term = new CastExpr(new ClassType(id, id.posn), castExpr, term.posn);
					operator = parseOptionalBinOp();
					chainHasNext = operator != null;
				} else {
					chainHasNext = false;
				}
			}
			else binOps.add(operator);
			termChain.add(new Term(term, termChain.size() - 1, termChain.size() + 1));
		}

		// apply operator precedence of expr (binop expr)* chain
		// done in O(N) where N = number of operators

		// bucket sort binOps by precedence levels
		List<Integer>[] precedenceLevels = new ArrayList[Operator.precedenceLevelCount];
		for (int p = 0; p < Operator.precedenceLevelCount; ++p)
				precedenceLevels[p] = new ArrayList<>();
		for (int i = 0; i < binOps.size(); ++i) {
			precedenceLevels[Operator.binOpPrecedence.get(binOps.get(i).kind)].add(i);
		}

		// handle binOps
		for (int p = 0; p < Operator.precedenceLevelCount; ++p) {
			for (int opIndex : precedenceLevels[p]) {
				// get indexes
				// merges to the left, so right index for current operator remains constant
				// left index maintained by right element
				int rightIndex = opIndex + 1;
				int leftIndex = termChain.get(rightIndex).prev;

				// merge terms and update doubly linked list
				Term leftTerm = termChain.get(leftIndex);
				Term rightTerm = termChain.get(rightIndex);
				Operator op = binOps.get(opIndex);
				leftTerm.expr = new BinaryExpr(op, leftTerm.expr, rightTerm.expr, leftTerm.expr.posn);

				// remove right term from the linked list
				// since each op maps to a unique right term, will never be used again
				leftTerm.next = rightTerm.next;
				if (rightTerm.next != termChain.size()) {
					Term rightRightTerm = termChain.get(rightTerm.next);
					rightRightTerm.prev = leftIndex;
				}
			}
		}

		// since merging left, first term is the result
		return termChain.get(0).expr;
	}
	private Expression parseExpression() throws SyntaxError {
		Expression expr = parseOptionalExpression();
		if (expr != null) return expr;
		errors.reportError(currToken.getLine(), currToken.getOffset(), String.format("Expected start of expression, but got %s", currToken.getTokenText()));
		throw new SyntaxError();
	}

	private Operator parseOptionalBinOp() {
		TokenType[] binOpTokens = {
				TokenType.Add, TokenType.Minus, TokenType.Multiply, TokenType.Divide,
				TokenType.RelLT, TokenType.RelGT, TokenType.RelLEq, TokenType.RelGEq, TokenType.RelEq, TokenType.RelNEq,
				TokenType.BitAnd, TokenType.BitXor, TokenType.BitOr, TokenType.LogAnd, TokenType.LogOr
		};
		for (TokenType token : binOpTokens) {
			if (currTokenMatches(token)) {
				Operator op = new Operator(currToken);
				nextToken();
				return op;
			}
		}
		return null;
	}

	private Operator parseOptionalUnOp() {
		TokenType[] unOpTokens = { TokenType.Minus, TokenType.BitComp, TokenType.LogNot };
		for (TokenType token : unOpTokens) {
			if (currTokenMatches(token)) {
				Operator op = new Operator(currToken);
				nextToken();
				return op;
			}
		}
		return null;
	}

	private boolean parseOptionalNum() {
		TokenType[] numTokens = { TokenType.ByteLiteral, TokenType.IntLiteral, TokenType.LongLiteral, TokenType.FloatLiteral, TokenType.DoubleLiteral, TokenType.CharLiteral };
		for (TokenType token : numTokens) {
			if (optionalAccept(token)) return true;
		}
		return false;
	}

	private void nextToken() throws SyntaxError {
		currToken = scanner.scan();
		if (unitTest) testData.tokenList.add(currToken);
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
		return currToken != null && currToken.getTokenType() == type;
	}
}