package org.klomp.cassowary;

import java.util.Random;

import org.junit.Test;
import org.klomp.cassowary.clconstraint.ClConstraint;
import org.klomp.cassowary.clconstraint.ClEditConstraint;
import org.klomp.cassowary.clconstraint.ClLinearEquation;
import org.klomp.cassowary.clconstraint.ClLinearInequality;

public class AddDelResolversTest {

    static private Random RND = new Random();

    @Test
    public void testAddDelete() throws Exception {
        int testNum = 1, cns = 900, resolves = 400, solvers = 15;
        addDelSolvers(cns, resolves, solvers, testNum);

    }

    // @Test
    public void testAddDeleteMany() throws Exception {
        System.out.println("type something");
        System.in.read();
        System.out.println("starting");

        int testNum = 40, cns = 1100, resolves = 10000, solvers = 20;
        addDelSolvers(cns, resolves, solvers, testNum);

        System.out.println("CAV.numCreated: " + ClAbstractVariable.numCreated());

        // System.out.println("type something");
        // System.in.read();
        // System.out.println("ended");

        // int testNum = 40, cns = 1100, resolves = 3000, solvers = 20;
        // Elapsed time for add: 4.35 seconds
        // Elapsed time for resolve: 14.425 seconds
        //
        // Elapsed time for add: 3.272 seconds
        // Elapsed time for resolve: 14.283 seconds
        //
        // Elapsed time for add: 2.783 seconds
        // Elapsed time for resolve: 15.699 seconds
        //
        // Elapsed time for add: 2.721 seconds
        // Elapsed time for resolve: 14.923 seconds
    }

    public final static double GrainedUniformRandom() {
        final double grain = 1.0e-4;
        double n = UniformRandomDiscretized();
        double answer = ((int) (n / grain)) * grain;
        return answer;
    }

    public final static int RandomInRange(int low, int high) {
        return (int) (UniformRandomDiscretized() * (high - low + 1)) + low;
    }

    public final static void InitializeRandoms() {
        // do nothing
    }

    public final static double UniformRandomDiscretized() {
        double n = Math.abs(RND.nextInt());
        return (n / Integer.MAX_VALUE);
    }

    public final static boolean addDelSolvers(int nCns, int nResolves, int nSolvers, int testNum) throws CLInternalError,
            RequiredConstraintFailureException, NonlinearExpressionException, ConstraintNotFoundException {
        Timer timer = new Timer();

        double tmAdd, tmEdit, tmResolve, tmEndEdit;
        // FIXGJB: from where did .12 come?
        final double ineqProb = 0.12;
        final int maxVars = 3;
        final int nVars = nCns;
        InitializeRandoms();

        System.err.println("starting timing test. nCns = " + nCns + ", nSolvers = " + nSolvers + ", nResolves = " + nResolves);

        timer.Start();

        ClSimplexSolver[] rgsolvers = new ClSimplexSolver[nSolvers + 1];

        for (int is = 0; is < nSolvers + 1; ++is) {
            rgsolvers[is] = new ClSimplexSolver();
            rgsolvers[is].setAutosolve(false);
        }

        ClVariable[] rgpclv = new ClVariable[nVars];
        for (int i = 0; i < nVars; i++) {
            rgpclv[i] = new ClVariable(i, "x");
            for (int is = 0; is < nSolvers + 1; ++is) {
                rgsolvers[is].addStay(rgpclv[i]);
            }
        }

        int nCnsMade = nCns * 5;

        ClConstraint[] rgpcns = new ClConstraint[nCnsMade];
        int nvs = 0;
        int k;
        int j;
        double coeff;
        for (j = 0; j < nCnsMade; ++j) {
            // number of variables in this constraint
            nvs = RandomInRange(1, maxVars);
            if (CL.fTraceOn)
                CL.traceprint("Using nvs = " + nvs);
            ClLinearExpression expr = new ClLinearExpression(GrainedUniformRandom() * 20.0 - 10.0);
            for (k = 0; k < nvs; k++) {
                coeff = GrainedUniformRandom() * 10 - 5;
                int iclv = (int) (UniformRandomDiscretized() * nVars);
                expr.addExpression(CL.Times(rgpclv[iclv], coeff));
            }
            if (UniformRandomDiscretized() < ineqProb) {
                rgpcns[j] = new ClLinearInequality(expr);
            } else {
                rgpcns[j] = new ClLinearEquation(expr);
            }
            if (CL.fTraceOn)
                CL.traceprint("Constraint " + j + " is " + rgpcns[j]);
        }

        timer.Stop();
        System.err.println("done building data structures");

        for (int is = 0; is < nSolvers; ++is) {
            int cCns = 0;
            ClSimplexSolver solver = rgsolvers[nSolvers];
            for (j = 0; j < nCnsMade && cCns < nCns; j++) {
                try {
                    if (null != rgpcns[j]) {
                        solver.addConstraint(rgpcns[j]);
                        // System.out.println("Added " + j + " = " + rgpcns[j]);
                        ++cCns;
                    }
                } catch (RequiredConstraintFailureException err) {
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
                try {
                    if (null != rgpcns[j]) {
                        solver.addConstraint(rgpcns[j]);
                        // System.out.println("Added " + j + " = " + rgpcns[j]);
                        ++cCns;
                    }
                } catch (RequiredConstraintFailureException err) {
                    cExceptions++;
                    rgpcns[j] = null;
                }
            }
            System.err.println("done adding " + cCns + " constraints [" + j + " attempted, " + cExceptions + " exceptions]");
            solver.solve();
        }
        timer.Stop();

        tmAdd = timer.ElapsedTime();

        int e1Index = (int) (UniformRandomDiscretized() * nVars);
        int e2Index = (int) (UniformRandomDiscretized() * nVars);

        System.err.println("Editing vars with indices " + e1Index + ", " + e2Index);

        ClEditConstraint edit1 = new ClEditConstraint(rgpclv[e1Index], ClStrength.strong);
        ClEditConstraint edit2 = new ClEditConstraint(rgpclv[e2Index], ClStrength.strong);

        // CL.fDebugOn = CL.fTraceOn = true;
        System.err.println("about to start resolves");

        timer.Reset();
        timer.Start();

        for (int is = 0; is < nSolvers; ++is) {
            rgsolvers[is].addConstraint(edit1).addConstraint(edit2);
        }
        timer.Stop();
        tmEdit = timer.ElapsedTime();

        timer.Reset();
        timer.Start();
        for (int is = 0; is < nSolvers; ++is) {
            ClSimplexSolver solver = rgsolvers[is];

            for (int m = 0; m < nResolves; m++) {
                solver.resolve(rgpclv[e1Index].getValue() * 1.001, rgpclv[e2Index].getValue() * 1.001);
            }
        }
        timer.Stop();
        tmResolve = timer.ElapsedTime();

        System.err.println("done resolves -- now ending edits");

        timer.Reset();
        timer.Start();
        for (int is = 0; is < nSolvers; ++is) {
            rgsolvers[is].removeConstraint(edit1).removeConstraint(edit2);
        }
        timer.Stop();

        tmEndEdit = timer.ElapsedTime();
        System.out.println("Elapsed time for add:      " + tmAdd + " seconds");
        // System.out.println("Elapsed time for edit:     " + tmEdit + " seconds");
        System.out.println("Elapsed time for resolve:  " + tmResolve + " seconds");
        // System.out.println("Elapsed time for end edit: " + tmEndEdit + " seconds");

        final int mspersec = 1000;
        System.out.println(nCns + "," + nSolvers + "," + nResolves + "," + testNum + "," + tmAdd * mspersec + ","
                + tmEdit * mspersec + "," + tmResolve * mspersec + "," + tmEndEdit * mspersec + ","
                + tmAdd / nCns / nSolvers * mspersec + "," + tmEdit / nSolvers / 2 * mspersec + ","
                + tmResolve / nResolves / nSolvers * mspersec + "," + tmEndEdit / nSolvers / 2 * mspersec);
        return true;
    }

}
