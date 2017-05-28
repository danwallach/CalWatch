// $Id: ClTestColumns.java,v 1.8 1999/11/17 01:34:48 gjb Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This Java Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
// 
// ClTestColumns.java

package EDU.Washington.grad.gjb.cassowary;

import java.lang.*;
import java.util.Random;

class ClTestColumns extends CL {
  public final static boolean addDelete1()
       throws ExCLInternalError, ExCLRequiredFailure, ExCLConstraintNotFound
  {
    boolean fOkResult = true; 
    ClVariable x = new ClVariable("x");
    ClSimplexSolver solver = new ClSimplexSolver();
      
    solver.addConstraint( new ClLinearEquation( x, 100, ClStrength.weak ) );
      
    ClLinearInequality c10 = new ClLinearInequality(x,CL.LEQ,10.0);
    ClLinearInequality c20 = new ClLinearInequality(x,CL.LEQ,20.0);
      
    solver
      .addConstraint(c10)
      .addConstraint(c20);
      
    fOkResult = fOkResult && CL.approx(x,10.0);
    System.out.println("x == " + x.value());
      
    solver.removeConstraint(c10);
    fOkResult = fOkResult && CL.approx(x,20.0);
    System.out.println("x == " + x.value());

    solver.removeConstraint(c20);
    fOkResult = fOkResult && CL.approx(x,100.0);
    System.out.println("x == " + x.value());

    ClLinearInequality c10again = new ClLinearInequality(x,CL.LEQ,10.0);

    solver
      .addConstraint(c10)
      .addConstraint(c10again);
      
    fOkResult = fOkResult && CL.approx(x,10.0);
    System.out.println("x == " + x.value());
    
    solver.removeConstraint(c10);
    fOkResult = fOkResult && CL.approx(x,10.0);
    System.out.println("x == " + x.value());

    solver.removeConstraint(c10again);
    fOkResult = fOkResult && CL.approx(x,100.0);
    System.out.println("x == " + x.value());

    System.err.println("Solver == " + solver);

    return(fOkResult);
  } 

  public final static boolean reqFail1()
       throws ExCLInternalError, ExCLRequiredFailure, ExCLConstraintNotFound
  {
    boolean fOkResult = true; 
    ClVariable x = new ClVariable("x");
    ClSimplexSolver solver = new ClSimplexSolver();

    for (int i = 100; i < 900; i += 100) {
        try {
            solver.addConstraint( new ClLinearEquation( x, i, ClStrength.required ) );
        } catch (Exception e) {
            // do nothing
        }
    }
      
    System.out.println("x == " + x.value());

    System.err.println("Solver == " + solver);

    return(fOkResult);
  } 


  public final static void main( String[] args )
       throws ExCLInternalError, ExCLNonlinearExpression,
	 ExCLRequiredFailure, ExCLConstraintNotFound
  {
    //    try 
    {
      boolean fAllOkResult = true;
      boolean fResult;
      
      System.out.println("addDelete1:");
      fResult = addDelete1(); fAllOkResult &= fResult;
      fResult = reqFail1(); fAllOkResult &= fResult;
      if (!fResult) System.out.println("Failed!");
      if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
    
    } 
    //    catch (Exception err)
    //      {
    //      System.err.println("Exception: " + err);
    //      }
  }

  static private Random RND;
}
