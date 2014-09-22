package org.klomp.cassowary.layout;

import java.awt.Component;

import org.klomp.cassowary.CL;
import org.klomp.cassowary.ClLinearExpression;
import org.klomp.cassowary.ClSimplexSolver;
import org.klomp.cassowary.ClVariable;
import org.klomp.cassowary.clconstraint.ClConstraint;
import org.klomp.cassowary.clconstraint.ClLinearEquation;
import org.klomp.cassowary.clconstraint.ClLinearInequality;

public class LayoutConstraint {

    private Component left;
    private Attribute leftAttribute;
    private Relation relation;
    private double rightFactor;
    private Component right;
    private Attribute rightAttribute;
    private double rightConstant;

    private ClVariable leftVariable;
    private ClVariable rightVariable;
    private ClConstraint constraint;

    public LayoutConstraint(Component left, Attribute leftAttribute, Relation relation, double rightFactor, Component right,
            Attribute rightAttribute, double rightConstant) {
        this.left = left;
        this.leftAttribute = leftAttribute;
        this.relation = relation;
        this.rightFactor = rightFactor;
        this.right = right;
        this.rightAttribute = rightAttribute;
        this.rightConstant = rightConstant;
    }

    public LayoutConstraint(Component left, Attribute leftAttribute, Relation relation, double rightConstant) {
        this(left, leftAttribute, relation, 1, null, null, rightConstant);
    }

    public LayoutConstraint(Component left, Attribute leftAttribute, Relation relation, double rightFactor, Component right,
            Attribute rightAttribute) {
        this(left, leftAttribute, relation, rightFactor, right, rightAttribute, 0);
    }

    public LayoutConstraint(Component left, Attribute leftAttribute, Relation relation, Component right, Attribute rightAttribute) {
        this(left, leftAttribute, relation, 1, right, rightAttribute, 0);
    }

    public LayoutConstraint(Component left, Attribute leftAttribute, Relation relation, Component right,
            Attribute rightAttribute, double rightConstant) {
        this(left, leftAttribute, relation, 1, right, rightAttribute, rightConstant);
    }

    void addToSolver(ClSimplexSolver solver, CassowaryLayout layout) {
        this.leftVariable = layout.componentVars.get(left).get(leftAttribute);
        if (right != null) {
            this.rightVariable = layout.componentVars.get(right).get(rightAttribute);
        }
        this.constraint = buildConstraint();
        solver.addConstraint(constraint);
    }

    void removeFromSolver(ClSimplexSolver solver) {
        solver.removeConstraint(constraint);
    }

    private ClConstraint buildConstraint() {
        ClConstraint constraint;
        if (relation == Relation.EQ) {
            if (right != null) {
                constraint = new ClLinearEquation(leftVariable, rightExpression());
            } else {
                constraint = new ClLinearEquation(leftVariable, rightConstant);
            }
        } else {
            byte op = (relation == Relation.GE ? CL.GEQ : CL.LEQ);
            if (right != null) {
                constraint = new ClLinearInequality(leftVariable, op, rightExpression());
            } else {
                constraint = new ClLinearInequality(leftVariable, op, rightConstant);
            }
        }
        return constraint;
    }

    private ClLinearExpression rightExpression() {
        return new ClLinearExpression(rightVariable, rightFactor, rightConstant);
    }

    public Component getLeft() {
        return left;
    }

    public Attribute getLeftAttribute() {
        return leftAttribute;
    }

    public Relation getRelation() {
        return relation;
    }

    public double getRightFactor() {
        return rightFactor;
    }

    public Component getRight() {
        return right;
    }

    public Attribute getRightAttribute() {
        return rightAttribute;
    }

    public double getRightConstant() {
        return rightConstant;
    }
}
