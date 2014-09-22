/*
 * Cassowary Incremental Constraint Solver
 * Original Smalltalk Implementation by Alan Borning
 * 
 * Java Implementation by:
 * Greg J. Badros
 * Erwin Bolwidt
 * 
 * (C) 1998, 1999 Greg J. Badros and Alan Borning
 * (C) Copyright 2012 Erwin Bolwidt
 * 
 * See the file LICENSE for legal details regarding this software
 */

package org.klomp.cassowary.clconstraint;

import org.klomp.cassowary.ClStrength;
import org.klomp.cassowary.ClVariable;

public class ClEditConstraint extends ClEditOrStayConstraint {

    public ClEditConstraint(ClVariable clv, ClStrength strength, double weight) {
        super(clv, strength, weight);
    }

    public ClEditConstraint(ClVariable clv, ClStrength strength) {
        super(clv, strength);
    }

    public ClEditConstraint(ClVariable clv) {
        super(clv);
    }

    @Override
    public boolean isEditConstraint() {
        return true;
    }

    @Override
    public String toString() {
        return "edit" + super.toString();
    }

}
