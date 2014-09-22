// $Id: testClStrength.java,v 1.8 1999/04/20 00:26:55 gjb Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This C++ Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
//
// testClStrength

package EDU.Washington.grad.gjb.cassowary;

class testClStrength {
  public static void main(String[] args) {
    ClSymbolicWeight clsw = new ClSymbolicWeight(0.0,1.0,0.0);
    ClSymbolicWeight clsw2 = new ClSymbolicWeight(2.0,0.5,0.5);
    ClSymbolicWeight clsw3 = new ClSymbolicWeight(2.0,0.5,0.5);
    ClSymbolicWeight clsw4 = new ClSymbolicWeight(2.0,0.4,0.5);

    System.out.println(ClStrength.required);
    System.out.println(clsw2.asDouble());

    ClStrength cls = new ClStrength("cls",clsw);
    System.out.println(cls);
    
    ClStrength cls2 = new ClStrength("cls2", clsw.times(2));
    System.out.println(cls2);
  }
}
