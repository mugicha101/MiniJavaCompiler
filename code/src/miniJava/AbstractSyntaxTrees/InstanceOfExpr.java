/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class InstanceOfExpr extends Expression
{
    public InstanceOfExpr(Expression expr, ClassType type, SourcePosition posn){
        super(posn);
        this.expr = expr;
        this.type = type;
    }
        
    public <A,R> R visit(Visitor<A,R> v, A o) {
        return v.visitInstanceOfExpr(this, o);
    }
    
    public Expression expr;
    public ClassType type;
}