/*
 * DrawPanel class.  The panel in which all drawing occurs.
 *
 * Mouse interface:
 * Mouse Down: If cursor selects something not already selected, add it
 *  to selection list (replace/augment sel. list)
 *  If cursor selects something already selected, do nothing
 *  If cursor does not select anything, store location for rubber-band sel box
 * Mouse Drag: If drawing rubber-band box, extend it to current location.
 *  Otherwise, pass deltas along to components and solver.
 *
 * Interaction with solver:
 * When the first drag event occurs, the set of selected points is traversed
 * and edit constraints are added.
 * For every drag event, a set of point deltas are computed during a 
 * traversal in the same order, resolve is then invoked on the resulting
 * list, and then the list is used to update the points.
 * When a mouse button up event occurs, edit constraints are removed.
 *
 * $Id: DrawPanel.java,v 1.5 1999/12/16 02:10:01 gjb Exp $
 *
 */

package org.klomp.cassowary.awt;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.List;

import org.klomp.cassowary.CLInternalError;
import org.klomp.cassowary.ClSimplexSolver;
import org.klomp.cassowary.awt.component.CircleCC;
import org.klomp.cassowary.awt.component.ConstrComponent;
import org.klomp.cassowary.awt.component.EditConstantList;
import org.klomp.cassowary.awt.component.LineCC;
import org.klomp.cassowary.awt.component.MidpointLineCC;
import org.klomp.cassowary.awt.component.RectangleCC;
import org.klomp.cassowary.awt.component.SelPoint;
import org.klomp.cassowary.awt.constraint.AboveConstraint;
import org.klomp.cassowary.awt.constraint.AlignmentConstraint;
import org.klomp.cassowary.awt.constraint.AnchorConstraint;
import org.klomp.cassowary.awt.constraint.BelowConstraint;
import org.klomp.cassowary.awt.constraint.ColocationConstraint;
import org.klomp.cassowary.awt.constraint.Constraint;
import org.klomp.cassowary.awt.constraint.LeftOfConstraint;
import org.klomp.cassowary.awt.constraint.RightOfConstraint;

class DrawPanel extends Panel {
    DPMouseMotionListener drawPanelMML;
    DPKeyListener drawPanelKL;

    // Flag indicating we're placing a component (starting or end SP)
    // and its type
    boolean placingComponent;
    String placingComponentType;
    boolean placingComponentEndSP;

    // Flag indicating we're picking a target CC for a constraint
    boolean pickingTargetCC;
    String relationalConstrType;

    int desiredWidth, desiredHeight;

    // Initial position of a mouse drag, updated whenever a mouse down occurs
    Point mouseStart, mouseDelta;

    // Flag used to determine when the first drag event has occurred, and thus
    // when the edit constraints need to be established.
    boolean firstDrag;
    int numEC;

    // List of active components
    List<ConstrComponent> curCC;

    // List of active Constraint objects
    List<Constraint> curConstr;

    // The instance of the Cassowary solver. The DrawPanel owns it.
    ClSimplexSolver solver;

    // Offset into the array of SP's at a given point. Used for cycling
    // through stacked SP's w/ repeated mouse clicks
    private int spListOffset;

    // List of edit move constraint values. Owned by DrawPanel, but passed
    // into CC's so the target edit constant values can be collected into one
    // place in a reasonably clean and efficient manner
    EditConstantList editConstantList;

    // The selection box and associated flag
    Rectangle selectionBox;
    boolean drawingSelectionBox;
    int selectionBoxStartx, selectionBoxStarty;

    // Variables for off-screen rendering (for double-buffering)
    Graphics paintBufferGraphics;
    Image paintBuffer;
    ImageObserver paintBufferObserver;

    // Creation function
    Panel create(LayoutManager layout) {
        return this;
    }

    // Constructor
    DrawPanel() {
        this.solver = new ClSimplexSolver();

        drawPanelMML = new DPMouseMotionListener(this);
        drawPanelKL = new DPKeyListener(this);
        mouseStart = new Point();
        mouseDelta = new Point();

        firstDrag = true;
        numEC = 0;

        addMouseMotionListener(drawPanelMML);
        addMouseListener(drawPanelMML);
        addKeyListener(drawPanelKL);

        desiredWidth = 600;
        desiredHeight = 500;

        curCC = new ArrayList<ConstrComponent>(10);

        curConstr = new ArrayList<Constraint>(10);

        placingComponent = false;
        placingComponentEndSP = false;
        placingComponentType = null;

        pickingTargetCC = false;
        relationalConstrType = null;

        editConstantList = new EditConstantList(10);

        selectionBox = new Rectangle();
        drawingSelectionBox = false;

        paintBufferGraphics = null;
        paintBuffer = null;
        paintBufferObserver = null;

        spListOffset = 0;
    }

    // Mouse position update function, used w/ drag events
    public void mouseStart(Point p) {
        mouseStart.x = p.x;
        mouseStart.y = p.y;
    }

    public void setDesiredSize(int dW, int dH) {

        desiredWidth = dW;
        desiredHeight = dH;
        this.setSize(desiredWidth, desiredHeight);

        // Also update within-window constraints on all SelPoints
        setBorderConstraints();

        // Force a re-solve
        resatisfyConstraints();

        // Discard the off-screen buffer; the next repaint will generate a new one
        paintBuffer = null;
        if (paintBufferGraphics != null)
            paintBufferGraphics.dispose();
        paintBufferGraphics = null;
    }

    // (re) Set border constraints for all CC's
    public void setBorderConstraints() {
        int a;
        ConstrComponent c;
        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.setBorderConstraints(desiredWidth, desiredHeight);
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, 100);
    }

    // Helper method used by paint and update. Renders into off-screen buffer.
    public void renderToBuffer() {
        Dimension d = this.getSize();
        int a;
        ConstrComponent c;

        if (paintBuffer == null) {
            paintBuffer = createImage(d.width, d.height);
            paintBufferGraphics = paintBuffer.getGraphics();
        }

        paintBufferGraphics.setColor(Color.white);
        paintBufferGraphics.fillRect(0, 0, d.width, d.height);
        // paintBufferGraphics.clearRect(0, 0, d.width, d.height);
        paintBufferGraphics.setColor(Color.black);
        paintBufferGraphics.drawRect(0, 0, d.width - 1, d.height - 1);

        // Draw every CC
        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.draw(paintBufferGraphics);
        }

        // Draw every Constraint
        Constraint constr;
        for (a = 0; a < curConstr.size(); a++) {
            constr = curConstr.get(a);
            constr.draw(paintBufferGraphics);
        }

        // Draw the selection box, if any
        if (drawingSelectionBox) {
            paintBufferGraphics.setColor(Color.lightGray);
            paintBufferGraphics.drawRect(selectionBox.x, selectionBox.y, selectionBox.width, selectionBox.height);
        }
    }

    // Update method, called by repaint(). Renders into the buffer.
    @Override
    public void update(Graphics g) {
        renderToBuffer();
        paint(g);
        // g.drawImage(paintBuffer, 0, 0, paintBufferObserver);
    }

    // Paint method. Blits the buffer to the screen.
    @Override
    public void paint(Graphics g) {
        if (paintBuffer == null) {
            // Generate the image first
            renderToBuffer();
        }
        g.drawImage(paintBuffer, 0, 0, paintBufferObserver);
    }

    // Helper function to build a list of all selected CC's
    // (based solely on isSelected flag of a CC)
    public List<ConstrComponent> getAllSelectedCC() {
        List<ConstrComponent> v = new ArrayList<ConstrComponent>(4);

        for (ConstrComponent c : curCC) {
            if (c.getisSelected())
                v.add(c);
        }
        return v;
    }

    // Helper function to get all selected constraint objs (based solely on
    // the isSelected flag)
    public List<Constraint> getAllSelectedConstr() {

        List<Constraint> retv = new ArrayList<Constraint>(4);

        for (Constraint c : curConstr) {
            if (c.getisSelected())
                retv.add(c);
        }

        return retv;
    }

    // Helper function to build a list of all selected SelPoints
    public List<SelPoint> getAllSelectedSelPoints() {
        List<SelPoint> allSelPoints = new ArrayList<SelPoint>(10);

        for (int a = 0; a < curCC.size(); a++) {
            ConstrComponent c = curCC.get(a);
            List<SelPoint> v = c.getAllSelectedSelPoints();
            /*
             * System.out.println("DP.getAllSelSP: getAllSelSP for CC " + c + " = " + v);
             */
            for (int nsp = 0; nsp < v.size(); nsp++) {
                SelPoint sp = v.get(nsp);
                /*
                 * System.out.println("DP.getAllSelSP: Considering " + sp + " to add to " + allSelPoints);
                 */
                if (!allSelPoints.contains(sp))
                    allSelPoints.add(sp);
            }
        }

        return allSelPoints;
    }

    // Create a new constraint of the given type
    public void createNewConstraint(String cc) {
        int a;

        // All selected SelPoints
        List<SelPoint> allSelPoints = null;
        SelPoint sp;
        ConstrComponent constrc;

        if (cc.equals("Anchor")) {
            // Apply an anchor constraint to every selected point
            allSelPoints = getAllSelectedSelPoints();

            /*
             * System.out.println("allSelPoints = " + allSelPoints);
             */

            Constraint ac;
            for (a = 0; a < allSelPoints.size(); a++) {
                sp = allSelPoints.get(a);
                ac = new AnchorConstraint(solver, sp);
                curConstr.add(ac);
                // Associate each SelPoint w/ its constraint
                sp.addInterestedConstr(ac);
            }

            repaint();
            return;
        }
        if (cc.equals("Colocation")) {
            // For every pair of points, apply a colocation constraint
            allSelPoints = getAllSelectedSelPoints();

            establishColocationConstraint(allSelPoints);

            repaint();
            return;
        }

        if (cc.equals("Above")) {
            // Must be at least one source CC relative to which the target
            // is to be constrained
            if (getAllSelectedCC().size() == 0) {
                System.out.println("Need at least one selected CC as source");
                return;
            }
            // Prompt user to pick a CC to be constrained above the others
            pickTargetCC("Above");

            repaint();
            return;
        }

        if (cc.equals("Below")) {
            // Must be at least one source CC relative to which the target
            // is to be constrained
            if (getAllSelectedCC().size() == 0) {
                System.out.println("Need at least one selected CC as source");
                return;
            }
            // Prompt user to pick a CC to be constrained below the others
            pickTargetCC("Below");

            repaint();
            return;
        }

        if (cc.equals("LeftOf")) {
            // Must be at least one source CC relative to which the target
            // is to be constrained
            if (getAllSelectedCC().size() == 0) {
                System.out.println("Need at least one selected CC as source");
                return;
            }
            // Prompt user to pick a CC to be constrained left of the others
            pickTargetCC("LeftOf");

            repaint();
            return;
        }

        if (cc.equals("RightOf")) {
            // Must be at least one source CC relative to which the target
            // is to be constrained
            if (getAllSelectedCC().size() == 0) {
                System.out.println("Need at least one selected CC as source");
                return;
            }
            // Prompt user to pick a CC to be constrained right of the others
            pickTargetCC("RightOf");

            repaint();
            return;
        }

        if (cc.equals("LeftAlign")) {
            List<ConstrComponent> v = getAllSelectedCC();
            // Need at least 2 CC's to align
            if (v.size() < 2) {
                System.out.println("Need at least 2 selected CCs to align");
                return;
            }
            Constraint ac;
            ac = new AlignmentConstraint(solver, v, AlignmentConstraint.LEFT_ALIGN);

            // Make sure each CC cares about the constraint
            for (a = 0; a < v.size(); a++) {
                constrc = v.get(a);
                constrc.addInterestedConstr(ac);
            }
            curConstr.add(ac);
            resatisfyConstraints();

            repaint();
            return;
        }

        if (cc.equals("TopAlign")) {
            List<ConstrComponent> v = getAllSelectedCC();
            // Need at least 2 CC's to align
            if (v.size() < 2) {
                System.out.println("Need at least 2 selected CCs to align");
                return;
            }
            Constraint ac;
            ac = new AlignmentConstraint(solver, v, AlignmentConstraint.TOP_ALIGN);

            // Make sure each CC cares about the constraint
            for (a = 0; a < v.size(); a++) {
                constrc = v.get(a);
                constrc.addInterestedConstr(ac);
            }
            curConstr.add(ac);
            resatisfyConstraints();

            repaint();
            return;
        }

        if (cc.equals("RightAlign")) {
            List<ConstrComponent> v = getAllSelectedCC();
            // Need at least 2 CC's to align
            if (v.size() < 2) {
                System.out.println("Need at least 2 selected CCs to align");
                return;
            }
            Constraint ac;
            ac = new AlignmentConstraint(solver, v, AlignmentConstraint.RIGHT_ALIGN);

            // Make sure each CC cares about the constraint
            for (a = 0; a < v.size(); a++) {
                constrc = v.get(a);
                constrc.addInterestedConstr(ac);
            }
            curConstr.add(ac);
            resatisfyConstraints();

            repaint();
            return;
        }

        if (cc.equals("BottomAlign")) {
            List<ConstrComponent> v = getAllSelectedCC();
            // Need at least 2 CC's to align
            if (v.size() < 2) {
                System.out.println("Need at least 2 selected CCs to align");
                return;
            }
            Constraint ac;
            ac = new AlignmentConstraint(solver, v, AlignmentConstraint.BOTTOM_ALIGN);

            // Make sure each CC cares about the constraint
            for (a = 0; a < v.size(); a++) {
                constrc = v.get(a);
                constrc.addInterestedConstr(ac);
            }
            curConstr.add(ac);
            resatisfyConstraints();

            repaint();
            return;
        }

        System.out.println("Unknown Constraint type " + cc);
    }

    // Begin the process of picking a target CC for an alignment constraint
    public void pickTargetCC(String constName) {
        /*
         * System.out.println("DP.pickTargCC: " + constName);
         */
        pickingTargetCC = true;
        relationalConstrType = constName;
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // Stop picking a target CC
    public void stopPickingTargetCC() {
        /*
         * System.out.println("DP.stopPickTargCC invoked");
         */
        pickingTargetCC = false;
        relationalConstrType = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    // Establish a relational constraint between all selected CC's and the
    // specified target CC
    public void establishRelationalConstr(ConstrComponent target) {
        List<ConstrComponent> v = getAllSelectedCC();
        Constraint rc;

        if (v.contains(target)) {
            /*
             * System.out.println("DP.estRelConstr: Selected CC list contains target!");
             */
            stopPickingTargetCC();
            return;
        }

        // For each selected CC, establish a relational constraint
        for (ConstrComponent c : v) {
            if (relationalConstrType.equals("Above"))
                rc = new AboveConstraint(solver, c, target);
            else if (relationalConstrType.equals("Below"))
                rc = new BelowConstraint(solver, c, target);
            else if (relationalConstrType.equals("LeftOf"))
                rc = new LeftOfConstraint(solver, c, target);
            else if (relationalConstrType.equals("RightOf"))
                rc = new RightOfConstraint(solver, c, target);
            else {
                System.out.println("DP.estRelConstr: Unknown type " + relationalConstrType);
                stopPickingTargetCC();
                return;
            }
            curConstr.add(rc);
        }

        // Done w/relational constraint, so don't need to pick it anymore
        stopPickingTargetCC();
        resatisfyConstraints();
        // Update the edit constants and BBoxes of all components
        // updateEditConstants();
    }

    // Establish a colocation constraint on a vector of SelPoints.
    // Does not allow a constraint to be established if any in stack
    // belong to the same CC.
    private void establishColocationConstraint(List<SelPoint> allSelPoints) {
        int a;
        List<ConstrComponent> CCofStack;
        SelPoint basesp, sp;

        if (allSelPoints.size() < 2) {
            // Can't have colocation constraint with only 1 point!
            return;
        }

        CCofStack = new ArrayList<ConstrComponent>(4);
        for (a = 0; a < allSelPoints.size(); a++) {
            sp = allSelPoints.get(a);
            for (ConstrComponent constrc : sp.getInterestedCC()) {
                if (CCofStack.contains(constrc)) {
                    /*
                     * System.out.println("CC " + constrc + " shared in stack!");
                     */
                    return;
                } else {
                    CCofStack.add(constrc);
                }
            }
        }

        /*
         * // Arbitrarily let the first selected SP be the base basesp = (SelPoint) allSelPoints.get(0);
         */

        // Scan the vector of points and pick the best one to use as the base.
        // Currently, choose any point w/ an anchor constraint
        basesp = null;
        for (a = 0; a < allSelPoints.size(); a++) {
            sp = allSelPoints.get(a);
            for (Constraint c : sp.getInterestedConstr()) {
                if (c instanceof AnchorConstraint) {
                    if (basesp != null) {
                        System.out.println("Two points to merge have anchor constraints:");
                        System.out.println(basesp + " and " + sp);
                    }
                    basesp = sp;
                }
            }
        }

        if (basesp == null) {
            // Just pick first point as base
            basesp = allSelPoints.get(0);
        }

        // Add a colocation constraint obj. to the base point, if it doesn't
        // already have one
        ColocationConstraint coloc = null;
        for (Constraint cons : basesp.getInterestedConstr()) {
            if (cons instanceof ColocationConstraint) {
                coloc = (ColocationConstraint) cons;
                break;
            }
        }
        if (coloc == null) {
            coloc = new ColocationConstraint(solver, basesp);
            basesp.addInterestedConstr(coloc);
            curConstr.add(coloc);
            for (ConstrComponent constrc : basesp.getInterestedCC()) {
                coloc.addCC(constrc);
                constrc.addInterestedConstr(coloc);
            }
        }

        // For each other SelPoint, notify any CC's and constraints that
        // care about it that it is being replaced
        for (a = 0; a < allSelPoints.size(); a++) {
            sp = allSelPoints.get(a);
            if (sp == basesp) {
                // Skip the base SP
                continue;
            }
            for (ConstrComponent constrc : sp.getInterestedCC()) {
                constrc.replaceSelPoint(sp, basesp);
                constrc.addInterestedConstr(coloc);
                coloc.addCC(constrc);
            }

            for (Constraint c : sp.getInterestedConstr()) {
                c.replaceSelPoint(sp, basesp);
            }
            // We can also discard the replaced point
            sp.cleanUp();
        }

        // Every CC that was affected by the colocation constraint needs to
        // have its bounding box updated
        for (ConstrComponent constrc : basesp.getInterestedCC()) {
            constrc.updateEditConstants();
        }

        // We also need to scan all constraints in case multiple colocation constrs
        // got merged.
        List<Constraint> v = new ArrayList<Constraint>(4);
        for (Constraint c : curConstr) {
            if (c.canDiscard()) {
                c.cleanUp();
                v.add(c);
            }
        }
        for (Constraint c : v) {
            curConstr.remove(c);
        }
    }

    // Create a new CC of the given type. Process unselects all selections.
    public void createNewCC(String cc) {
        /*
         * System.out.println("DP.createNCC: " + cc);
         */

        unselectAll();
        startPlacingComponent(cc);
    }

    // Start the process of placing a component of given type
    public void startPlacingComponent(String cc) {
        placingComponent = true;
        placingComponentType = cc;
        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }

    // Start the process of placing a component's end SP
    // Select the end SP of the component so it can be dragged around,
    // resetting the "drag start" position so the right thing happens.
    public void startPlacingComponentEndSP(ConstrComponent cc) {
        // Select end SP
        SelPoint endSP = cc.getEndSP();
        unselectAll();
        endSP.setSelected();
        // Set the end SP location as the "start" of the drag
        mouseStart(new Point(endSP.x, endSP.y));
    }

    // Stop the process of placing a component
    public void stopPlacingComponent() {
        placingComponent = false;
        placingComponentType = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    // Add a new CC to the panel
    public void addConstrComponent(ConstrComponent c) {
        curCC.add(c);
        // Update the new component's bbox
        c.updateBoundingBox();
        repaint();
    }

    // Remove specified CC from panel.
    public void removeConstrComponent(ConstrComponent c) {

        if (curCC.contains(c)) {
            curCC.remove(c);
            c.setBorderConstraints(desiredWidth, desiredHeight);
            // Also notify all of c's constraints and interested components about
            // its removal
        } else {
            System.out.println("CC " + c + " not found");
        }
    }

    // Helper function to display app info
    public void displayAppInfo() {
        System.out.println(curCC.size() + " ConstrComponent objects:");
        for (int a = 0; a < curCC.size(); a++)
            System.out.println(curCC.get(a));
        System.out.println(curConstr.size() + " Constraint objects:");
        for (int a = 0; a < curConstr.size(); a++)
            System.out.println(curConstr.get(a));

        System.out.println("Solver info:");
        System.out.println(solver);
        System.out.println(solver.getInternalInfo());
    }

    // Helper function to display more detailed app. info
    // For now, just display full version of all selected SP's
    public void displayFullInfo() {
        int a;
        List<SelPoint> v = getAllSelectedSelPoints();
        SelPoint sp;

        System.out.println("Full info on all selected SelPoints:");
        for (a = 0; a < v.size(); a++) {
            sp = v.get(a);
            System.out.println(sp.fullInfo());
        }
    }

    // Remove everything from the panel (all groups, constraints, etc)
    @Override
    public void removeAll() {
        // Remove all constraints
        // Remove all CC's
        for (ConstrComponent cc : curCC) {
            // Notify associated constraint objs and CC's that it is going away
            // MORE WORK HERE
        }
        curCC.clear();

        for (Constraint constr : curConstr) {
            constr.removeConstraints();
        }
        curConstr.clear();

        // Redraw display
        repaint();
    }

    // Delete the selected CC(s) and constraint objs
    public void deleteCC() {
        List<ConstrComponent> selCC;
        List<Constraint> selConstr;
        List<ConstrComponent> keepCC;
        ConstrComponent c, iC;
        Constraint constr, aConstr;
        int a, b, d, j;
        SelPoint repsp;

        // Build a list of all components that are selected
        selCC = getAllSelectedCC();

        /*
         * System.out.println("DP.delCC: selCC's = " + selCC);
         */

        // For each selected CC, remove it.
        for (a = 0; a < selCC.size(); a++) {
            c = selCC.get(a);

            // First, notify each constraint that cares about the component that the
            // CC is going away.
            List<Constraint> v = c.getInterestedConstr();
            /*
             * System.out.println("For CC " + c + ", interested Constrs: " + v);
             */
            for (b = 0; b < v.size(); b++) {
                constr = v.get(b);
                constr.notifyCCRemoval(c);
                // If the constraint can be discarded, do so now.
                if (constr.canDiscard()) {
                    constr.cleanUp();
                    curConstr.remove(constr);
                    c.removeInterestedConstr(constr);
                }
            }

            // See if each SelPoint of the component can be discarded
            // MORE WORK HERE
            for (SelPoint sp : c.selPoints) {
                // Each SP loses interest in CC about to go away
                sp.removeInterestedCC(c);

                if (sp.getInterestedCC().size() == 0) {
                    // SelPoints must be associated with 1+ CC's, so an "orphan"
                    // can delete any constraint objs. associated w/ it.
                    /*
                     * System.out.println("DP.delCC: No CC's interested in SP " + sp.fullInfo() + ", removing");
                     */
                    v = sp.getInterestedConstr();
                    for (d = 0; d < v.size(); d++) {
                        constr = v.get(d);
                        /*
                         * System.out.println("Removing " + constr);
                         */
                        constr.cleanUp();
                        curConstr.remove(constr);
                    }
                    sp.cleanUp();
                }
            }

            curCC.remove(c);
            c.cleanUp();
        }

        // Get all selected Constraint objs.
        selConstr = getAllSelectedConstr();
        /*
         * System.out.println("DP.delCC: Selected Constraints = " + selConstr);
         */

        // For each selected constraint, remove it.
        for (a = 0; a < selConstr.size(); a++) {
            constr = selConstr.get(a);

            // If the constraint that is being deleted is a colocation
            // constraint, clone the point so that every interested CC gets its own
            // copy.
            if (constr instanceof ColocationConstraint) {
                List<ConstrComponent> v = constr.ccList;
                SelPoint sp = constr.selPointList.get(0);
                for (b = 1; b < v.size(); b++) {
                    c = v.get(b);
                    repsp = sp.clone();
                    /*
                     * // Each replacement SP should not be interested in the constraint // that is going away...
                     * repsp.removeInterestedConstr(constr);
                     */
                    // Remove all anchor constraints from a cloned pt.
                    // ...also remove interest in any CC's where the coloc. constr.
                    // was the only reason for interest

                    /*
                     * System.out.println("DP.delCC: repsp " + repsp + " interCC = " + repsp.interCC);
                     */

                    {
                        // Get all constraint objs. interested in point
                        List<Constraint> iCon = repsp.getInterestedConstr();
                        keepCC = new ArrayList<ConstrComponent>(4);
                        // Build list of CC's that repsp should keep interest in
                        for (d = 0; d < iCon.size(); d++) {
                            aConstr = iCon.get(d);
                            for (j = 0; j < aConstr.ccList.size(); j++) {
                                iC = aConstr.ccList.get(j);
                                if (!keepCC.contains(iC))
                                    keepCC.add(iC);
                            }
                        }
                    }
                    {
                        // For each CC that the to-be-deleted constraint was interested in,
                        // remove repsp's interest unless it's in the keepCC list.
                        List<ConstrComponent> iCon = repsp.getInterestedCC();
                        for (d = 0; d < iCon.size(); d++) {
                            iC = iCon.get(d);
                            if (!keepCC.contains(iC)) {
                                repsp.removeInterestedCC(iC);
                            }
                        }
                    }
                    c.replaceSelPoint(sp, repsp);
                }
            }
            // Remove this constraint from every SP interested in it

            for (SelPoint sp : constr.selPointList) {
                sp.removeInterestedConstr(constr);
            }
            constr.selPointList.clear();

            {
                List<ConstrComponent> v = constr.ccList;
                for (b = 0; b < v.size(); b++) {
                    c = v.get(b);
                    c.removeInterestedConstr(constr);
                }
                constr.ccList.clear();
            }
            if (constr.canDiscard()) {
                constr.cleanUp();
                curConstr.remove(constr);
            } else {
                System.out.println("DP.delCC: Trying to delete constr " + constr + " but canDiscard() = false!");
            }
        }

        // Re-establish border constraints on CC's, to catch cloned pts.
        setBorderConstraints();

        repaint();
    }

    // Unselect all components
    public void unselectAll() {
        int a;
        ConstrComponent c;
        Constraint constr;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.unselect();
        }

        for (a = 0; a < curConstr.size(); a++) {
            constr = curConstr.get(a);
            constr.unselect();
        }
    }

    // ///////////////////////////////////////////////////////////////////////
    // Methods related to event-handling

    public void notifyMousePosition(MouseEvent e) {
        int a;
        ConstrComponent c;
        Constraint constr;
        Point p = e.getPoint();
        boolean isShiftDown = e.isShiftDown() || pickingTargetCC;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.highlight(p, isShiftDown);
        }

        for (a = 0; a < curConstr.size(); a++) {
            constr = curConstr.get(a);
            constr.highlight(p, isShiftDown);
        }

        // Need to repaint to show current highlighting status
        repaint();
    }

    // Notify CC's of a mouse drag movement delta
    public void notifyDeltaMove() {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.moveBy(mouseDelta, editConstantList);
        }
    }

    // Have each CC update its internal state
    public void updateEditConstants() {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.updateEditConstants();
        }
    }

    // Remove edit constraints from each CC
    public void removeAllEditConstraints() {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.removeEditConstraints();
        }
    }

    // Inform each CC of a possible selection
    public void select(Point p, boolean performMultipleSelect) {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.select(p, performMultipleSelect);
        }
    }

    public void select(Rectangle b, boolean performMultipleSelect) {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.select(b, performMultipleSelect);
        }
    }

    // Get a vector of all SP's of any CC at given point p. Modifies
    // retv vector in place.
    public void getSelPointsAt(Point p, List<SelPoint> retv) {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.getSelPointsAt(p, retv);
        }
    }

    // Highlight contents of selection box
    public void highlightSelectionBox() {
        int a;
        ConstrComponent c;

        for (a = 0; a < curCC.size(); a++) {
            c = curCC.get(a);
            c.highlight(selectionBox);
        }
    }

    // Get vector of CC's whose bounding box encloses the given point.
    public List<ConstrComponent> getCCAtPoint(Point p) {
        List<ConstrComponent> retv = new ArrayList<ConstrComponent>(4);

        for (ConstrComponent c : curCC) {
            if (c.bbox.contains(p))
                retv.add(c);
        }

        return retv;
    }

    // Get vector of Constraint objs whose bounding box encloses the given pt.
    // Requires the shift key be held down while clicking.
    public List<Constraint> getConstrAtPoint(Point p, boolean isShiftDown) {
        List<Constraint> retv = new ArrayList<Constraint>(4);

        if (!isShiftDown) {
            // Can't select a constraint obj w/out shift key
            return retv;
        }

        for (Constraint c : curConstr) {
            if (c.bbox.contains(p))
                retv.add(c);
        }

        return retv;
    }

    // Select any CC's. Notify CC of a selection at point p.
    // Return vector of selected component(s).
    public List<ConstrComponent> selectCC(Point p, boolean performMultipleSelect) {

        List<ConstrComponent> v = getCCAtPoint(p);

        // If no multiple selection is being performed, just pick first one
        if (performMultipleSelect) {
            ConstrComponent c = v.get(0);
            if (c != null)
                c.setisSelected(true);
        } else {
            for (ConstrComponent c : v) {
                c.setisSelected(true);
            }
        }

        return v;
    }

    public void mousePressed(MouseEvent e) {
        SelPoint sp;
        int a;
        ConstrComponent c;

        // Store mouse position, in case it's the start of a drag
        mouseStart(e.getPoint());

        // If we're placing an object, use mouse coords as initial position
        if (placingComponent) {
            c = placeComponent(e);
            startPlacingComponentEndSP(c);
            return;
        }
        {
            List<ConstrComponent> v = getCCAtPoint(e.getPoint());

            // If we need to pick a target CC for a relative-position constraint,
            // check for target CC
            if (pickingTargetCC) {
                if (v.size() < 1) {
                    /*
                     * System.out.println("No target selected");
                     */
                    stopPickingTargetCC();
                    return;
                }
                c = v.get(0);
                establishRelationalConstr(c);
                return;
            }
        }

        // See if we're over a selectable thing
        List<SelPoint> selPointAtPoint = new ArrayList<SelPoint>(4);
        getSelPointsAt(e.getPoint(), selPointAtPoint);

        if (selPointAtPoint.size() < 1)
            sp = null;
        else {
            sp = selPointAtPoint.get(spListOffset % (selPointAtPoint.size()));
            spListOffset++;
        }

        // Did we hit anything?
        if (sp != null) {
            if (!sp.getSelected()) {
                // Hit unselected point, so perform normal selection process
                if (!e.isControlDown())
                    unselectAll();
                sp.setSelected();
                // To help with the appearance of snap-to-point behavior,
                // use the center of SP as the apparent start of a drag.
                mouseStart(new Point(sp.x, sp.y));

                // For each CC interested in SP, make sure it can change its
                // whole CC selection flag
                for (ConstrComponent cc : sp.getInterestedCC()) {
                    cc.updateIsSelected();
                }
            } else {
                // Hitting a selected point does not change the selection list.
                // Make sure to reset the mouse start position in case the SP hit
                // is then dragged.
                mouseStart(new Point(sp.x, sp.y));
            }
        } else {
            // See if any constraint objs. should be selected
            // This entails clicking in their bounding box.
            List<Constraint> cl = getConstrAtPoint(e.getPoint(), e.isShiftDown());
            if (cl.size() > 0) {
                if (e.isControlDown()) {
                    Constraint constr = cl.get(0);
                    constr.setisSelected(true);
                } else {
                    unselectAll();
                    for (Constraint constr : cl) {
                        constr.setisSelected(true);
                    }
                }
            } else {
                // See if any CC's should be selected
                // This entails having clicked in their bounding boxes *and*
                // having the shift key held down.
                List<ConstrComponent> v = getCCAtPoint(e.getPoint());
                if ((v.size() == 0) || !e.isShiftDown()) {
                    // Hitting nothing results in setting the start of a rubber-band sel.
                    // box drag. Also may need to clear all currently selected points.
                    if (!e.isControlDown()) {
                        // Only things in box should be selected
                        unselectAll();
                    }
                    drawingSelectionBox = true;
                    selectionBoxStartx = e.getPoint().x;
                    selectionBoxStarty = e.getPoint().y;
                    selectionBox.x = e.getPoint().x;
                    selectionBox.y = e.getPoint().y;
                    selectionBox.width = 0;
                    selectionBox.height = 0;
                } else {
                    // There are CC's in v enclosing the given point, so select them
                    if (e.isControlDown()) {
                        c = v.get(0);
                        c.setisSelected(true);
                    } else {
                        unselectAll();
                        for (a = 0; a < v.size(); a++) {
                            c = v.get(a);
                            c.setisSelected(true);
                        }
                    }
                }
            }
        }

    }

    // Invoked when mouse is released. Removes all edit constraints.
    // If a selection box was being drawn, select everything in it.
    // If no selection box, establish colocation constraints on any points
    // that match (x, y) coords with any selected points (which implies
    // they were snapped-to during a drag)
    public void mouseReleased(MouseEvent e) {
        firstDrag = true;

        removeAllEditConstraints();

        // Build a list of CC's that may have been affected by any dragged
        // points (IE selected points)
        List<SelPoint> allSelSP = getAllSelectedSelPoints();

        List<ConstrComponent> movedCC = new ArrayList<ConstrComponent>(4);
        for (int a = 0; a < allSelSP.size(); a++) {
            SelPoint sp = allSelSP.get(a);
            for (ConstrComponent c : sp.getInterestedCC()) {
                if (!movedCC.contains(c))
                    movedCC.add(c);
            }
        }

        if (drawingSelectionBox) {
            select(selectionBox, e.isControlDown());
            drawingSelectionBox = false;
        } else {
            // For every SP, get all other points stacked at that location.
            List<SelPoint> allSP = new ArrayList<SelPoint>(20);
            for (ConstrComponent cc : curCC) {
                for (int b = 0; b < cc.selPoints.size(); b++) {
                    if (!allSP.contains(cc.selPoints.get(b)))
                        allSP.add(cc.selPoints.get(b));
                }
            }
            List<SelPoint> stackedSP;
            while (allSP.size() > 0) {
                SelPoint sp = allSP.get(0);
                List<SelPoint> v = new ArrayList<SelPoint>(4);
                getSelPointsAt(new Point(sp.x, sp.y), v);
                stackedSP = new ArrayList<SelPoint>(4);

                // To be merged, the point must exactly share x, y coords with SelPoint sp
                for (SelPoint stSP : v) {
                    if ((stSP.x == sp.x) && (stSP.y == sp.y)) {
                        if (!stackedSP.contains(stSP))
                            stackedSP.add(stSP);
                    }
                }
                // Are any SP's in the stack interested in any live CC's?
                boolean stackHasActiveSP = false;
                for (int b = 0; b < stackedSP.size(); b++) {
                    sp = stackedSP.get(b);
                    for (ConstrComponent cc : sp.getInterestedCC()) {
                        if (movedCC.contains(cc))
                            stackHasActiveSP = true;
                    }
                }
                if (stackedSP.size() > 1) {
                    if (stackHasActiveSP) {
                        establishColocationConstraint(stackedSP);
                    }
                    for (int b = 0; b < stackedSP.size(); b++)
                        if (allSP.contains(stackedSP.get(b)))
                            allSP.remove(stackedSP.get(b));
                } else {
                    allSP.remove(sp);
                }
            }
        }

        repaint();
    }

    // Invoked when mouse is dragged
    public void mouseDragged(MouseEvent e) {
        int a, minx, miny, maxx, maxy, ex, ey;

        // If we're drawing a selection box, update box corners, highlight
        // contents, and trigger repaint
        ex = e.getX();
        ey = e.getY();

        // Ensure that strange things don't happen when cursor is dragged outside
        // of the window and back again
        if (ex < 0)
            ex = 0;
        if (ey < 0)
            ey = 0;
        if (ex > desiredWidth)
            ex = desiredWidth;
        if (ey > desiredHeight)
            ey = desiredHeight;

        if (drawingSelectionBox) {
            if (ex < selectionBoxStartx) {
                minx = ex;
                maxx = selectionBoxStartx;
            } else {
                maxx = ex;
                minx = selectionBoxStartx;
            }
            if (ey < selectionBoxStarty) {
                miny = ey;
                maxy = selectionBoxStarty;
            } else {
                maxy = ey;
                miny = selectionBoxStarty;
            }
            selectionBox.x = minx;
            selectionBox.y = miny;
            selectionBox.width = maxx - minx;
            selectionBox.height = maxy - miny;

            highlightSelectionBox();
            repaint();
            return;
        }

        {
            // See if there are any SelPoints under the cursor, and snap to them
            // if so.
            List<SelPoint> v = new ArrayList<SelPoint>(4);
            getSelPointsAt(new Point(ex, ey), v);
            SelPoint sp = null;
            for (a = 0; a < v.size(); a++) {
                if (isValidSnapToTarget(v.get(a))) {
                    sp = v.get(a);
                    break;
                }
            }
            if (sp != null) {
                ex = sp.x;
                ey = sp.y;
            }
        }

        mouseDelta.x = ex - mouseStart.x;
        mouseDelta.y = ey - mouseStart.y;
        mouseStart.x = ex;
        mouseStart.y = ey;

        // On the first drag event, establish edit constraints for every
        // selected point
        if (firstDrag) {
            firstDrag = false;
            numEC = 0;

            for (SelPoint sp : getAllSelectedSelPoints()) {
                sp.setEditConstraints();
                numEC += 2;
            }

            // Ensure that the ec list is of the appropriate size
            editConstantList.setSize(numEC);

            /*
             * System.out.println("Set " + numEC + " edit constraints");
             */
        }

        // Change every variable with an edit constraint by the delta,
        // and record the changes
        notifyDeltaMove();

        // Now, editConstantList contains list of edit constant targets, so
        // re-solve with them
        try {
            solver.resolve(editConstantList.ec);
        } catch (CLInternalError cle) {
            System.out.println("mouseDrag: CLInternalError!");
        }

        // Give each group an opportunity to update its CC's internal states by
        // querying its ClVariables which may have been changed by resolve()
        updateEditConstants();

        // Trigger a repaint
        repaint();
    }

    // Helper function to determine if given SelPoint sp is a valid snap-to
    // target during a drag. It must not be selected, and must not belong
    // to the same CC as any point being dragged (IE selected).
    public boolean isValidSnapToTarget(SelPoint sp) {
        if (sp.getSelected())
            return false;

        List<SelPoint> allSelSP = getAllSelectedSelPoints();
        List<ConstrComponent> activeCC = new ArrayList<ConstrComponent>(4);
        for (SelPoint tSP : allSelSP) {
            List<ConstrComponent> v = tSP.getInterestedCC();
            for (ConstrComponent cc : v) {
                if (!activeCC.contains(cc))
                    activeCC.add(cc);
            }
        }

        // For each active CC, if sp is contained by it, don't do it.
        for (ConstrComponent cc : activeCC) {
            if (cc.selPoints.contains(sp))
                return false;
        }

        return true;
    }

    // Place a component
    public ConstrComponent placeComponent(MouseEvent e) {
        ConstrComponent cc = null;
        int ex, ey;

        // Snap starting point to coincide with any SP under cursor
        List<SelPoint> v = new ArrayList<SelPoint>(4);
        getSelPointsAt(e.getPoint(), v);
        if (v.size() > 0) {
            SelPoint sp = v.get(0);
            ex = sp.x;
            ey = sp.y;
        } else {
            ex = e.getX();
            ey = e.getY();
        }
        if (placingComponentType.equals("LineCC")) {
            cc = new LineCC(solver, ex, ey, desiredWidth, desiredHeight);
            addConstrComponent(cc);
            stopPlacingComponent();
            return cc;
        }
        if (placingComponentType.equals("MidpointLineCC")) {
            cc = new MidpointLineCC(solver, ex, ey, desiredWidth, desiredHeight);
            addConstrComponent(cc);
            stopPlacingComponent();
            return cc;
        }
        if (placingComponentType.equals("RectangleCC")) {
            cc = new RectangleCC(solver, ex, ey, desiredWidth, desiredHeight);
            addConstrComponent(cc);
            stopPlacingComponent();
            return cc;
        }
        if (placingComponentType.equals("CircleCC")) {
            cc = new CircleCC(solver, ex, ey, desiredWidth, desiredHeight);
            addConstrComponent(cc);
            stopPlacingComponent();
            return cc;
        }
        System.out.println("Placing unknown component type: " + placingComponentType);
        stopPlacingComponent();
        return cc;
    }

    // Ensure that constraints are satisfied. Do this by creating some dummy
    // edit constraints and invoke resolve.
    public void resatisfyConstraints() {
        numEC = 0;

        for (SelPoint sp : getAllSelectedSelPoints()) {
            sp.setEditConstraints();
            numEC += 2;
        }

        /*
         * if ( numEC == 0 ) { // Don't try a resolve with no selected points! return; }
         */

        // Ensure that the ec list is of the appropriate size
        editConstantList.setSize(numEC);

        mouseDelta = new Point(0, 0);

        // Change every variable with an edit constraint by the delta,
        // and record the changes
        notifyDeltaMove();

        // Now, editConstantList contains list of edit constant targets, so
        // re-solve with them
        try {
            solver.resolve(editConstantList.ec);
        } catch (CLInternalError cle) {
            System.out.println("mouseDrag: CLInternalError!");
        }

        updateEditConstants();
        removeAllEditConstraints();

    }

}

/*
 * $Log: DrawPanel.java,v $ Revision 1.5 1999/12/16 02:10:01 gjb * java/cda/Makefile.am: Put Constraint/*, Main/* files into the
 * distribution and build with them.
 * 
 * * java/demos/*.java: Move everything into the EDU.Washington.grad.gjb.cassowary_demos package.
 * 
 * * java/cda/classes/run.html, java/demos/quaddemo.htm: Fix nl convention, name class explicitly w/ package in front, w/o
 * trailing .class.
 * 
 * * java/cda/**: Move everything into the EDU.Washington.grad.noth.cda package.
 * 
 * Revision 1.4 1998/06/23 02:08:54 gjb Added import of cassowary package so that the cda doesn't need to be in the same package
 * as the solver.
 * 
 * Revision 1.3 1998/05/14 22:34:22 gjb Print full solver information upon "i" keystroke
 * 
 * Revision 1.2 1998/05/09 00:30:32 gjb Remove cr-s
 * 
 * Revision 1.1 1998/05/09 00:11:13 gjb Added
 * 
 * Revision 1.27 1998/05/05 21:42:58 Michael Now require shift key to be held for constr. selection
 * 
 * Revision 1.26 1998/04/28 20:08:40 Michael Removed dead code
 * 
 * Revision 1.25 1998/04/26 06:16:12 Michael Changed layout
 * 
 * Revision 1.24 1998/04/23 09:02:31 Michael Fixed several point-merging bugs w/alignment constraints Changed selection of
 * colocation base point to favor pts w/ anchor constraints Added code for alignment constraints Added regeneration of border
 * constraints on cloned SP's Relaxed agressiveness of snap-to behavior Prevented colocation constraints from being formed on pts
 * in same CC
 * 
 * Revision 1.23 1998/04/20 09:55:08 Michael Changed auto-merging to only look at stacked points related to a CC that was moved
 * directly Added ability to select and delete constraint objs
 * 
 * Revision 1.22 1998/04/19 04:12:47 Michael Moved establishColocationConstraint to separate function Added snap-to-point for
 * placing CC's and dragging points Added automatic colocation point establishment
 * 
 * Revision 1.21 1998/04/15 00:10:18 Michael Changed selection model to allow for picking only one point in a stack
 * 
 * Revision 1.20 1998/04/10 02:01:12 Michael Added ability to set size of CC when placing it
 * 
 * Revision 1.19 1998/04/08 23:40:33 Michael Changes so colocation constraints disappear when appropriate Dragging cursor outside
 * of window no longer causes offset problems
 * 
 * Revision 1.18 1998/04/08 05:42:56 Michael Commented out printing msgs Changed selection box to be draggable in any direction
 * (not just down-right) Changed selection interface to require shift + click to select whole obj, and removed highlighting of
 * whole objs w/out shift down
 * 
 * Revision 1.17 1998/04/05 03:12:21 Michael Added circle CC
 * 
 * Revision 1.16 1998/04/02 06:59:38 Michael Added relational constraints
 * 
 * Revision 1.15 1998/04/01 10:18:18 Michael Merged in mousePress, mouseRelease functions from DrawPanelMouseMotionListener Added
 * code to handle using and maintaining back pointers
 * 
 * Revision 1.14 1998/03/27 06:24:51 Michael Added code to allow picking a target CC for relational constraints
 * 
 * Revision 1.13 1998/03/25 02:47:19 Michael Added CC selection functions
 * 
 * Revision 1.12 1998/03/19 09:18:22 Michael Fixed logic bug
 * 
 * Revision 1.11 1998/03/19 04:37:08 Michael Added cmds to del one/all CC's
 * 
 * Revision 1.10 1998/03/02 06:45:06 Michael Extensive changes to move to CCG-based list of parts
 * 
 * Revision 1.9 1998/02/25 10:40:43 Michael Added first cut of internal grouping for point sharing
 * 
 * Revision 1.8 1998/02/22 05:00:01 Michael Removed debugging print statements
 * 
 * Revision 1.7 1998/02/17 10:42:00 Michael Split files with multiple classes
 * 
 * Revision 1.5 1998/02/16 10:33:40 Michael Added hooks for adding/placing a new component
 * 
 * Revision 1.4 1998/02/15 11:33:38 Michael Hooked up solver to mouse movement
 * 
 * Revision 1.3 1998/02/15 07:14:35 Michael Added highlighting on mouse movement Added Cassowary solver instance and hooks
 * 
 * Revision 1.2 1998/02/09 02:25:17 Michael Added mouse drag functionality Moved mouse listener class inside DrawPanel class Added
 * vector of ConstrComponents stored in DrawPanel
 * 
 * Revision 1.1 1998/02/06 11:13:30 Michael Major changes in layout to handle event propagation and resizing
 */
