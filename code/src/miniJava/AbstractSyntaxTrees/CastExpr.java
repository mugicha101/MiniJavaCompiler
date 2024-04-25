/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class CastExpr extends Expression {
  public CastExpr(TypeDenoter type, Expression expr, SourcePosition posn){
    super(posn);
    this.type = type;
    this.expr = expr;
  }
 
  public <A,R> R visit(Visitor<A,R> v, A o) {
      return v.visitCastExpr(this, o);
  }

  public TypeDenoter type;
  public Expression expr;
  public ClassDecl typeDecl; // null if type not class type
}