/*
 * Rectangle constrained component.  Corners are moveable, sides are 
 * at right angles and are vertical/horizontal.
 *
 * $Id: RectangleCC.java,v 1.4 1999/12/16 02:09:59 gjb Exp $
 *
 */

package org.klomp.cassowary.awt.component;

import java.awt.Color;
import java.awt.Graphics;

import org.klomp.cassowary.CLInternalError;
import org.klomp.cassowary.ClLinearExpression;
import org.klomp.cassowary.ClSimplexSolver;
import org.klomp.cassowary.ConstraintNotFoundException;
import org.klomp.cassowary.RequiredConstraintFailureException;
import org.klomp.cassowary.clconstraint.ClLinearEquation;

public class RectangleCC extends ConstrComponent {
    // Constrainable parts are the corners
    public SelPoint c1, c2, c3, c4;

    // Constraints on the corners
    ClLinearEquation c1c2yConstr, c2c3xConstr, c3c4yConstr, c1c4xConstr;

    // Additional fields for drawing
    Color c;

    // Constructor
    public RectangleCC(ClSimplexSolver solver) {
        this(solver, 10, 50, 400, 400);
    }

    // Default constructor, taking initial (x, y) value and window border
    public RectangleCC(ClSimplexSolver solver, int sx, int sy, int r, int b) {
        super(solver);
        c1 = new SelPoint(solver, sx, sy, r, b);
        c2 = new SelPoint(solver, sx + 30, sy, r, b);
        c3 = new SelPoint(solver, sx + 30, sy + 20, r, b);
        c4 = new SelPoint(solver, sx, sy + 20, r, b);
        c = Color.black;

        // Add endpoints and midpoint to selectable point vector
        selPoints.add(c1);
        selPoints.add(c2);
        selPoints.add(c3);
        selPoints.add(c4);

        // Set owner of corners to this CC
        c1.setOwner(this);
        c2.setOwner(this);
        c3.setOwner(this);
        c4.setOwner(this);
        c1.addInterestedCC(this);
        c2.addInterestedCC(this);
        c3.addInterestedCC(this);
        c4.addInterestedCC(this);

        // Establish constraints on corners
        c1c2yConstr = null;
        c2c3xConstr = null;
        c3c4yConstr = null;
        c1c4xConstr = null;
        establishCornerConstraints();
    }

    // The bottom-right corner (c3) is the end SP for placement purposes
    @Override
    public SelPoint getEndSP() {
        return c3;
    }

    // Initialize and establish constraints on the corners of the rectangle.
    // Constraints are: c1.y == c2.y
    // c2.x == c3.x
    // c3.y == c4.y
    // c1.x == c4.x
    // Old constraints will *not* be removed by this method!
    private void establishCornerConstraints() {
        ClLinearExpression cle;
        try {
            cle = new ClLinearExpression(c1.Y());
            c1c2yConstr = new ClLinearEquation(c2.Y(), cle);
            solver.addConstraint(c1c2yConstr);
            cle = new ClLinearExpression(c2.X());
            c2c3xConstr = new ClLinearEquation(c3.X(), cle);
            solver.addConstraint(c2c3xConstr);
            cle = new ClLinearExpression(c3.Y());
            c3c4yConstr = new ClLinearEquation(c4.Y(), cle);
            solver.addConstraint(c3c4yConstr);
            cle = new ClLinearExpression(c1.X());
            c1c4xConstr = new ClLinearEquation(c4.X(), cle);
            solver.addConstraint(c1c4xConstr);
        } catch (RequiredConstraintFailureException e) {
            System.out.println("RectangleCC.constructor: ExCLRequiredFailure!");
        } catch (CLInternalError e) {
            System.out.println("RectangleCC.constructor: ExCLInternalError!");
        }
    }

    // Remove the corner constraints, if any
    private void removeCornerConstraints() {
        try {
            if (c1c2yConstr != null)
                solver.removeConstraint(c1c2yConstr);
            if (c2c3xConstr != null)
                solver.removeConstraint(c2c3xConstr);
            if (c3c4yConstr != null)
                solver.removeConstraint(c3c4yConstr);
            if (c1c4xConstr != null)
                solver.removeConstraint(c1c4xConstr);
        } catch (CLInternalError e) {
            System.out.println("RectangleCC.remMPC: CLInternalError!");
        } catch (ConstraintNotFoundException e) {
            System.out.println("RectangleCC.remMPC: CLConstraintNotFound!");
        }

        c1c2yConstr = null;
        c2c3xConstr = null;
        c3c4yConstr = null;
        c1c4xConstr = null;
    }

    // Implementation of draw method
    @Override
    public void draw(Graphics g) {
        super.draw(g);
        if (!isSelected && !isHighlighted) {
            // Don't redraw the box sides, as the bbox of the rectangle was drawn
            // by the base class
            g.setColor(c);
            g.drawLine(c1.x, c1.y, c2.x, c2.y);
            g.drawLine(c2.x, c2.y, c3.x, c3.y);
            g.drawLine(c3.x, c3.y, c4.x, c4.y);
            g.drawLine(c1.x, c1.y, c4.x, c4.y);
        }
    }

    // Performs necessary updates when the SelPoint at index idx is to be
    // replaced with newsp.
    @Override
    protected void notifySelPointReplacement(int idx, SelPoint newsp) {
        SelPoint sp = selPoints.get(idx);

        if (c1 == sp) {
            c1 = newsp;
        }

        if (c2 == sp) {
            c2 = newsp;
        }

        if (c3 == sp) {
            c3 = newsp;
        }

        if (c4 == sp) {
            c4 = newsp;
        }

        // Need to update constraints at this point
        removeCornerConstraints();
        establishCornerConstraints();
    }

    // Clean-up function
    @Override
    public void cleanUp() {
        removeCornerConstraints();
    }

    // Return a string version of the component
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("RectangleCC: ");
        sb.append("[" + c1 + ", " + c2 + ", " + c3 + ", " + c4 + "]");
        return sb.toString();
    }
}

/*
 * $Log: RectangleCC.java,v $ Revision 1.4 1999/12/16 02:09:59 gjb * java/cda/Makefile.am: Put Constraint/*, Main/* files into the
 * distribution and build with them.
 * 
 * * java/demos/*.java: Move everything into the EDU.Washington.grad.gjb.cassowary_demos package.
 * 
 * * java/cda/classes/run.html, java/demos/quaddemo.htm: Fix nl convention, name class explicitly w/ package in front, w/o
 * trailing .class.
 * 
 * * java/cda/**: Move everything into the EDU.Washington.grad.noth.cda package.
 * 
 * Revision 1.3 1998/06/23 02:08:40 gjb Added import of cassowary package so that the cda doesn't need to be in the same package
 * as the solver.
 * 
 * Revision 1.2 1998/05/09 00:30:21 gjb Remove cr-s
 * 
 * Revision 1.1 1998/05/09 00:10:57 gjb Added
 * 
 * Revision 1.5 1998/04/15 00:08:57 Michael Changed drawing of rectangle so selection/highlighting is visible
 * 
 * Revision 1.4 1998/04/10 01:59:11 Michael Added getEndSP method
 * 
 * Revision 1.3 1998/04/08 05:40:42 Michael Commented out printing msgs
 * 
 * Revision 1.2 1998/04/01 10:11:42 Michael Added cleanUp function, code to set interested CC's
 * 
 * Revision 1.1 1998/03/19 09:14:13 Michael Initial check-in
 */

