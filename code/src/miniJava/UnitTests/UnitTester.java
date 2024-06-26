package miniJava.UnitTests;

import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.AbstractSyntaxTrees.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

// TODO: use Compiler object directly to reduce redundant code

public class UnitTester {
    public static class Test {
        String inputPath;
        String expectedPath;
    }

    public static void main(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("missing test folder path");
        File testFolder = new File(args[0] + "/tests");
        if (!testFolder.isDirectory())
            throw new RuntimeException(String.format("invalid test folder path %s", args[0]));
        HashMap<String, Test> tests = new HashMap<>();
        for (File file : testFolder.listFiles()) {
            System.out.println(file.getAbsolutePath());
            String name = file.getName();
            int i = name.lastIndexOf('_');
            if (i == -1) throw new RuntimeException(String.format("invalid test file %s", name));
            String suffix = name.substring(i+1);
            String prefix = name.substring(0, i);
            if (!tests.containsKey(prefix)) tests.put(prefix, new Test());
            if (suffix.equals("input.txt"))
                tests.get(prefix).inputPath = file.getAbsolutePath();
            else if (suffix.equals("expected.txt"))
                tests.get(prefix).expectedPath = file.getAbsolutePath();
            else throw new RuntimeException(String.format("invalid test file %s", name));
        }

        for (Map.Entry<String, Test> entry : tests.entrySet()) {
            Test test = entry.getValue();
            try {
                File file = new File(test.inputPath);
                InputStream in = new FileInputStream(file);
                ErrorReporter errors = new ErrorReporter();
                Scanner scanner = new Scanner(in, errors);
                Parser parser = new Parser(scanner, errors);
                parser.enableUnitTest();
                AST ast = parser.parse();
                String terminalOutput = "";
                if (ast != null) {
                    java.io.ByteArrayOutputStream printCatcher = new java.io.ByteArrayOutputStream();
                    PrintStream stdOut = System.out;
                    System.setOut(new java.io.PrintStream(printCatcher));
                    ASTDisplay display = new ASTDisplay();
                    display.showTree(ast);
                    System.setOut(stdOut);
                    terminalOutput = printCatcher.toString();
                    printCatcher.close();
                }
                String output = trimString(parser.getTestOutput() + "\n" + terminalOutput);
                File expectedFile = new File(test.expectedPath);
                InputStream expectedIn = new FileInputStream(expectedFile);
                StringBuilder expectedSb = new StringBuilder();
                while (expectedIn.available() != 0) {
                    expectedSb.append((char)expectedIn.read());
                }
                String expected = trimString(expectedSb.toString());
                if (output.equals(expected)) {

                    // timing analysis
                    in = new FileInputStream(file);
                    scanner = new Scanner(in, errors);
                    parser = new Parser(scanner, errors);
                    final long startTime = System.nanoTime();
                    parser.parse();
                    final long endTime = System.nanoTime();
                    System.out.printf("test %s passed in %.3fms\n", entry.getKey(), (double)(endTime - startTime) / 1000000);
                } else {
                    System.err.printf("test %s failed\n", entry.getKey());
                    File errFile = new File(args[0] + "/failed_test_outputs/" + entry.getKey() + ".txt");
                    errFile.createNewFile();
                    OutputStream out = new FileOutputStream(errFile);
                    out.write(output.getBytes());
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("test exception %s %s", entry.getKey(), e.getMessage()));
            }
        }
    }

    private static String trimString(String string) {
        String[] lines = string.split("\n");
        for (int i = 0; i < lines.length; ++i) {
            lines[i] = lines[i].replaceAll("\r", "");
            if (lines[i].isBlank()) lines[i] = "";
            int s = lines[i].length()-1;
            while (s >= 0 && lines[i].charAt(s) == ' ') --s;
            lines[i] = lines[i].substring(0, s+1);
        }
        return String.join("\n", lines);
    }
}
