package miniJava;

import miniJava.AbstractSyntaxTrees.AST;
import miniJava.AbstractSyntaxTrees.ASTDisplay;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.Codifier;
import miniJava.ContextualAnalysis.Matcher;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

import java.io.*;

public class Compiler {
	// Main function, the file to compile will be an argument.
	public static void main(String[] args) throws FileNotFoundException {
		ErrorReporter errors = new ErrorReporter();
		if (args.length == 0) throw new IllegalArgumentException("Missing source code file path");
		InputStream in;
		try {
			File file = new File(args[0]);
			in = new FileInputStream(file);
		} catch (IOException e) {
			throw new FileNotFoundException("Source code file not found");
		}
		Scanner scanner = new Scanner(in, errors);
		Parser parser = new Parser(scanner, errors);
		Package ast = (Package)parser.parse();
		if (ast != null) {
			ASTDisplay display = new ASTDisplay();
			Matcher matcher = new Matcher(errors);
			matcher.match(ast);
			if (!errors.hasErrors()) {
				display.showTree(ast);
				Codifier codifier = new Codifier(errors);
				codifier.parse(ast);
			}
		}
		if (errors.hasErrors()) {
			System.out.println("Error");
			errors.outputErrors();
		} else {
			System.out.println("Success");
		}
		try {
			in.close();
		} catch (IOException e) {
			throw new RuntimeException("file close error");
		}
	}
}
