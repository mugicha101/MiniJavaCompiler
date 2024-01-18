package miniJava.SyntacticAnalyzer;

public enum SymbolType {
    // PRIMARY
    Program,
    ClassDeclaration,
    FieldDeclaration,
    MethodDeclaration,
    Access,
    Type,
    ParameterList,
    ArgumentList,
    Reference,
    Statement,
    Expression,
    UnaryOp,
    BinaryOp,
    Number,

    // HELPER
    MemberDeclarations,
    OptionalVisibility,
    TypeOrVoid,
    OptionalParameterList,
    OptionalArgumentList,
    Statements,
    IdentifierOrThis,
    ExpressionTerminator,
    ReferenceIdRepeat
}
