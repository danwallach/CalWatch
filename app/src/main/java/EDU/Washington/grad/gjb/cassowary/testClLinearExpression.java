// $Id: testClLinearExpression.java,v 1.9 1999/04/20 00:26:54 gjb Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This C++ Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
//
// testClLinearExpression

package EDU.Washington.grad.gjb.cassowary;

public class testClLinearExpression {
  public final static void main(String[] args) {
    ClVariable a = new ClVariable("a");
    ClLinearExpression cle = new ClLinearExpression(a);
    System.out.println(cle.toString());
    ClVariable b = new ClVariable("b");
    cle.addVariable(b,2);
    System.out.println(cle.toString()); //ASKCSK
    System.out.println(cle.times(2).toString());
    System.out.println((cle.times(2).addVariable(new ClVariable("c"),3)).times(-1).toString());
    cle = ClLinearExpression.Plus(cle,new ClLinearExpression(8));
    System.out.println(cle.toString());
    cle.changeSubject(a,b);
    System.out.println(cle.toString());
    ClLinearExpression cle2 = (ClLinearExpression) cle.clone();
    cle.addExpression(cle,-1);
    System.out.println(cle.toString());
    System.out.println(b.toString());

  }
}
