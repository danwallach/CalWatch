// $Id: ClTests.java,v 1.27 2011/05/23 04:47:17 gjbadros Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This Java Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
// 
// ClTests.java
// run as:
// java -classpath ./classes EDU.Washington.grad.gjb.cassowary.ClTests testNum Cns Solvers Resolves


package EDU.Washington.grad.gjb.cassowary;

import java.lang.*;
import java.util.Random;
import java.util.Vector;
import java.io.FileReader;
import java.io.BufferedReader;

public class ClTests extends CL {

  public ClTests()
  {
    RND = new Random();
  }

  public final static boolean simple1()
       throws ExCLInternalError, ExCLRequiredFailure
  {
    boolean fOkResult = true;
    ClVariable x = new ClVariable(167);
    ClVariable y = new ClVariable(2);
    ClSimplexSolver solver = new ClSimplexSolver();
      
    //    solver.addStay(x);
    //    solver.addStay(y);

    ClLinearEquation eq = new ClLinearEquation(x,new ClLinearExpression(y));
    solver.addConstraint(eq);
    fOkResult = (x.value() == y.value());

    System.out.println("x == " + x.value());
    System.out.println("y == " + y.value());
    return(fOkResult);
  }

  public final static boolean justStay1()
       throws ExCLInternalError, ExCLRequiredFailure
  {
    boolean fOkResult = true;
    ClVariable x = new ClVariable(5);
    ClVariable y = new ClVariable(10);
    ClSimplexSolver solver = new ClSimplexSolver();
      
    solver.addStay(x);
    solver.addStay(y);
    fOkResult = fOkResult && CL.approx(x,5);
    fOkResult = fOkResult && CL.approx(y,10);
    System.out.println("x == " + x.value());
    System.out.println("y == " + y.value());
    return(fOkResult);
  }
  
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

    return(fOkResult);
  } 

  public final static boolean addDelete2()
       throws ExCLInternalError, ExCLRequiredFailure, 
	 ExCLConstraintNotFound, ExCLNonlinearExpression
  {
    boolean fOkResult = true; 
    ClVariable x = new ClVariable("x");
    ClVariable y = new ClVariable("y");
    ClSimplexSolver solver = new ClSimplexSolver();

    solver
      .addConstraint( new ClLinearEquation(x, 100.0, ClStrength.weak))
      .addConstraint( new ClLinearEquation(y, 120.0, ClStrength.strong));
      
    ClLinearInequality c10 = new ClLinearInequality(x,CL.LEQ,10.0);
    ClLinearInequality c20 = new ClLinearInequality(x,CL.LEQ,20.0);
      
    solver
      .addConstraint(c10)
      .addConstraint(c20);
    fOkResult = fOkResult && CL.approx(x,10.0) && CL.approx(y,120.0);
    System.out.println("x == " + x.value() + ", y == " + y.value());

    solver.removeConstraint(c10);
    fOkResult = fOkResult && CL.approx(x,20.0) && CL.approx(y,120.0);
    System.out.println("x == " + x.value() + ", y == " + y.value());
   
    ClLinearEquation cxy = new ClLinearEquation( CL.Times(2.0,x), y);
    solver.addConstraint(cxy);
    fOkResult = fOkResult && CL.approx(x,20.0) && CL.approx(y,40.0);
    System.out.println("x == " + x.value() + ", y == " + y.value());

    solver.removeConstraint(c20);
    fOkResult = fOkResult && CL.approx(x,60.0) && CL.approx(y,120.0);
    System.out.println("x == " + x.value() + ", y == " + y.value());

    solver.removeConstraint(cxy);
    fOkResult = fOkResult && CL.approx(x,100.0) && CL.approx(y,120.0);
    System.out.println("x == " + x.value() + ", y == " + y.value());
      
    return(fOkResult);
  } 

  public final static boolean casso1()
       throws ExCLInternalError, ExCLRequiredFailure
  {
    boolean fOkResult = true; 
    ClVariable x = new ClVariable("x");
    ClVariable y = new ClVariable("y");
    ClSimplexSolver solver = new ClSimplexSolver();

    solver
      .addConstraint( new ClLinearInequality(x,CL.LEQ,y) )
      .addConstraint( new ClLinearEquation(y, CL.Plus(x,3.0)) )
      .addConstraint( new ClLinearEquation(x,10.0,ClStrength.weak) )
      .addConstraint( new ClLinearEquation(y,10.0,ClStrength.weak) )
      ;
   
    fOkResult = fOkResult && 
      ( CL.approx(x,10.0) && CL.approx(y,13.0) ||
	CL.approx(x,7.0) && CL.approx(y,10.0) );
      
    System.out.println("x == " + x.value() + ", y == " + y.value());
    return(fOkResult);
  } 

  public final static boolean inconsistent1()
       throws ExCLInternalError, ExCLRequiredFailure
  {
    try 
      {
      ClVariable x = new ClVariable("x");
      ClSimplexSolver solver = new ClSimplexSolver();
      
      solver
	.addConstraint( new ClLinearEquation(x,10.0) )
	.addConstraint( new ClLinearEquation(x, 5.0) );
      
      // no exception, we failed!
      return(false);
      } 
    catch (ExCLRequiredFailure err)
      {
      // we want this exception to get thrown
      System.out.println("Success -- got the exception");
      return(true);
      }
  }

  public final static boolean inconsistent2()
       throws ExCLInternalError, ExCLRequiredFailure
  {
    try 
      {
      ClVariable x = new ClVariable("x");
      ClSimplexSolver solver = new ClSimplexSolver();
      
      solver
	.addConstraint( new ClLinearInequality(x,CL.GEQ,10.0) )
	.addConstraint( new ClLinearInequality(x,CL.LEQ, 5.0) );

      // no exception, we failed!
      return(false);
      } 
    catch (ExCLRequiredFailure err)
      {
      // we want this exception to get thrown
      System.out.println("Success -- got the exception");
      return(true);
      }
  }

  public final static boolean multiedit()
       throws ExCLInternalError, ExCLRequiredFailure, ExCLError
  {
    try 
      {
      boolean fOkResult = true;

      ClVariable x = new ClVariable("x");
      ClVariable y = new ClVariable("y");
      ClVariable w = new ClVariable("w");
      ClVariable h = new ClVariable("h");
      ClSimplexSolver solver = new ClSimplexSolver();
      
      solver
        .addStay(x)
        .addStay(y)
        .addStay(w)
        .addStay(h);

      solver
        .addEditVar(x)
        .addEditVar(y)
        .beginEdit();

      solver
        .suggestValue(x,10)
        .suggestValue(y,20)
        .resolve();

      System.out.println("x = " + x.value() + "; y = " + y.value());
      System.out.println("w = " + w.value() + "; h = " + h.value());

      fOkResult = fOkResult &&
        CL.approx(x,10) && CL.approx(y,20) &&
        CL.approx(w,0) && CL.approx(h,0);

      solver
        .addEditVar(w)
        .addEditVar(h)
        .beginEdit();

      solver
        .suggestValue(w,30)
        .suggestValue(h,40)
        .endEdit();

      System.out.println("x = " + x.value() + "; y = " + y.value());
      System.out.println("w = " + w.value() + "; h = " + h.value());

      fOkResult = fOkResult &&
        CL.approx(x,10) && CL.approx(y,20) && 
        CL.approx(w,30) && CL.approx(h,40);

      solver
        .suggestValue(x,50)
        .suggestValue(y,60)
        .endEdit();

      System.out.println("x = " + x.value() + "; y = " + y.value());
      System.out.println("w = " + w.value() + "; h = " + h.value());

      fOkResult = fOkResult &&
        CL.approx(x,50) && CL.approx(y,60) &&
        CL.approx(w,30) && CL.approx(h,40);

      return(fOkResult);
      } 
    catch (ExCLRequiredFailure err)
      {
      // we want this exception to get thrown
      System.out.println("Success -- got the exception");
      return(true);
      }
  }


  public final static boolean inconsistent3()
       throws ExCLInternalError, ExCLRequiredFailure
  {
    try 
      {
      ClVariable w = new ClVariable("w");
      ClVariable x = new ClVariable("x");
      ClVariable y = new ClVariable("y");
      ClVariable z = new ClVariable("z");
      ClSimplexSolver solver = new ClSimplexSolver();
      
      solver
	.addConstraint( new ClLinearInequality(w,CL.GEQ,10.0) )
	.addConstraint( new ClLinearInequality(x,CL.GEQ,w) )
	.addConstraint( new ClLinearInequality(y,CL.GEQ,x) )
	.addConstraint( new ClLinearInequality(z,CL.GEQ,y) )
	.addConstraint( new ClLinearInequality(z,CL.GEQ,8.0) )
	.addConstraint( new ClLinearInequality(z,CL.LEQ, 4.0) );

      // no exception, we failed!
      return(false);
      } 
    catch (ExCLRequiredFailure err)
      {
      // we want this exception to get thrown
      System.out.println("Success -- got the exception");
      return(true);
      }
  }


  public final static boolean addDel(int nCns, int nVars, int nResolves)
       throws ExCLInternalError, ExCLRequiredFailure, 
	 ExCLNonlinearExpression, ExCLConstraintNotFound
  {
    Timer timer = new Timer();
    // FIXGJB: from where did .12 come?
    final double ineqProb = 0.12;
    final int maxVars = 3;
    InitializeRandoms();

    System.out.println("starting timing test. nCns = " + nCns +
        ", nVars = " + nVars + ", nResolves = " + nResolves);
    
    timer.Start();
    ClSimplexSolver solver = new ClSimplexSolver();
    solver.setAutosolve(false);

    ClVariable[] rgpclv = new ClVariable[nVars];
    for (int i = 0; i < nVars; i++) {
      rgpclv[i] = new ClVariable(i,"x");
      solver.addStay(rgpclv[i]);
    }

    int nCnsMade = nCns*2;

    ClConstraint[] rgpcns = new ClConstraint[nCnsMade];
    ClConstraint[] rgpcnsAdded = new ClConstraint[nCns];
    int nvs = 0;
    int k;
    int j;
    double coeff;
    for (j = 0; j < nCnsMade; ++j) {
      // number of variables in this constraint
      nvs = RandomInRange(1,maxVars);
      if (fTraceOn) traceprint("Using nvs = " + nvs);
      ClLinearExpression expr = new ClLinearExpression(UniformRandomDiscretized() * 20.0 - 10.0);
      for (k = 0; k < nvs; k++) {
        coeff = UniformRandomDiscretized()*10 - 5;
        int iclv = (int) (UniformRandomDiscretized()*nVars);
        expr.addExpression(CL.Times(rgpclv[iclv], coeff));
      }
      if (UniformRandomDiscretized() < ineqProb) {
        rgpcns[j] = new ClLinearInequality(expr);
      } else {  
        rgpcns[j] = new ClLinearEquation(expr);
      }
      if (fTraceOn) traceprint("Constraint " + j + " is " + rgpcns[j]);
    }

    timer.Stop();
    System.out.println("done building data structures");
    System.out.println("time = " + timer.ElapsedTime());

    timer.Reset();
    timer.Start();
    int cExceptions = 0;
    int cCns = 0;
    for (j = 0; j < nCnsMade && cCns < nCns; j++) {
      // add the constraint -- if it's incompatible, just ignore it
      // FIXGJB: exceptions are extra expensive in C++, so this might not
      // be particularly fair
      try
	{
	  solver.addConstraint(rgpcns[j]);
          rgpcnsAdded[cCns++] = rgpcns[j];
          if (fTraceAdded) traceprint("Added cn: " + rgpcns[j]);
	}
      catch (ExCLRequiredFailure err)
	{
	  cExceptions++;
	  if (fTraceOn) traceprint("got exception adding " + rgpcns[j]);
	  if (fTraceAdded) traceprint("got exception adding " + rgpcns[j]);
	  rgpcns[j] = null;
	}
    }
    solver.solve();
    timer.Stop();

    System.out.println("done adding " + cCns + " constraints [" 
                       + j + " attempted, " 
                       + cExceptions + " exceptions]");
    System.out.println("time = " + timer.ElapsedTime() + "\n");
    System.out.println("time per Add cn = " + timer.ElapsedTime()/cCns);
    
    int e1Index = (int) (UniformRandomDiscretized()*nVars);
    int e2Index = (int) (UniformRandomDiscretized()*nVars);
    
    System.out.println("Editing vars with indices " + e1Index + ", " + e2Index);
    
    ClEditConstraint edit1 = new ClEditConstraint(rgpclv[e1Index],ClStrength.strong);
    ClEditConstraint edit2 = new ClEditConstraint(rgpclv[e2Index],ClStrength.strong);
    
    //   CL.fDebugOn = CL.fTraceOn = true;
    System.out.println("about to start resolves");
    
    timer.Reset();
    timer.Start();
    
    solver
      .addConstraint(edit1)
      .addConstraint(edit2);

    timer.Stop();
    for (int m = 0; m < nResolves; m++)
      {
        solver.resolve(rgpclv[e1Index].value() * 1.001,
                       rgpclv[e2Index].value() * 1.001);
      }
    solver.removeConstraint(edit1);
    solver.removeConstraint(edit2);
    timer.Stop();
    
    
    System.out.println("done resolves -- now removing constraints");
    System.out.println("time = " + timer.ElapsedTime() + "\n");
    System.out.println("time per Resolve = " + timer.ElapsedTime()/nResolves);
    
    timer.Reset();
    timer.Start();
    
    for (j = 0; j < cCns; j++)
      {
        solver.removeConstraint(rgpcnsAdded[j]);
        // System.out.println("removing #" + j);
      }
    //    solver.solve();
    timer.Stop();
    
    System.out.println("done removing constraints and addDel timing test");
    System.out.println("time = " + timer.ElapsedTime() + "\n");
    System.out.println("time per Remove cn = " + timer.ElapsedTime()/cCns);
    
    return true;
  }

  public final static void InitializeRandomsFromFile() {
    try {
      iRandom = 0;
      String s;
      FileReader in = new FileReader("randoms.txt");
      BufferedReader reader = new BufferedReader(in);
      vRandom = new Vector(20001);
      // skip over comment
      reader.readLine();
      // skip over number of randoms
      reader.readLine();
      Double f;
      while ((s = reader.readLine()) != null) {
        f = Double.valueOf(s);
        vRandom.add(f);
        ++cRandom;
      }
      System.err.println("Read in " + cRandom + " random numbers");
    } catch (java.io.IOException e) {
      // nothing
    }
  }
    
  public final static void InitializeRandoms() {
    // do nothing
  }


  public final static double UniformRandomDiscretized()
  {
    double n = Math.abs(RND.nextInt());
    return (n/Integer.MAX_VALUE);
  }

  public final static double UniformRandomDiscretizedFromFile()
  {
    if (iRandom >= cRandom) {
      // throw new Exception("Out of random numbers");
      return -1;
    }
    double f =  ((Double)vRandom.elementAt(iRandom++)).doubleValue();
    //    System.out.println("returning value = " + f);
    return f;
  }

  public final static double GrainedUniformRandom()
  {
    final double grain = 1.0e-4;
    double n = UniformRandomDiscretized();
    double answer =  ((int)(n/grain))*grain;
    return answer;
  }

  public final static int RandomInRange(int low, int high)
  {
    return (int) (UniformRandomDiscretized()*(high-low+1))+low;
  }
    

  public final static boolean addDelSolvers(int nCns, int nResolves, int nSolvers, int testNum)
       throws ExCLInternalError, ExCLRequiredFailure, 
	 ExCLNonlinearExpression, ExCLConstraintNotFound
  {
    Timer timer = new Timer();

    double tmAdd, tmEdit, tmResolve, tmEndEdit;
    // FIXGJB: from where did .12 come?
    final double ineqProb = 0.12;
    final int maxVars = 3;
    final int nVars = nCns;
    InitializeRandoms();

    System.err.println("starting timing test. nCns = " + nCns +
                       ", nSolvers = " + nSolvers + ", nResolves = " + nResolves);
    
    timer.Start();

    ClSimplexSolver[] rgsolvers = new ClSimplexSolver[nSolvers+1];

    for (int is = 0; is < nSolvers+1; ++is) {
      rgsolvers[is] = new ClSimplexSolver();
      rgsolvers[is].setAutosolve(false);
    }

    ClVariable[] rgpclv = new ClVariable[nVars];
    for (int i = 0; i < nVars; i++) {
      rgpclv[i] = new ClVariable(i,"x");
      for (int is = 0; is < nSolvers+1; ++is) {
        rgsolvers[is].addStay(rgpclv[i]);
      }
    }

    int nCnsMade = nCns*5;

    ClConstraint[] rgpcns = new ClConstraint[nCnsMade];
    ClConstraint[] rgpcnsAdded = new ClConstraint[nCns];
    int nvs = 0;
    int k;
    int j;
    double coeff;
    for (j = 0; j < nCnsMade; ++j) {
      // number of variables in this constraint
      nvs = RandomInRange(1,maxVars);
      if (fTraceOn) traceprint("Using nvs = " + nvs);
      ClLinearExpression expr = new ClLinearExpression(GrainedUniformRandom() * 20.0 - 10.0);
      for (k = 0; k < nvs; k++) {
        coeff = GrainedUniformRandom()*10 - 5;
        int iclv = (int) (UniformRandomDiscretized()*nVars);
        expr.addExpression(CL.Times(rgpclv[iclv], coeff));
      }
      if (UniformRandomDiscretized() < ineqProb) {
        rgpcns[j] = new ClLinearInequality(expr);
      } else {  
        rgpcns[j] = new ClLinearEquation(expr);
      }
      if (fTraceOn) traceprint("Constraint " + j + " is " + rgpcns[j]);
    }

    timer.Stop();
    System.err.println("done building data structures");


    for (int is = 0; is < nSolvers; ++is) {
      int cCns = 0;
      int cExceptions = 0;
      ClSimplexSolver solver = rgsolvers[nSolvers];
      cExceptions = 0;
      for (j = 0; j < nCnsMade && cCns < nCns; j++) {
        try
          {
            if (null != rgpcns[j]) {
              solver.addConstraint(rgpcns[j]);
              //              System.out.println("Added " + j + " = " + rgpcns[j]);
              ++cCns;
            }
          }
        catch (ExCLRequiredFailure err)
          {
            cExceptions++;
            rgpcns[j] = null;
          }
      }
    }


    timer.Reset();
    timer.Start();
    for (int is = 0; is < nSolvers; ++is) {
      int cCns = 0;
      int cExceptions = 0;
      ClSimplexSolver solver = rgsolvers[is];
      cExceptions = 0;
      for (j = 0; j < nCnsMade && cCns < nCns; j++) {
        // add the constraint -- if it's incompatible, just ignore it
        try
          {
            if (null != rgpcns[j]) {
              solver.addConstraint(rgpcns[j]);
              //              System.out.println("Added " + j + " = " + rgpcns[j]);
              ++cCns;
            }
          }
        catch (ExCLRequiredFailure err)
          {
            cExceptions++;
            rgpcns[j] = null;
          }
      }
      System.err.println("done adding " + cCns + " constraints [" 
                         + j + " attempted, " 
                         + cExceptions + " exceptions]");
      solver.solve();
    }
    timer.Stop();


    tmAdd = timer.ElapsedTime();
    
    int e1Index = (int) (UniformRandomDiscretized()*nVars);
    int e2Index = (int) (UniformRandomDiscretized()*nVars);
    
    System.err.println("Editing vars with indices " + e1Index + ", " + e2Index);
    
    ClEditConstraint edit1 = new ClEditConstraint(rgpclv[e1Index],ClStrength.strong);
    ClEditConstraint edit2 = new ClEditConstraint(rgpclv[e2Index],ClStrength.strong);
    
    //   CL.fDebugOn = CL.fTraceOn = true;
    System.err.println("about to start resolves");
    
    timer.Reset();
    timer.Start();

    for (int is = 0; is < nSolvers; ++is) {
      rgsolvers[is]
        .addConstraint(edit1)
        .addConstraint(edit2);
    }      
    timer.Stop();
    tmEdit = timer.ElapsedTime();


    timer.Reset();
    timer.Start();
    for (int is = 0; is < nSolvers; ++is) {
      ClSimplexSolver solver = rgsolvers[is];

      for (int m = 0; m < nResolves; m++)
        {
          solver.resolve(rgpclv[e1Index].value() * 1.001,
                         rgpclv[e2Index].value() * 1.001);
        }
    }
    timer.Stop();
    tmResolve = timer.ElapsedTime();


    System.err.println("done resolves -- now ending edits");

    timer.Reset();
    timer.Start();
    for (int is = 0; is < nSolvers; ++is) {
      rgsolvers[is]
        .removeConstraint(edit1)
        .removeConstraint(edit2);
    }
    timer.Stop();

    tmEndEdit = timer.ElapsedTime();

    final int mspersec = 1000;
    System.out.println(nCns + "," + nSolvers + "," + nResolves + "," + testNum + "," +
                       tmAdd*mspersec + "," + tmEdit*mspersec + "," + tmResolve*mspersec + "," + tmEndEdit*mspersec + "," +
                       tmAdd/nCns/nSolvers*mspersec + "," +
                       tmEdit/nSolvers/2*mspersec + "," + 
                       tmResolve/nResolves/nSolvers*mspersec + "," +
                       tmEndEdit/nSolvers/2*mspersec);
    return true;
  }



  public final static void main( String[] args )
       throws ExCLInternalError, ExCLNonlinearExpression,
	 ExCLRequiredFailure, ExCLConstraintNotFound, ExCLError
  {
    try 
    {
      ClTests clt = new ClTests();

      boolean fAllOkResult = true;
      boolean fResult;

      if (true) {
        System.out.println("\n\n\nsimple1:");
        fResult = simple1(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
        
        System.out.println("\n\n\njustStay1:");
        fResult = justStay1(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
	
        System.out.println("\n\n\naddDelete1:");
        fResult = addDelete1(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
        
        System.out.println("\n\n\naddDelete2:");
        fResult = addDelete2(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
        
        System.out.println("\n\n\ncasso1:");
        fResult = casso1(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
    
        System.out.println("\n\n\ninconsistent1:");
        fResult = inconsistent1(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
        
        System.out.println("\n\n\ninconsistent2:");
        fResult = inconsistent2(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
        
        System.out.println("\n\n\ninconsistent3:");
        fResult = inconsistent3(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );

        System.out.println("\n\n\nmultiedit:");
        fResult = multiedit(); fAllOkResult &= fResult;
        if (!fResult) System.out.println("Failed!");
        if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );

      }
      
      System.out.println("\n\n\naddDel:");
      int testNum = 1, cns = 900, resolves = 100, solvers = 10;
        

        if (args.length > 0)
          testNum = Integer.parseInt(args[0]);

        if (args.length > 1)
          cns = Integer.parseInt(args[1]);
        
        if (args.length > 2)
          solvers = Integer.parseInt(args[2]);
        
        if (args.length > 3)
          resolves = Integer.parseInt(args[3]);

        if (false) {
          fResult = addDel(cns,cns,resolves);
          // fResult = addDel(300,300,1000);
          // fResult = addDel(30,30,100);
          // fResult = addDel(10,10,30);
          // fResult = addDel(5,5,10);
          fAllOkResult &= fResult;
          if (!fResult) System.out.println("Failed!");
          if (CL.fGC) System.out.println("Num vars = " + ClAbstractVariable.numCreated() );
        }
        
        addDelSolvers(cns,resolves,solvers,testNum);
    } 
    catch (Exception err)
      {
        ExCLError myerr = (ExCLError) err;
        if (null != myerr) {
          System.err.println("Exception: " + myerr + ": " + myerr.description());
        } else {
          System.err.println("Exception: " + err);
        }
      }
  }
  
    

  static private int iRandom = 0;
  static private int cRandom = 0;
  static private Vector vRandom;
  static private Random RND;
}
