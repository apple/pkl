package org.pkl.core.newparser;

public record Span(int beginLine, int beginCol, int endLine, int endCol) {

  public boolean sameLine(Span other) {
    return endLine == other.beginLine;
  }

  /** Returns a span that starts with this span and ends with `other`. */
  public Span endWith(Span other) {
    return new Span(beginLine, beginCol, other.endLine, other.endCol);
  }

  @Override
  public String toString() {
    return String.format("(%d:%d - %d:%d)", beginLine, beginCol, endLine, endCol);
  }
}
