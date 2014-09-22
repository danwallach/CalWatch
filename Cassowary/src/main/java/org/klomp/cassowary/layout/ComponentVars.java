package org.klomp.cassowary.layout;

import static org.klomp.cassowary.layout.Attribute.BOTTOM;
import static org.klomp.cassowary.layout.Attribute.HEIGHT;
import static org.klomp.cassowary.layout.Attribute.LEFT;
import static org.klomp.cassowary.layout.Attribute.RIGHT;
import static org.klomp.cassowary.layout.Attribute.TOP;
import static org.klomp.cassowary.layout.Attribute.WIDTH;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.klomp.cassowary.CL;
import org.klomp.cassowary.ClLinearExpression;
import org.klomp.cassowary.ClSimplexSolver;
import org.klomp.cassowary.ClStrength;
import org.klomp.cassowary.ClVariable;
import org.klomp.cassowary.clconstraint.ClConstraint;
import org.klomp.cassowary.clconstraint.ClLinearEquation;
import org.klomp.cassowary.clconstraint.ClLinearInequality;

class ComponentVars {
    private EnumMap<Attribute, ClVariable> map = new EnumMap<Attribute, ClVariable>(Attribute.class);

    private Component component;

    private List<ClConstraint> constraints = new ArrayList<ClConstraint>();

    public ComponentVars(Component component) {
        this.component = component;
        for (Attribute attr : Attribute.values()) {
            map.put(attr,
                    new ClVariable(component.getClass().getName() + "@" + System.identityHashCode(component) + "_" + attr.name()));
        }
        add(new ClLinearInequality(get(LEFT), CL.GEQ, 0.));
        add(new ClLinearInequality(get(TOP), CL.GEQ, 0.));
        Dimension minimum = component.getMinimumSize();
        add(new ClLinearInequality(get(WIDTH), CL.GEQ, minimum.width));
        add(new ClLinearInequality(get(HEIGHT), CL.GEQ, minimum.height));
        add(new ClLinearEquation(get(RIGHT), new ClLinearExpression(get(LEFT)).plus(get(WIDTH))));
        add(new ClLinearEquation(get(BOTTOM), new ClLinearExpression(get(TOP)).plus(get(HEIGHT))));
        // TODO: add constraint on width/height of container (those are variables)
        // TODO: centerx,y/baseline
        // TODO: skip leading/trailing
    }

    public void addToSolver(ClSimplexSolver solver) {
        for (Attribute attr : Attribute.values()) {
            solver.addEditVar(map.get(attr), ClStrength.weak);
        }
        for (ClConstraint constraint : constraints) {
            solver.addConstraint(constraint);
        }
    }

    private void add(ClConstraint constraint) {
        constraints.add(constraint);
    }

    public void suggestValues(ClSimplexSolver solver) {
        for (Attribute attr : Attribute.values()) {
            // TODO: use preferred width/height for component
            solver.suggestValue(map.get(attr), attr.lookup(component));
        }
    }

    void applyResult(boolean size) {
        for (Attribute attr : Attribute.values()) {
            if (attr.isSize() == size) {
                int value = (int) get(attr).getValue();
                attr.apply(component, value);
            }
        }
    }

    public void removeFromSolver(ClSimplexSolver solver) {
        for (Attribute attr : Attribute.values()) {
            solver.removeEditVar(map.get(attr));
        }
        for (ClConstraint constraint : constraints) {
            solver.removeConstraint(constraint);
        }
    }

    public ClVariable get(Attribute attribute) {
        return map.get(attribute);
    }
}