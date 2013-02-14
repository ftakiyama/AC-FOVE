package br.usp.poli.takiyama.cfove;

import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import br.usp.poli.takiyama.common.Constraint;
import br.usp.poli.takiyama.common.Parfactor;
import br.usp.poli.takiyama.common.RandomVariableSet;
import br.usp.poli.takiyama.prv.CountingFormula;
import br.usp.poli.takiyama.prv.LogicalVariable;
import br.usp.poli.takiyama.prv.ParameterizedRandomVariable;

public final class MacroOperations {
	
	/**
	 * Empty constructor that enforces non-instantiability
	 */
	private MacroOperations() { }
	
	/**
	 * This operation makes all the necessary splits and expansions to 
	 * guarantee that the sets of random variables represented by parameterized
	 * random variables in each parfactor of the given set are equal or 
	 * disjoint.
	 * <br>
	 * In other words, for any parameterized random variables p and q from
	 * parfactors of the specified set, p and q represent identical or
	 * disjoint sets of random variables.
	 * <br>
	 * This operation is used before multiplication and elimination 
	 * on parfactors.
	 * <br>
	 * If the specified set is empty, returns an empty set.
	 * 
	 * @param parfactors The set of parfactors to shatter.
	 * @return The specified set of parfactors shattered, or an empty set if
	 * the specified set is also empty.
	 */
	public static Set<Parfactor> shatter(Set<Parfactor> parfactors) {
		
		if (parfactors.isEmpty()) 
			return parfactors;
		
		Stack<Parfactor> parfactorsToProcess = new Stack<Parfactor>();
		parfactorsToProcess.addAll(parfactors);
		parfactors = null;
		
		Set<Parfactor> shatteredSet = new HashSet<Parfactor>();
		Set<Parfactor> shatteredPool = new HashSet<Parfactor>();
		
		while (!parfactorsToProcess.isEmpty()) {
			Parfactor p1 = parfactorsToProcess.pop();
			while (!parfactorsToProcess.isEmpty()) {
				Parfactor p2;
				try {
					p2 = parfactorsToProcess.pop();
				} catch (EmptyStackException e) {
					continue;
				}
				Set<Parfactor> unifiedSet = p1.unify(p2);
				if (unifiedSet.size() == 2
						&& unifiedSet.contains(p1)
						&& unifiedSet.contains(p2)) {
					shatteredPool.add(p2);
				} else {
					parfactorsToProcess.addAll(unifiedSet);
					parfactorsToProcess.addAll(shatteredPool);
					parfactorsToProcess.addAll(shatteredSet);
					unifiedSet.clear();
					shatteredPool.clear();
					shatteredSet.clear();
					p1 = parfactorsToProcess.pop();
				}
			}
			shatteredSet.add(p1);
			parfactorsToProcess.addAll(shatteredPool);
			shatteredPool.clear();
		}
		
		return shatteredSet;
	}
	
	/**
	 * This operation makes all the necessary splits to 
	 * guarantee that the sets of random variables represented by parameterized
	 * random variables in each parfactor of the given set are equal or 
	 * disjoint to the specified query set.
	 * <br>
	 * In other words, for any parameterized random variable p from
	 * parfactors of the specified set, p and the query set represent identical 
	 * or disjoint sets of random variables.
	 * <br>
	 * This operation is used once at the beginning of the C-FOVE algorithm.
	 * <br>
	 * If the specified set is empty, returns an empty set.
	 * <br>
	 * <b>I am not sure whether this operation should be used every time SHATTER
	 * is called.</b>
	 * 
	 * @param parfactors The set of parfactors to shatter.
	 * @param query A set of random variables to shatter against. It must be 
	 * given in the form of a {@link RandomVariableSet}
	 * @return The specified set of parfactors shattered, or an empty set if
	 * the specified set is also empty.
	 */
	public static Set<Parfactor> shatter (
			Set<Parfactor> parfactors, 
			RandomVariableSet query) {
		
		HashSet<Parfactor> shatteredSet = new HashSet<Parfactor>(parfactors);
		for (Parfactor p : parfactors) {
			if (p.contains(query.getPrv())) {
				HashSet<Constraint> constraints = 
						new HashSet<Constraint>(query.getConstraints());
				shatteredSet.remove(p);
				shatteredSet.addAll(p.splitOnConstraints(constraints));
			}
		}
		return shatteredSet;
	}
	
	/**
	 * This operation eliminates the specified Logical Variable in the 
	 * specified parfactor. This is done using the Counting operation.
	 * <br>
	 * Conditions to call this method:
	 * <li> The specified parfactor must belong to the specified set of
	 * Parfactors
	 * <li> The specified logical variable must appear free in single
	 * parameterized random variable in the specified Parfactor.
	 * <br>
	 * This method does not check for the conditions outlined above. They
	 *  must be verified in the caller method.
	 * 
	 * @param parfactors A set of parfactors
	 * @param parfactorToProcess The parfactor where the logical variable to
	 * eliminate resides. This parfactor must be in the specified set of 
	 * parfactors.
	 * @param lv The free logical variable to eliminate. It must satisfy the 
	 * conditions specified above.
	 * @return The specified set of parfactors modified so that the logical
	 * variable no longer appears free in the specified parfactor.
	 */
	public static Set<Parfactor> countingConvert(
			Set<Parfactor> parfactors, 
			Parfactor parfactorToProcess, 
			LogicalVariable lv) {
		Parfactor result = parfactorToProcess.count(lv);
		parfactors.add(result);
		parfactors.remove(parfactorToProcess);
		return parfactors;
	}
	
	/**
	 * This operation executes a split on the specified parfactor for every 
	 * constant in the population
	 * of the specified Logical Variable.
	 * <br>
	 * Conditions to call this method:
	 * <li> The specified parfactor must belong to the specified set of
	 * Parfactors
	 * <li> The specified logical variable must appear free in 
	 * parameterized random variables of the specified Parfactor.
	 * <br>
	 * <br>
	 * This method does not check for the conditions outlined above. They
	 *  must be verified in the caller method.
	 * It is always possible to propositionalize a parfactor.
	 * <br>
	 * After all the splits, the SHATTER macro operation is invoked to 
	 * guarantee that all parameterized random variables represent equal or
	 * disjoint sets of random variables.
	 * <br>
	 * 
	 * @param parfactors A set of parfactors
	 * @param parfactorToPropositionalize A parfactor from the specified set
	 * @param freeLogicalVariable A free logical variable belonging to the
	 * specified parfactor.
	 * @return The specified set of parfactors with the parfactor 
	 * propositionalized in individuals of the specified logival variable.
	 */
	public static Set<Parfactor> propositionalize(
			Set<Parfactor> parfactors,
			Parfactor parfactorToPropositionalize,
			LogicalVariable freeLogicalVariable) {
		parfactors.remove(parfactorToPropositionalize);
		parfactors.addAll(parfactorToPropositionalize.propositionalize(freeLogicalVariable));
		return shatter(parfactors);
	}
	
	/**
	 * <p>
	 * Expands the specified counting formula in the specified parfactor for all
	 * individuals of the bound variable that satisfy the constraints of the
	 * counting formula.
	 * </p>
	 * <p>
	 * Conditions to call this method:
	 * </p>
	 * <li> The specified parfactor must belong to the specified set of
	 * Parfactors
	 * <li> The specified counting formula must appear in the set of
	 * parameterized random variables of the specified Parfactor.
	 * <br>
	 * <br>
	 * <p>
	 * This method does not check for the conditions outlined above. They
	 *  must be verified in the caller method.
	 * </p>
	 * <p>
	 * Given a set of parfactors, it is always possible to fully expand a 
	 * parfactor.
	 * </p>
	 * <p>
	 * After all the splits, the SHATTER macro operation is invoked to 
	 * guarantee that all parameterized random variables represent equal or
	 * disjoint sets of random variables.
	 * </p>
	 * 
	 * @param parfactors A set of parfactors
	 * @param parfactorToExpand The parfactor in which the expansion will take
	 * place.
	 * @param countingFormula The counting formula to expand.
	 * @return The specified set of parfactor with the counting formula expanded
	 * in the specified parfactor for all individuals satisfying the constraints
	 * of the counting formula.
	 */
	public static Set<Parfactor> fullExpand(
			Set<Parfactor> parfactors,
			Parfactor parfactorToExpand,
			CountingFormula countingFormula) {
		
		parfactors.remove(parfactorToExpand);
		parfactors.add(parfactorToExpand.fullExpand(countingFormula));
		
		return shatter(parfactors);
	}
	
	/**
	 * <p>
	 * Eliminates the set of random variables represented by the specified
	 * parameterized random variable constrained to the given set of
	 * constraints. The parfactors that involve the specified PRV are 
	 * multiplied and then the PRV is eliminated in a lifted manner from the
	 * product, if possible.
	 * </p>
	 * <p>
	 * When it is not possible to eliminate the specified PRV, all parfactors
	 * that involve it are replace by their product.
	 * </p>
	 * <p>
	 * Conditions to call this method:
	 * </p>
	 * <li> The specified set of parfactors must have been shattered 
	 * previously to enable multiplication.
	 * <br>
	 * <br>
	 * <p>
	 * This method does not check for the conditions outlined above. They
	 *  must be verified in the caller method.
	 * </p>
	 * @param parfactors A set of parfactors
	 * @param variableToEliminate The parameterized random variable to
	 * eliminate from the set of parfactors.
	 * @param constraints A set of constraints involving logical variables
	 * from the specified parameterized random variable. It can be empty.
	 * @return The specified set of parfactors with all random variables
	 * represented by the specified PRV subject to the specified set of
	 * constraints eliminated.
	 */
	public static Set<Parfactor> globalSumOut(
			Set<Parfactor> parfactors,
			ParameterizedRandomVariable variableToEliminate,
			Set<Constraint> constraints) {
		
		Parfactor result = SimpleParfactor.getConstantInstance();
		Set<Parfactor> parfactorsCopy = new HashSet<Parfactor>(parfactors);
		for (Parfactor parfactor : parfactorsCopy) {
			if (parfactor.contains(variableToEliminate) 
					&& parfactor.getConstraints().containsAll(constraints)) {
				result = result.multiply(parfactor);
				parfactors.remove(parfactor);
			}
		}
		if (variableToEliminate.getParameters().equals(result.getLogicalVariables())) {
			result = result.sumOut(variableToEliminate);
		}
		if(!result.isConstant()) {
			parfactors.add(result);
		}
		return parfactors;
	}
}


//package br.usp.poli.takiyama.cfove;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.Set;
//
//import br.usp.poli.takiyama.acfove.AggregationParfactor;
//import br.usp.poli.takiyama.common.Constraint;
//import br.usp.poli.takiyama.prv.LogicalVariable;
//import br.usp.poli.takiyama.prv.ParameterizedRandomVariable;
//
//public final class MacroOperations {
//	
//	// Enforces non-instantiability
//	private MacroOperations() { }
//	
//	
//	/**
//	 * Given a set of parfactors &Phi;, a parameterized random variable f(...) and a 
//	 * set of constraints C, the macro-operation multiplies all parfactors from 
//	 * &Phi; containing parameterized random variables that represent 
//	 * ground(f(...)) : C and eliminates random variables ground(f(...)) : C 
//	 * from the product; the macro-operation is only applicable if the product 
//	 * satisfies the pre-conditions for Lifted Elimination. This method does
//	 * not verify those pre-conditions, they must be checked separately using
//	 * the appropriate method from this class.
//	 * <br>
//	 * <br>
//	 * Multiplication operations can always be performed because of initial shattering. 
//	 * <br>
//	 * <br>
//	 * This is the only macro-operation that eliminates random variables from J(&Phi;).
//	 * <br>
//	 * <br>
//	 * 
//	 * @param setOfParfactors The set of parfactors from which the variable
//	 * will be eliminated
//	 * @param variable The parameterized random variable to eliminate
//	 * @param setOfConstraints A set of constraints the given variable must
//	 * obey
//	 */
//	public static Set<SimpleParfactor> globalSumOut(Set<SimpleParfactor> setOfParfactors, 
//			ParameterizedRandomVariable variable, 
//			Set<Constraint> setOfConstraints) {
//		
//		/*
//		 * Input:  set of parfactors Phi
//		 * 	       parameterized random variable f
//		 *         set of constraints C
//		 * Output: set of parfactors Phi with f summed out
//		 * 
//		 * product := unary parfactor
//		 * for each parfactor p in Phi
//		 *     if p contains PRV v such that ground(v):C = ground(f):C
//		 *         product := product * p
//		 *         remove p from Phi
//		 * if product satisfies pre-condition for elimination
//		 *     result := eliminate f from product
//		 * return result
//		 */
//		
//		HashSet<SimpleParfactor> newSetOfParfactors = new HashSet<SimpleParfactor>(setOfParfactors);
//		SimpleParfactor product = SimpleParfactor.getConstantInstance(); //TODO: it would be better to do the first multiplication outside the loop
//		for (SimpleParfactor p : setOfParfactors) {
//			if (p.getParameterizedRandomVariables().contains(variable)) {
//				newSetOfParfactors = new HashSet<SimpleParfactor>(p.multiply(newSetOfParfactors, product));
//				HashSet<SimpleParfactor> copyOfNewSet = new HashSet<SimpleParfactor>(newSetOfParfactors);
//				copyOfNewSet.removeAll(setOfParfactors);
//				product = copyOfNewSet.iterator().hasNext() ? copyOfNewSet.iterator().next() : null;
//				
//				if (product == null) {
//					throw new NullPointerException("The new set is empty!: " + copyOfNewSet);
//				}
//			}
//		}
//		newSetOfParfactors = new HashSet<SimpleParfactor>(product.sumOut(newSetOfParfactors, variable));
//		
//		return newSetOfParfactors;
//	}
//	
//	/**
//	 * Checks if it is possible to apply the method  
//	 * {@link MacroOperations#globalSumOut(Set, ParameterizedRandomVariable, Set)}
//	 * to eliminate a parameterized random variable from a set of parfactors.
//	 * <br>
//	 * This method must be used before the aforementioned method to check the
//	 * validity of the operation. One may get unexpected results if the
//	 * verification is not made.
//	 * @param setOfParfactors The set of parfactors from which the variable
//	 * will be eliminated
//	 * @param variable The parameterized random variable to eliminate
//	 * @param setOfConstraints A set of constraints the given variable must
//	 * obey
//	 * @return True if GLOBAL-SUM-OUT is possible for the given parameters, 
//	 * false otherwise 
//	 */
//	public static boolean conditionsForGlobalSumOutAreMet(
//			Set<SimpleParfactor> setOfParfactors, 
//			ParameterizedRandomVariable variable, 
//			Set<Constraint> setOfConstraints) {
//		// remember that multiplication may be over aggregations or simple
//		// how can i check the sum out condition without doing the multiplication?
//		
//		/*
//		 * for each parfactor p in set of parfactors P
//		 *     if p contains PRV v
//		 *         add p to pool
//		 * all_vars := empty set
//		 * for each parfactor p in pool
//		 *     if p can be multiplied by next parfactor np in pool
//		 *         all_vars := PRVs from p union PRVs from np union all_vars
//		 *     else
//		 *         all_vars := empty set
//		 *         break
//		 * if all_vars = empty set
//		 *     return false
//		 * if sumOutIsPossible(all_vars, v)
//		 *     return true
//		 * return false
//		 * 
//		 */
//		
//		AggregationParfactor agg = null;
//		ArrayList<SimpleParfactor> candidateParfactors = new ArrayList<SimpleParfactor>();
//		ParameterizedRandomVariable childVariable = null;
//		
//		for (SimpleParfactor parfactor : setOfParfactors) {
//			if (parfactor.contains(variable)) {
//				if (parfactor instanceof AggregationParfactor) {
//					agg = (AggregationParfactor) parfactor; // what if there are more aggregation parfactors in the set?
//					childVariable = agg.getChildVariable();
//				} else {
//					candidateParfactors.add(parfactor);	
//				}
//			}
//		}
//		
//		// check if multiplication is possible
//		// it only makes sense when there is an Aggregation Parfactor in the set
//		// TODO: this is utterly ugly
//		if (agg != null) {
//			for (SimpleParfactor parfactor : candidateParfactors) {
//				if (!agg.canBeMultipliedBy(parfactor)) {
//					return false;
//				}
//			}
//			candidateParfactors.add(agg);
//		}
//		
//		HashSet<ParameterizedRandomVariable> variablesFromMultiplication = 
//			new HashSet<ParameterizedRandomVariable>(candidateParfactors.get(0).getParameterizedRandomVariables());
//				
//		for (int i = 0; i < candidateParfactors.size() - 1; i++) {
//			if (candidateParfactors.get(i).canBeMultipliedBy(candidateParfactors.get(i + 1))) {
//				variablesFromMultiplication.addAll(candidateParfactors.get(i + 1).getParameterizedRandomVariables());
//			} else {
//				return false;
//			}
//		}
//		
//		if (sumOutIsPossible(variablesFromMultiplication, variable, childVariable)) {
//			return true;
//		}
//		return false;
//		
//	}
//	
//	/**
//	 * Returns true if it is possible to sum out the <code>variableToSumOut</code>
//	 * given the all parameterized random variables in the factor from the
//	 * parfactor and its child variable, if any.
//	 * <br>
//	 * The <code>childVariable</code> must be provided separately when the
//	 * parfactor is actually an aggregation parfactor, otherwise it must be set
//	 * to <code>null</code>.
//	 * 
//	 * @param variablesFromMultiplication The variables present in the factor
//	 * @param variableToSumOut The parameterized random variable to be summed
//	 * out
//	 * @param childVariable The child parameterized random variable from the
//	 * parfactor, set to null if the is no such variable.
//	 * @return True if summing out the given variable is possible, false
//	 * otherwise.
//	 */
//	private static boolean sumOutIsPossible(
//			Set<ParameterizedRandomVariable> variablesFromMultiplication, 
//			ParameterizedRandomVariable variableToSumOut,
//			ParameterizedRandomVariable childVariable) {
//		
//		if (childVariable != null) { // Product is an aggregation parfactor
//			HashSet<LogicalVariable> parentParameters = new HashSet<LogicalVariable>(variableToSumOut.getParameters());
//			parentParameters.removeAll(childVariable.getParameters());
//			return (parentParameters.size() == 1);
//		} else { // Product is simple parfactor
//			variablesFromMultiplication.remove(variableToSumOut);
//			HashSet<LogicalVariable> allLogicalVariables = new HashSet<LogicalVariable>();
//			for (ParameterizedRandomVariable variable : variablesFromMultiplication) {
//				allLogicalVariables.addAll(variable.getParameters());
//			}
//			return variableToSumOut.getParameters().containsAll(allLogicalVariables);
//		}
//	}
//}
