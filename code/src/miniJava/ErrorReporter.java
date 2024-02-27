package miniJava;

import miniJava.SyntacticAnalyzer.SourcePosition;

import java.util.*;

public class ErrorReporter {
	private static class ErrorEntry implements Comparable<ErrorEntry> {
		final SourcePosition posn;
		final String message;
		ErrorEntry(SourcePosition posn, String message) {
			this.posn = posn;
			this.message = String.format("%d:%d %s", posn.line, posn.offset, message);
		}

		@Override
		public int compareTo(ErrorEntry other) {
			return posn.compareTo(other.posn);
		}
	}
	private SortedSet<ErrorEntry> errorSet;
	
	public ErrorReporter() {
		this.errorSet = new TreeSet<ErrorEntry>();
	}
	
	public boolean hasErrors() {
		return !errorSet.isEmpty();
	}
	
	public void outputErrors() {
		for (ErrorEntry error : errorSet) {
			System.out.println(error.message);
		}
	}
	
	public void reportError(int line, int offset, String message) {
		reportError(new SourcePosition(line, offset), message);
	}

	public void reportError(SourcePosition posn, String message) {
		errorSet.add(new ErrorEntry(posn, message));
	}

	public List<String> getErrors() {
		List<String> errors = new ArrayList<>();
		for (ErrorEntry error : errorSet)
			errors.add(error.message);
		return errors;
	}

	public void clear() {
		errorSet.clear();
	}
}
