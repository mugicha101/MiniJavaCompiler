package miniJava;

import miniJava.AbstractSyntaxTrees.AST;
import miniJava.AbstractSyntaxTrees.ASTDisplay;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

import java.io.*;

public class Compiler {
	// Main function, the file to compile will be an argument.
	public static void main(String[] args) {
		ErrorReporter errors = new ErrorReporter();
		if (args.length == 0) throw new IllegalArgumentException("missing file path");
		InputStream in;
		try {
			File file = new File(args[0]);
			in = new FileInputStream(file);
		} catch (IOException e) {
			throw new RuntimeException("file open error");
		}
		Scanner scanner = new Scanner(in, errors);
		Parser parser = new Parser(scanner, errors);
		AST ast = parser.parse();
		if (errors.hasErrors()) {
			System.out.println("Error");
			errors.outputErrors();
		} else {
			if (ast != null) {
				ASTDisplay display = new ASTDisplay();
				display.showTree(ast);
			}
		}
		try {
			in.close();
		} catch (IOException e) {
			throw new RuntimeException("file close error");
		}
	}
}
