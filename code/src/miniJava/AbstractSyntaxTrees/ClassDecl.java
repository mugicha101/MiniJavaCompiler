/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import  miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class ClassDecl extends Declaration {
  public boolean unsupported;
  public long vmtOffset; // offset of start of vmt table relative to stack base
  public Identifier parent = new Identifier(new Token(TokenType.Identifier, "Object", -1, -1));;
  public ClassDecl parentDecl;
  public List<ClassDecl> subclasses = new ArrayList<>();
  public int hierarchyDepth = -1; // depth in inheritance graph (Object is root)
  public long memSize; // size of class in memory (in bytes)
  public ClassDecl(String cn, FieldDeclList fdl, MethodDeclList mdl, SourcePosition posn) {
	  super(cn, null, posn);
	  fieldDeclList = fdl;
	  methodDeclList = mdl;
  }
  
  public <A,R> R visit(Visitor<A, R> v, A o) {
      return v.visitClassDecl(this, o);
  }
      
  public FieldDeclList fieldDeclList;
  public MethodDeclList methodDeclList;
}
