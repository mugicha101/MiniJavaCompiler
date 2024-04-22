/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class ForStmt extends Statement
{

    public ForStmt(Statement init, Expression cond, Statement incr, Statement body, SourcePosition posn){
        super(posn);
        this.init = init;
        this.cond = cond;
        this.incr = incr;
        this.body = body;
    }
        
    public <A,R> R visit(Visitor<A,R> v, A o) {
        return v.visitForStmt(this, o);
    }
    public Statement init;

    public Expression cond;
    public Statement incr;
    public Statement body;
}
