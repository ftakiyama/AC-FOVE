package br.usp.poli.takiyama.common;

import java.util.Iterator;

import br.usp.poli.takiyama.prv.Binding;
import br.usp.poli.takiyama.prv.Constant;
import br.usp.poli.takiyama.prv.LogicalVariable;
import br.usp.poli.takiyama.prv.StdLogicalVariable;
import br.usp.poli.takiyama.prv.Substitution;
import br.usp.poli.takiyama.prv.Term;

/**
 * This class represents inequality constraints of the form X &ne; Y, where
 * X and Y are a Terms. 
 * <p>
 * Inequalities with two constants, like t &ne; q, are invalid. If a method
 * detects a invalid constraint, it returns null.
 * </p>
 *  
 * @author Felipe Takiyama
 *
 */
public final class InequalityConstraint extends AbstractConstraint {
	
	/* ************************************************************************
	 *    Constructors
	 * ************************************************************************/
	
	/**
	 * Creates the inequality constraint composed by the specified terms.
	 * 
	 * @param firstTerm The left-hand side of the constraint 
	 * @param secondTerm The right-hand side of the constraint
	 * @return An inequality constraint
	 * @throws IllegalArgumentException if both terms are {@link Constant}s or 
	 * if both terms are equal
	 */
	private InequalityConstraint(Term t1, Term t2) throws IllegalArgumentException {
		if (t1.equals(t2)) {
			// trying to create a constraint with equal terms
			throw new IllegalArgumentException();
		}
		if (t1 instanceof LogicalVariable || t2 instanceof LogicalVariable) {
			firstTerm = t1;
			secondTerm = t2;
		} else {
			// trying to create a constraint with two constants
			throw new IllegalArgumentException();
		}
	}
	
	/* ************************************************************************
	 *    Static factories
	 * ************************************************************************/
	
	/**
	 * Static factory of inequality constraints
	 * 
	 * @param firstTerm The left-hand side of the constraint 
	 * @param secondTerm The right-hand side of the constraint
	 * @return An inequality constraint
	 * @throws IllegalArgumentException if both terms are {@link Constant}s or 
	 * if both terms are equal
	 */
	public static Constraint getInstance(Term firstTerm, Term secondTerm) {
		return new InequalityConstraint(firstTerm, secondTerm);
	}

	
	/* ************************************************************************
	 *    Inherited methods
	 * ************************************************************************/
	
	@Override
	public Constraint apply(Substitution s) throws IllegalStateException {
		Term t1 = this.firstTerm;
		Term t2 = this.secondTerm;
		for (Iterator<LogicalVariable> it = s.getSubstitutedIterator(); it.hasNext(); ) {
			LogicalVariable replaced = it.next();
			if (replaced.equals(t1)) {
				t1 = s.getReplacement(replaced);
			} 
			if (replaced.equals(t2)) {
				t2 = s.getReplacement(replaced);
			}
		}
		if (t1.equals(t2)) {
			throw new IllegalStateException();
		}
		return new InequalityConstraint(t1, t2);
	}

	
	@Override
	public boolean isConsistentWith(Binding b) {
		
		/*
		 * Tests a more subtle case: is X != a consistent with X/W?
		 * Well, if we apply the substitution, we obtain W != a which is indeed 
		 * a valid constraint, but not necessarily correct. 
		 * Event though X != a, we cannot say the same about W.
		 * This case is not supposed to happen in the current application,
		 * because I use only unary bindings (X/a) to call this method.
		 */
		if (hasCommonTerm(b) && b.secondTerm() instanceof LogicalVariable) {
			return false;
		}
		Substitution sub = Substitution.getInstance(b);
		try {
			apply(sub);
		} catch (IllegalStateException e) {
			return false;
		}
		return true;
	}
	
	
	private boolean hasCommonTerm(Binding b) {
		return (this.firstTerm().equals(b.firstTerm())) 
			|| (this.firstTerm().equals(b.secondTerm()))
			|| (this.secondTerm().equals(b.firstTerm()))
			|| (this.secondTerm().equals(b.secondTerm()));
	}
	
	
	/* ************************************************************************
	 *    hashCode, equals and toString
	 * ************************************************************************/

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof InequalityConstraint)) {
			return false;
		}
		
		InequalityConstraint other = (InequalityConstraint) obj; 
		
		// tests A!=B == A!=B
		boolean direct = 
				(firstTerm == null) ? (other.firstTerm == null) 
									: (firstTerm.equals(other.firstTerm))
				&& 
				(secondTerm == null) ? (other.secondTerm == null) 
									 : (secondTerm.equals(other.secondTerm));
		
		// tests A!=B == B!=A
		boolean inverse = 
				(firstTerm == null) ? (other.secondTerm == null) 
								  	: (firstTerm.equals(other.secondTerm))
				&& 
				(secondTerm == null) ? (other.firstTerm == null) 
									 : (secondTerm.equals(other.firstTerm)); 
		
		return direct || inverse;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((firstTerm == null) ? 0 : firstTerm.hashCode());
		result = prime * result
				+ ((secondTerm == null) ? 0 : secondTerm.hashCode());
		result = prime * 5; // otherwise EqualityConstraint with same terms would have same hash
		return result;
	}
	
	
	@Override
	public String toString() {
		return firstTerm.toString() + " != " + secondTerm.toString();
	}
	
	/**
	 * @deprecated
	 * Apply the substitution in this inequality.
	 * The following rules apply:<br>
	 * <li> X &ne; Y and X/q  returns Y &ne; q
	 * <li> X &ne; Y and Y/q  returns X &ne; q
	 * <li> X &ne; Y and X/W  returns W &ne; Y
	 * <li> X &ne; Y and Y/W  returns X &ne; W
	 * <li> X &ne; t and X/q  returns <b>null</b>
	 * <li> X &ne; t and Y/q  returns X &ne; t
	 * <li> X &ne; t and X/W  returns W &ne; t
	 * <li> X &ne; t and Y/W  returns X &ne; t
	 * <br>
	 * @param substitution The substitution to apply on the constraint
	 * @return The constraint that results from the application of the
	 * specified substitution to this constraint, following the rules 
	 * specified above.
	 */
	public Constraint applySubstitution(Binding substitution) {
		if (firstTerm.equals(substitution.firstTerm())) { // looks ugly
			if (secondTerm instanceof StdLogicalVariable && substitution.secondTerm() instanceof Constant) { // X!=Y && X/q 
				return new InequalityConstraint((LogicalVariable) secondTerm, substitution.secondTerm());
			} else if (secondTerm instanceof Constant && substitution.secondTerm() instanceof Constant) { // X!=t && X/q
				return null;
			} else {
				return new InequalityConstraint((LogicalVariable) substitution.secondTerm(), secondTerm);	
			}
		} else if (secondTerm.equals(substitution.firstTerm())) {
			return new InequalityConstraint(firstTerm, substitution.secondTerm());
		} else {
			return this;
		}
	}
}