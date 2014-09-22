// $Id: ClConstraint.java,v 1.14 2000/04/10 15:55:06 gjb Exp $
//
// Cassowary Incremental Constraint Solver
// Original Smalltalk Implementation by Alan Borning
// This Java Implementation by Greg J. Badros, <gjb@cs.washington.edu>
// http://www.cs.washington.edu/homes/gjb
// (C) 1998, 1999 Greg J. Badros and Alan Borning
// See ../LICENSE for legal details regarding this software
//
// ClConstraint
//

package EDU.Washington.grad.gjb.cassowary;

import java.lang.*;


public abstract class ClConstraint
{

  public ClConstraint(ClStrength strength, double weight)
    { _strength = strength; _weight = weight; _times_added = 0; }

  public ClConstraint(ClStrength strength)
    { _strength = strength; _weight = 1.0; _times_added = 0; }

  public ClConstraint()
    { _strength = ClStrength.required; _weight = 1.0; _times_added = 0; }
  
  public abstract ClLinearExpression expression();

  public boolean isEditConstraint()
    { return false; }

  public boolean isInequality()
    { return false; }

  public boolean isRequired()
    { return _strength.isRequired(); }

  public boolean isStayConstraint()
    { return false; }

  public ClStrength strength()
    { return _strength; }

  public double weight()
    { return _weight; }

  public String toString()
    { return _strength.toString() +
	" {" + weight() + "} (" + expression(); }

  public void setAttachedObject(Object o)
    { _attachedObject = o; }

  public Object getAttachedObject()
    { return _attachedObject; }

  public void changeStrength(ClStrength strength)
    throws ExCLTooDifficult
    { 
      if (_times_added == 0) {
        setStrength(strength);
      } else {
        throw new ExCLTooDifficult();
      }
    }

  public void addedTo(ClSimplexSolver solver)
    { ++_times_added; }

  public void removedFrom(ClSimplexSolver solver)
    { --_times_added; }

  private void setStrength(ClStrength strength)
    { _strength = strength; }

  private void setWeight(double weight)
    { _weight = weight; }

  private ClStrength _strength;
  private double _weight;
  
  private Object _attachedObject;

  private int _times_added;
}
