/*
 * AnchorConstraint class.  Anchors a single SelPoint to its current location.
 *
 * $Id: AnchorConstraint.java,v 1.4 1999/12/16 02:10:00 gjb Exp $
 *
 */

package org.klomp.cassowary.awt.constraint;

import java.awt.Color;
import java.awt.Graphics;

import org.klomp.cassowary.CLInternalError;
import org.klomp.cassowary.ClSimplexSolver;
import org.klomp.cassowary.ClStrength;
import org.klomp.cassowary.ConstraintNotFoundException;
import org.klomp.cassowary.RequiredConstraintFailureException;
import org.klomp.cassowary.awt.CDA_G;
import org.klomp.cassowary.awt.component.SelPoint;
import org.klomp.cassowary.clconstraint.ClStayConstraint;

public class AnchorConstraint extends Constraint {
    private ClStayConstraint stayConstrX, stayConstrY;

    public AnchorConstraint(ClSimplexSolver solver, SelPoint sp) {
        super(solver);
        x = (int) sp.X().getValue();
        y = (int) sp.Y().getValue();

        bbox.x = x - 4;
        bbox.y = y - 4;
        bbox.width = 8;
        bbox.height = 17;

        addSelPoint(sp);
        addConstraints();
    }

    @Override
    public void draw(Graphics g) {
        x = (int) selPointList.get(0).X().getValue();
        y = (int) selPointList.get(0).Y().getValue();
        // g.setColor(Color.green);
        g.setColor(Color.red.darker());
        // g.drawLine(x - 5, y - 5, x + 5, y + 5);
        // g.drawLine(x + 5, y - 5, x - 5, y + 5);
        g.drawLine(x, y + 5, x, y + 12);
        g.drawLine(x - 1, y + 6, x + 1, y + 6);
        g.drawArc(x - 4, y + 7, 8, 6, 0, -180);

        // If constraint is selected, draw the box
        if (isSelected) {
            g.setColor(CDA_G.DARK_GREEN);
            g.drawRect(bbox.x, bbox.y, bbox.width, bbox.height);
        } else if (isHighlighted) {
            g.setColor(CDA_G.DARK_BLUE);
            g.drawRect(bbox.x, bbox.y, bbox.width, bbox.height);
            g.drawRect(bbox.x - 1, bbox.y - 1, bbox.width + 2, bbox.height + 2);
        }
    }

    // Add constraints to solver
    @Override
    public void addConstraints() {
        SelPoint sp = selPointList.get(0);
        if (sp == null)
            return;
        if (stayConstrX == null)
            stayConstrX = new ClStayConstraint(sp.X(), ClStrength.required);
        if (stayConstrY == null)
            stayConstrY = new ClStayConstraint(sp.Y(), ClStrength.required);
        try {
            if (stayConstrX != null)
                solver.addConstraint(stayConstrX);
            if (stayConstrY != null)
                solver.addConstraint(stayConstrY);
        } catch (CLInternalError e) {
            System.out.println("AnchorConstraint.aC: CLInternalError!");
        } catch (RequiredConstraintFailureException e) {
            System.out.println("AnchorConstraint.aC: CLRequiredFailure!");
        }
    }

    // Remove constraints from solver
    @Override
    public void removeConstraints() {
        try {
            if (stayConstrX != null)
                solver.removeConstraint(stayConstrX);
            if (stayConstrY != null)
                solver.removeConstraint(stayConstrY);
        } catch (CLInternalError e) {
            System.out.println("AnchorConstraint.rC: CLInternalError!");
        } catch (ConstraintNotFoundException e) {
            System.out.println("AnchorConstraint.rC: CLConstraintNotFound!");
        }
        stayConstrX = null;
        stayConstrY = null;
    }

    // Convert object to a string
    @Override
    public String toString() {
        SelPoint sp = selPointList.get(0);
        return new String("Anchor:" + sp);
    }

    // Replace a given SelPoint with another. Needs to update the stay
    // constraints. For an anchor constraint, there had better only be one
    // point in the list!
    @Override
    public void replaceSelPoint(SelPoint oldsp, SelPoint newsp) {
        if (selPointList.size() != 1) {
            /*
             * System.out.print("Error--" + selPointList.size() + " points in list");
             * System.out.println(" for an anchor constraint! (vs 1 required)");
             */
            return;
        }

        SelPoint sp;
        int a;

        for (a = 0; a < selPointList.size(); a++) {
            sp = selPointList.get(a);
            if (sp == oldsp) {
                // Remove old constraints
                removeConstraints();
                // Replace element
                selPointList.set(a, newsp);
                // Update constraints
                addConstraints();
            }
        }
    }

    // An anchor constraint is valid unless it is no longer associated with
    // a SelPoint.
    @Override
    public boolean canDiscard() {
        SelPoint sp;
        if (selPointList.size() < 1)
            return true;

        sp = selPointList.get(0);

        if (sp == null)
            return true;

        return false;
    }

}

/*
 * $Log: AnchorConstraint.java,v $ Revision 1.4 1999/12/16 02:10:00 gjb * java/cda/Makefile.am: Put Constraint/*, Main/* files
 * into the distribution and build with them.
 * 
 * * java/demos/*.java: Move everything into the EDU.Washington.grad.gjb.cassowary_demos package.
 * 
 * * java/cda/classes/run.html, java/demos/quaddemo.htm: Fix nl convention, name class explicitly w/ package in front, w/o
 * trailing .class.
 * 
 * * java/cda/**: Move everything into the EDU.Washington.grad.noth.cda package.
 * 
 * Revision 1.3 1998/06/23 02:08:46 gjb Added import of cassowary package so that the cda doesn't need to be in the same package
 * as the solver.
 * 
 * Revision 1.2 1998/05/09 00:30:26 gjb Remove cr-s
 * 
 * Revision 1.1 1998/05/09 00:11:00 gjb Added
 * 
 * Revision 1.5 1998/04/20 09:52:44 Michael Added code for selection/highlighting
 * 
 * Revision 1.4 1998/04/01 10:20:36 Michael Added methods for new functionality
 * 
 * Revision 1.3 1998/03/27 06:29:37 Michael Added toString method
 */
