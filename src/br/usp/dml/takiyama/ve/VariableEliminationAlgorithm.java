/**
 * 
 */
package br.usp.dml.takiyama.ve;

import java.math.BigDecimal;
import java.util.ArrayList;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;

/**
 * This class implements the VE algorithm.
 * @author ftakiyama
 *
 */
public class VariableEliminationAlgorithm {
	/**
	 * This is the main code that will work someday, hopefully.
	 *
	public Factor VariableElimination(RandomVariable[] v, Factor[] f, Assignment[] vo, Variable q, Heuristic h) {
		RandomVariable[] e = Set.subtract(v, Set.union(o,q));  // getNonQueriedAndNonObservedRandomVariables()
		while (f.contains(e)) { // thereIsAFactorInvolvingRandomVariableFromE
			RandomVariable y = h.selectVariable(e);  // selectRandomVaribleAccordingToHeuristic
			f = eliminate(f, y); // eliminateRandomVariableFromFactor(y,F)
			e = Set.subtract(e,y); // removeRandomVariableFromE
		}
		f = multiply(f); // MultiplyRemainingFactors()
		normalizingConstant = sumOut(q,f); // computeNormalizinfConstant
		return f/normalizingConstant;  // returnResult
	}
	
	private Factor[] eliminate(y, f) {
		fy = partition(f, y);
		fwithouty = Set.subtract(f, fy); // getFactorWithoutY
		return Set.union(fwithouty, sumOut(y, multiply(fy, f)));  // multiply factors with y, sum out y and union with factors without y
	} */
	
	private ImmutableSet<RandomVariable> randomVariables;
	private ImmutableSet<Factor> factors;
	private ImmutableSet<RandomVariable> observedRandomVariables;
	private RandomVariable queryRandomVariable;
	private Factor posteriorDistribution;
	
	private RandomVariable randomVariableToEliminate;
	private ImmutableSet<Factor> factorsOnRandomVariableToEliminate;
	private ImmutableSet<Factor> factorsWithoutRandomVariableToEliminate;
	
	private ImmutableSet<RandomVariable> nonQueriedNonObservedRandomVariables;
	
	private BigDecimal normalizingConstant;
	
	/**
	 * Constructor. Initializes the parameters for the algorithm. The
	 * following restrictions must be satisfied:
	 * <li> The set of observed random variables must be a subset of the set of
	 * random variables;
	 * <li> The query random variable must not be observed;
	 * <li> All random variables must appear at least in one factor.
	 * The algorithm does not check for model consistency. If the model provided
	 * is inconsistent, results will be unpredictable.
	 * @param randomVariables Set of random variables
	 * @param factors Set of factors
	 * @param observedRandomVariables Set of observed random variables
	 * @param queryRandomVariable Query random variable
	 */
 	 public VariableEliminationAlgorithm (
			RandomVariable[] randomVariables,
			Factor[] factors,
			RandomVariable[] observedRandomVariables,
			RandomVariable queryRandomVariable) {
		this.randomVariables = ImmutableSet.copyOf(randomVariables);
		this.factors = ImmutableSet.copyOf(factors);
		this.observedRandomVariables = ImmutableSet.copyOf(observedRandomVariables);
		this.queryRandomVariable = RandomVariable.copyOf(queryRandomVariable);
		
		if (!Sets.difference(this.observedRandomVariables, this.randomVariables)
				.immutableCopy()
				.isEmpty()) {
			throw new IllegalArgumentException("The set of observed random " +
					"variables is not a sub set of the set of random variables" +
					"provided.");
		}
		
		if (this.observedRandomVariables.contains(this.queryRandomVariable)) {
			throw new IllegalArgumentException("The query random variable" +
					"is in the set of observed random variables.");
		}
		
		// should check if all random variables appear in at least on factor
	}
	
 	/**
 	 * Given a set of factors F and a random variable v, extracts from F the
 	 * subset of factors that have the random variable v. 
 	 * <br>
 	 * For instance, if F = {f(x1,x2,x3), g(x1,x4), h(x2,x4)} and v = x1, then
 	 * the extracted set will be {f(x1,x2,x3), g(x1,x4)}.
 	 * @param v The random variable that defines the partition.
 	 */
	private void getFactorsOn(RandomVariable v) {
		UnmodifiableIterator<Factor> it = factors.iterator();
		ArrayList<Factor> factorsOnRandomVariable = new ArrayList<Factor>(); 
		
		while (it.hasNext()) {
			Factor currentFactor = it.next();
			if (currentFactor.getRandomVariableIndex(v) >= 0) {
				factorsOnRandomVariable.add(currentFactor);
			}
			
		}
		
		factorsOnRandomVariableToEliminate = ImmutableSet.copyOf(
				factorsOnRandomVariable.toArray(
						new Factor[factorsOnRandomVariable.size()]
						           )
				);
	}
 	 
	/*
	 * Eliminates a random variable from a set of factors by multiplying those
	 * factors that have the specified random variable and summing it out. The
	 * result is then united with the subset of factors that did not have the
	 * specified random variable.
	 */
	private void eliminateRandomVaribleFromFactors() {
		getFactorsOn(randomVariableToEliminate);
		factorsWithoutRandomVariableToEliminate = Sets
			.difference(factors, 
						factorsOnRandomVariableToEliminate)
			.immutableCopy();
		ImmutableSet<Factor> sumOut = ImmutableSet
			.of(FactorOperation
				.sumOut(FactorOperation
					.product(factorsOnRandomVariableToEliminate
						.toArray(new Factor[factorsOnRandomVariableToEliminate.size()])),
                    randomVariableToEliminate)); 
		factors = Sets
			.union(factorsWithoutRandomVariableToEliminate,
				   sumOut)
			.immutableCopy();
	}
	
	/**
	 * Creates a set with the random variables that are neither queried nor
	 * observed.
	 */
	private void getNonQueriedNonObservedRandomVariables() {
		nonQueriedNonObservedRandomVariables = Sets
			.difference(
				randomVariables, 
				Sets
				.union(
					observedRandomVariables, 
					ImmutableSet
					.of(queryRandomVariable)))
			.immutableCopy();
	}
	
	/**
	 * Returns true if there is a factor with non queried and non observed
	 * random variable, false otherwise.
	 * @return True if there is a factor with non queried and non observed
	 * random variable, false otherwise.
	 */
	private boolean thereIsAFactorWithNonQueriedNonObservedRandomVariable() {
		UnmodifiableIterator<Factor> factorIterator = factors.iterator();
		
		while (factorIterator.hasNext()) {
			Factor currentFactor = factorIterator.next();
			UnmodifiableIterator<RandomVariable> variableIterator 
					= nonQueriedNonObservedRandomVariables.iterator();
			while (variableIterator.hasNext()) {
				if (currentFactor
						.getRandomVariableIndex(
								variableIterator
								.next()
						) >= 0
					)
					return true;
			}
		}
		return false;
	}
	
	/**
	 * Selects a non queried, non observed random variable to eliminate.
	 * The current heuristic is simply getting the first variable from the set. 
	 */
	private void selectRandomVariableAccordingToHeuristic() {
		randomVariableToEliminate = nonQueriedNonObservedRandomVariables
			.toArray(new RandomVariable[nonQueriedNonObservedRandomVariables.size()])
			[0];
	}
	
	/**
	 * Removes the specified random variable from the list of non-queried, 
	 * non-observed random variables.
	 * @param v The random variable to be removed.
	 */
	private void removeFromNonQueriedNonObserved(RandomVariable v) {
		nonQueriedNonObservedRandomVariables = Sets
			.difference(
				nonQueriedNonObservedRandomVariables, 
				ImmutableSet
					.of(randomVariableToEliminate))
			.immutableCopy(); 
	}
	
	/**
	 * Multiplies all factors.
	 */
	private void multiplyAllFactors() {
		posteriorDistribution = FactorOperation
			.product(factors
				.toArray(new Factor[factors.size()]));
	}
	
	/**
	 * Calculates the normalizing constant.
	 * @throws Exception If the factor does not have size 1.
	 */
	private void calculateNormalizingConstant() throws Exception {
		normalizingConstant = FactorOperation
			.sumOut(posteriorDistribution, 
					queryRandomVariable)
			.toBigDecimal();
	}
	
	/**
	 * Calculates the final posterior distribution by normalizing the result.
	 */
	private void calculatePosteriorDistribution() {
		posteriorDistribution = FactorOperation
			.divide(posteriorDistribution, 
					normalizingConstant);
	}
	
	/**
	 * Executes the Variable Elimination Algorithm.
	 * @return A factor representing the posterior distribution on the query
	 * random variable.
	 * @throws Exception If something wrong happens :P.
	 */
	public Factor execute() throws Exception {
		getNonQueriedNonObservedRandomVariables();
		while (thereIsAFactorWithNonQueriedNonObservedRandomVariable()) {
			selectRandomVariableAccordingToHeuristic();
			eliminateRandomVaribleFromFactors();
			removeFromNonQueriedNonObserved(randomVariableToEliminate);
		}
		multiplyAllFactors();
		calculateNormalizingConstant();
		calculatePosteriorDistribution();
		return posteriorDistribution;
	}
}
