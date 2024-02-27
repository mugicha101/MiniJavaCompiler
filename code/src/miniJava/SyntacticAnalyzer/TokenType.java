package miniJava.SyntacticAnalyzer;

// TODO: Enumate the types of tokens we have.
//   Consider taking a look at the terminals in the Grammar.
//   What types of tokens do we want to be able to differentiate between?
//   E.g., I know "class" and "while" will result in different syntax, so
//   it makes sense for those reserved words to be their own token types.
//
// This may result in the question "what doesn't result in different syntax?"
//   By example, if binary operations are always "x binop y"
//   Then it makes sense for -,+,*,etc. to be one TokenType "operator" that can be accepted,
//      (E.g. compare accepting the stream: Expression Operator Expression Semicolon
//       compare against accepting stream: Expression (Plus|Minus|Multiply) Expression Semicolon.)
//   and then in a later assignment, we can peek at the Token's underlying text
//   to differentiate between them.
public enum TokenType {
    If, Else, For, While, Do, Switch, // control flow
    Visibility, // visibility
    Class, Static, This, Return, New, // other class/func tokens
    Identifier, // names
    Add, Minus, Multiply, Divide, // arithmetic operators (minus is both binary and unary depending on context)
    RelLT, RelGT, RelLEq, RelGEq, RelEq, RelNEq, // relational operators
    BitAnd, BitXor, BitOr, BitComp, // bitwise operators
    LogAnd, LogOr, LogNot, // logical operators
    AssignmentOp, // assignment operators
    VoidType, BooleanType, ByteType, CharType, IntType, LongType, FloatType, DoubleType, // primitive types
    BooleanLiteral, ByteLiteral, CharLiteral, IntLiteral, LongLiteral, FloatLiteral, DoubleLiteral, StringLiteral, NullLiteral, // literals
    LCurly, RCurly, LParen, RParen, LBracket, RBracket, // parenthesis family
    Comma, Semicolon, Colon, Dot, // misc single character tokens
    End, Error, ParseEnd
}