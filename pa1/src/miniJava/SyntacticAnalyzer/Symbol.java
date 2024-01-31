package miniJava.SyntacticAnalyzer;

import java.util.HashMap;

public class Symbol {
    private static HashMap<SymbolType, Symbol> nonTerminalSymbols = new HashMap<>();
    private static HashMap<TokenType, Symbol> terminalSymbols = new HashMap<>();
    public static HashMap<SymbolType, Symbol[][]> productions = new HashMap<>();

    public static Symbol getSymbol(TokenType terminalType) {
        return terminalSymbols.get(terminalType);
    }

    public static Symbol getSymbol(SymbolType symbolType) {
        return nonTerminalSymbols.get(symbolType);
    }

    public static Symbol[][] getProductions(SymbolType consumedSymbol) {
        return productions.containsKey(consumedSymbol) ? productions.get(consumedSymbol) : new Symbol[][] {};
    }

    static {
        for (SymbolType symbolType : SymbolType.values()) {
            nonTerminalSymbols.put(symbolType, new Symbol(symbolType));
        }
        for (TokenType tokenType : TokenType.values()) {
            terminalSymbols.put(tokenType, new Symbol(tokenType));
        }
        productions.put(SymbolType.Program, new Symbol[][] {
                { getSymbol(SymbolType.ClassDeclaration), getSymbol(SymbolType.Program) },
                { getSymbol(TokenType.End), getSymbol(TokenType.ParseEnd) }
        });
        productions.put(SymbolType.ClassDeclaration, new Symbol[][] {
                { getSymbol(TokenType.Class), getSymbol(TokenType.Identifier), getSymbol(TokenType.LCurly), getSymbol(SymbolType.MemberDeclarations), getSymbol(TokenType.RCurly) }
        });
        productions.put(SymbolType.MemberDeclarations, new Symbol[][] {
                { getSymbol(SymbolType.FieldDeclaration), getSymbol(SymbolType.MemberDeclarations) },
                { getSymbol(SymbolType.MethodDeclaration), getSymbol(SymbolType.MemberDeclarations) },
                {  }
        });
        productions.put(SymbolType.FieldDeclaration, new Symbol[][] {
                { getSymbol(SymbolType.OptionalVisibility), getSymbol(SymbolType.Access), getSymbol(SymbolType.Type), getSymbol(TokenType.Identifier), getSymbol(TokenType.Semicolon) }
        });
        productions.put(SymbolType.MethodDeclaration, new Symbol[][] {
                { getSymbol(SymbolType.OptionalVisibility), getSymbol(SymbolType.Access), getSymbol(SymbolType.TypeOrVoid), getSymbol(TokenType.Identifier), getSymbol(TokenType.LParen), getSymbol(SymbolType.OptionalParameterList), getSymbol(TokenType.RParen), getSymbol(TokenType.LCurly), getSymbol(SymbolType.Statements), getSymbol(TokenType.RCurly) }
        });
        productions.put(SymbolType.TypeOrVoid, new Symbol[][] {
                { getSymbol(SymbolType.Type) },
                { getSymbol(TokenType.VoidType) }
        });
        productions.put(SymbolType.OptionalVisibility, new Symbol[][] {
                {  },
                { getSymbol(TokenType.Visibility) }
        });
        productions.put(SymbolType.Access, new Symbol[][] {
                {  },
                { getSymbol(TokenType.Static) }
        });
        productions.put(SymbolType.Type, new Symbol[][] {
                { getSymbol(TokenType.IntType) },
                { getSymbol(TokenType.BooleanType) },
                { getSymbol(TokenType.Identifier) },
                { getSymbol(TokenType.IntType), getSymbol(TokenType.LBracket), getSymbol(TokenType.RBracket) },
                { getSymbol(TokenType.Identifier), getSymbol(TokenType.LBracket), getSymbol(TokenType.RBracket) }
        });
        productions.put(SymbolType.OptionalParameterList, new Symbol[][] {
                {  },
                { getSymbol(SymbolType.ParameterList) }
        });
        productions.put(SymbolType.ParameterList, new Symbol[][] {
                { getSymbol(SymbolType.Type), getSymbol(TokenType.Identifier) },
                { getSymbol(SymbolType.Type), getSymbol(TokenType.Identifier), getSymbol(TokenType.Comma), getSymbol(SymbolType.ParameterList) }
        });
        productions.put(SymbolType.OptionalArgumentList, new Symbol[][] {
                {  },
                { getSymbol(SymbolType.ArgumentList) }
        });
        productions.put(SymbolType.ArgumentList, new Symbol[][] {
                { getSymbol(SymbolType.Expression) },
                { getSymbol(SymbolType.Expression), getSymbol(TokenType.Comma), getSymbol(SymbolType.ArgumentList) }
        });
        productions.put(SymbolType.Reference, new Symbol[][] {
                { getSymbol(SymbolType.IdentifierOrThis), getSymbol(SymbolType.ReferenceIdRepeat) },
        });
        productions.put(SymbolType.ReferenceIdRepeat, new Symbol[][] {
                {  },
                { getSymbol(TokenType.Dot), getSymbol(TokenType.Identifier), getSymbol(SymbolType.ReferenceIdRepeat) },
        });
        productions.put(SymbolType.IdentifierOrThis, new Symbol[][] {
                { getSymbol(TokenType.Identifier) },
                { getSymbol(TokenType.This) }
        });
        productions.put(SymbolType.Statements, new Symbol[][] {
                {  },
                { getSymbol(SymbolType.Statement), getSymbol(SymbolType.Statements) }
        });
        productions.put(SymbolType.Statement, new Symbol[][] {
                { getSymbol(TokenType.LCurly), getSymbol(SymbolType.Statements), getSymbol(TokenType.RCurly) },
                { getSymbol(SymbolType.Type), getSymbol(TokenType.Identifier), getSymbol(TokenType.AssignmentOp), getSymbol(SymbolType.Expression), getSymbol(TokenType.Semicolon) },
                { getSymbol(SymbolType.Reference), getSymbol(TokenType.AssignmentOp), getSymbol(SymbolType.Expression), getSymbol(TokenType.Semicolon) },
                { getSymbol(SymbolType.Reference), getSymbol(TokenType.LBracket), getSymbol(SymbolType.Expression), getSymbol(TokenType.RBracket), getSymbol(TokenType.AssignmentOp), getSymbol(SymbolType.Expression), getSymbol(TokenType.Semicolon) },
                { getSymbol(SymbolType.Reference), getSymbol(TokenType.LParen), getSymbol(SymbolType.OptionalArgumentList), getSymbol(TokenType.RParen), getSymbol(TokenType.Semicolon) },
                { getSymbol(TokenType.Return), getSymbol(TokenType.Semicolon) },
                { getSymbol(TokenType.Return), getSymbol(SymbolType.Expression), getSymbol(TokenType.Semicolon) },
                { getSymbol(TokenType.If), getSymbol(TokenType.LParen), getSymbol(SymbolType.Expression), getSymbol(TokenType.RParen), getSymbol(SymbolType.Statement) },
                { getSymbol(TokenType.If), getSymbol(TokenType.LParen), getSymbol(SymbolType.Expression), getSymbol(TokenType.RParen), getSymbol(SymbolType.Statement), getSymbol(TokenType.Else), getSymbol(SymbolType.Statement) },
                { getSymbol(TokenType.While), getSymbol(TokenType.LParen), getSymbol(SymbolType.Expression), getSymbol(TokenType.RParen), getSymbol(SymbolType.Statement) }
        });
        productions.put(SymbolType.Expression, new Symbol[][] {
                { getSymbol(SymbolType.ExpressionTerminator) },
                { getSymbol(SymbolType.ExpressionTerminator), getSymbol(SymbolType.BinaryOp), getSymbol(SymbolType.Expression) }
        });
        productions.put(SymbolType.ExpressionTerminator, new Symbol[][] {
                { getSymbol(TokenType.BooleanLiteral) },
                { getSymbol(TokenType.New), getSymbol(TokenType.Identifier), getSymbol(TokenType.LParen), getSymbol(TokenType.RParen) },
                { getSymbol(TokenType.New), getSymbol(TokenType.IntType), getSymbol(TokenType.LBracket), getSymbol(SymbolType.Expression), getSymbol(TokenType.RBracket) },
                { getSymbol(TokenType.New), getSymbol(TokenType.Identifier), getSymbol(TokenType.LBracket), getSymbol(SymbolType.Expression), getSymbol(TokenType.RBracket) },
                { getSymbol(TokenType.LParen), getSymbol(SymbolType.Expression), getSymbol(TokenType.RParen) },
                { getSymbol(SymbolType.Reference) },
                { getSymbol(SymbolType.Reference), getSymbol(TokenType.LParen), getSymbol(SymbolType.OptionalArgumentList), getSymbol(TokenType.RParen) },
                { getSymbol(SymbolType.UnaryOp), getSymbol(SymbolType.Expression) },
                { getSymbol(SymbolType.Number) },
                { getSymbol(SymbolType.Reference), getSymbol(TokenType.LBracket), getSymbol(SymbolType.Expression), getSymbol(TokenType.RBracket) }
        });
        productions.put(SymbolType.UnaryOp, new Symbol[][] {
                { getSymbol(TokenType.BitComp) },
                { getSymbol(TokenType.LogNot) },
                { getSymbol(TokenType.Minus) }
        });
        productions.put(SymbolType.BinaryOp, new Symbol[][] {
                { getSymbol(TokenType.Add) },
                { getSymbol(TokenType.Minus) },
                { getSymbol(TokenType.Multiply) },
                { getSymbol(TokenType.Divide) },
                { getSymbol(TokenType.RelLT) },
                { getSymbol(TokenType.RelGT) },
                { getSymbol(TokenType.RelLEq) },
                { getSymbol(TokenType.RelGEq) },
                { getSymbol(TokenType.RelEq) },
                { getSymbol(TokenType.RelNEq) },
                { getSymbol(TokenType.BitAnd) },
                { getSymbol(TokenType.BitOr) },
                { getSymbol(TokenType.LogAnd) },
                { getSymbol(TokenType.LogOr) }
        });
        productions.put(SymbolType.Number, new Symbol[][] {
                { getSymbol(TokenType.ByteLiteral) },
                { getSymbol(TokenType.IntLiteral) },
                { getSymbol(TokenType.DoubleLiteral) },
                { getSymbol(TokenType.FloatLiteral) }
        });
    }

    private final TokenType terminalType; // null if non-terminal
    private final SymbolType symbolType; // null if terminal

    private Symbol(TokenType terminalType) {
        this.terminalType = terminalType;
        symbolType = null;
    }

    private Symbol(SymbolType symbolType) {
        terminalType = null;
        this.symbolType = symbolType;
    }

    public TokenType getTerminalType() {
        return terminalType;
    }

    public SymbolType getSymbolType() {
        return symbolType;
    }

    public boolean isTerminal() {
        return terminalType != null;
    }

    @Override
    public String toString() {
        return isTerminal() ? terminalType.toString() : symbolType.toString();
    }
}