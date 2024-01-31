/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Operator extends Terminal {

  public static Map<TokenType, Integer> binOpPrecedence; // low precedence = handled first

  static {
    binOpPrecedence = new HashMap();
    binOpPrecedence.put(TokenType.Multiply, 0);
    binOpPrecedence.put(TokenType.Divide, 0);
    binOpPrecedence.put(TokenType.Add, 1);
    binOpPrecedence.put(TokenType.Minus, 1);
    binOpPrecedence.put(TokenType.RelLT, 2);
    binOpPrecedence.put(TokenType.RelGT, 2);
    binOpPrecedence.put(TokenType.RelLEq, 2);
    binOpPrecedence.put(TokenType.RelGEq, 2);
    binOpPrecedence.put(TokenType.RelEq, 3);
    binOpPrecedence.put(TokenType.RelNEq, 3);
    binOpPrecedence.put(TokenType.BitAnd, 4);
    binOpPrecedence.put(TokenType.BitXor, 5);
    binOpPrecedence.put(TokenType.BitOr, 6);
    binOpPrecedence.put(TokenType.LogAnd, 7);
    binOpPrecedence.put(TokenType.LogOr, 8);
  }

  public Operator (Token t) {
    super (t);
  }

  public <A,R> R visit(Visitor<A,R> v, A o) {
      return v.visitOperator(this, o);
  }
}
