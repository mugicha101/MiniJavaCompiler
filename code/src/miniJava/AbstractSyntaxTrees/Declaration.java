/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class Declaration extends AST {

	// for static field decl, is address of field on stack relative to base at r15
	// for nonstatic field decl, is offset from object address
	// for class decl, is address of class (and all its static fields) on stack relative to base at r15
	// for var decl, is address on stack relative to rbp
	// method decl does not use
	public long memOffset = Long.MAX_VALUE;
	
	public Declaration(String name, TypeDenoter type, SourcePosition posn) {
		super(posn);
		this.name = name;
		this.type = type;
	}
	
	public String name;
	public TypeDenoter type;
}
