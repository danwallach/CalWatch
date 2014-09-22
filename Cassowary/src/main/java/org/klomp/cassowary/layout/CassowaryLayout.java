package org.klomp.cassowary.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.IdentityHashMap;

import org.klomp.cassowary.ClSimplexSolver;
import org.klomp.cassowary.util.IdentityHashSet;

public class CassowaryLayout implements LayoutManager2 {
    /**
     * Set: each component can only be added once to the layout manager.
     */
    private IdentityHashSet<Component> components = new IdentityHashSet<Component>();

    private IdentityHashSet<LayoutConstraint> constraints = new IdentityHashSet<LayoutConstraint>();

    IdentityHashMap<Component, ComponentVars> componentVars = new IdentityHashMap<Component, ComponentVars>();

    private ClSimplexSolver solver = new ClSimplexSolver();

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        addLayoutComponent(comp);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        addLayoutComponent(comp);
    }

    public void addLayoutComponent(Component comp) {
        components.add(comp);
        ComponentVars vars = new ComponentVars(comp);
        componentVars.put(comp, vars);
        vars.addToSolver(solver);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        components.remove(comp);
        componentVars.remove(comp).removeFromSolver(solver);
        // TODO: remove default constraints for component, and perhaps all constraints that involve the component
        // except if we allow constraints against components in other Cassowary-managed containers.
    }

    public void addConstraint(LayoutConstraint constraint) {
        constraints.add(constraint);
        constraint.addToSolver(solver, this);
    }

    public void removeConstraint(LayoutConstraint constraint) {
        constraint.removeFromSolver(solver);
        constraints.add(constraint);
    }

    @Override
    public void layoutContainer(Container parent) {
        System.out.println("Performing layout");
        for (Component c : components) {
            Dimension d = c.getPreferredSize();
            c.setBounds(0, 0, d.width, d.height);
        }
        for (ComponentVars vars : componentVars.values()) {
            vars.suggestValues(solver);
        }
        solver.solve();
        for (ComponentVars vars : componentVars.values()) {
            vars.applyResult(true);
        }
        for (ComponentVars vars : componentVars.values()) {
            vars.applyResult(false);
        }

        for (Component c : components) {
            System.out.println(c.toString() + ": " + c.getBounds());
        }

        System.out.println("solver: " + solver);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // TODO: implementation incorrect, should precalculate, not measure actual situation
        return maximumLayoutSize(parent);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        // TODO: implementation incorrect, should precalculate, not measure actual situation
        return maximumLayoutSize(parent);
    }

    @Override
    public Dimension maximumLayoutSize(Container parent) {
        // TODO: implementation incorrect, should precalculate, not measure actual situation
        int width = 0;
        int height = 0;
        Rectangle rv = new Rectangle();
        for (Component c : components) {
            c.getBounds(rv);
            width = Math.max(width, rv.x + rv.width);
            height = Math.max(height, rv.y + rv.height);
        }
        return new Dimension(width, height);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        // Centered - what is the use of this poorly documented method?
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        // Centered - what is the use of this poorly documented method?
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // No cached information to invalidate.
    }

}
