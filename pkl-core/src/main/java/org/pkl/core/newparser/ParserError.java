package org.pkl.core.newparser;

public class ParserError extends RuntimeException {

  public ParserError(String msg, Span span) {
    super(String.format("Error parsing file: %s\nat %s", msg, span));
  }
}
