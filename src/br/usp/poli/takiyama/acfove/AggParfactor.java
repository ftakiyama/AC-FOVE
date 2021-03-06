/*******************************************************************************
 * Copyright 2014 Felipe Takiyama
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package br.usp.poli.takiyama.acfove;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import br.usp.poli.takiyama.cfove.StdParfactor;
import br.usp.poli.takiyama.cfove.StdParfactor.StdParfactorBuilder;
import br.usp.poli.takiyama.common.AggregationParfactor;
import br.usp.poli.takiyama.common.Builder;
import br.usp.poli.takiyama.common.ConstantFactor;
import br.usp.poli.takiyama.common.Constraint;
import br.usp.poli.takiyama.common.Distribution;
import br.usp.poli.takiyama.common.Factor;
import br.usp.poli.takiyama.common.InequalityConstraint;
import br.usp.poli.takiyama.common.MultiplicationChecker;
import br.usp.poli.takiyama.common.Parfactor;
import br.usp.poli.takiyama.common.ParfactorVisitor;
import br.usp.poli.takiyama.common.SplitResult;
import br.usp.poli.takiyama.common.StdDistribution;
import br.usp.poli.takiyama.common.StdFactor;
import br.usp.poli.takiyama.common.Tuple;
import br.usp.poli.takiyama.common.VisitableParfactor;
import br.usp.poli.takiyama.prv.Binding;
import br.usp.poli.takiyama.prv.Constant;
import br.usp.poli.takiyama.prv.CountingFormula;
import br.usp.poli.takiyama.prv.LogicalVariable;
import br.usp.poli.takiyama.prv.Operator;
import br.usp.poli.takiyama.prv.Prv;
import br.usp.poli.takiyama.prv.RangeElement;
import br.usp.poli.takiyama.prv.StdLogicalVariable;
import br.usp.poli.takiyama.prv.StdPrv;
import br.usp.poli.takiyama.prv.Substitution;
import br.usp.poli.takiyama.prv.Term;
import br.usp.poli.takiyama.utils.Lists;
import br.usp.poli.takiyama.utils.MathUtils;
import br.usp.poli.takiyama.utils.Sets;

public class AggParfactor implements AggregationParfactor, VisitableParfactor {

	private final Prv parent;
	private final Prv child;
	private final Factor factor;
	private final Operator<? extends RangeElement> operator;
	private final Set<Constraint> constraintsNotOnExtra;
	private final Set<Constraint> constraintsOnExtra;
	private final LogicalVariable extraVar;
	
	/*
	 * List of context parameterized random variables.
	 * Context variables describe dependency in aggregation between
	 * parent and child.
	 * On standard aggregation parfactors, this list is empty. 
	 */
	private final List<Prv> context;
	
	
	/* ************************************************************************
	 *    Builders
	 * ************************************************************************/
	
	/**
	 * Builder for {@link AggParfactor}.
	 * <p>
	 * There are two ways of specifying the factor associated with this
	 * aggregation parfactor: either pass a factor that was previously built
	 * using {@link #factor(Factor)} or pass the values that constitute the
	 * factor using {@link #values()}. These operations overwrite changes
	 * made by previous calls. 
	 * </p>
	 */
	public static class AggParfactorBuilder implements Builder<AggParfactor> {

		// mandatory parameters 
		private final Prv p;
		private Prv c; // not final to allow renaming
		private final Operator<? extends RangeElement> op;
		private final LogicalVariable lv;
		
		// optional parameters
		private List<BigDecimal> values;
		private Set<Constraint> constraintsOnExtra;
		private Set<Constraint> constraintsNotOnExtra;
		private List<Prv> ctxt;
		
		public AggParfactorBuilder(Prv p, Prv c, Operator<? extends RangeElement> op) 
					throws IllegalArgumentException {
			this.p = p;
			this.c = c;
			this.op = op;
			this.values = new ArrayList<BigDecimal>();
			this.constraintsNotOnExtra = new HashSet<Constraint>(0);
			this.constraintsOnExtra = new HashSet<Constraint>(0);
			this.lv = setExtra();
			this.ctxt = new ArrayList<Prv>(0);
		}
		
		public AggParfactorBuilder(AggregationParfactor ap) {
			this.p = ap.parent();
			this.c = ap.child();
			this.op = ap.operator();
			this.values = ap.factor().values();
			this.constraintsNotOnExtra = ap.constraintsNotOnExtra();
			this.constraintsOnExtra = ap.constraintsOnExtra();
			this.lv = StdLogicalVariable.getInstance(ap.extraVariable());
			this.ctxt = ap.context();
		}
				
		/**
		 * Puts in <code>lv</code> the logical variable that is present in
		 * the parent PRV and not in child PRV.
		 * 
		 * @return The extra logical variable in parent PRV
		 * @throws IllegalArgumentException If the parent PRV has a number
		 * of extra variables that is different from 1.
		 */
		private LogicalVariable setExtra() throws IllegalArgumentException {
			List<LogicalVariable> pVars = p.parameters();
			List<LogicalVariable> cVars = c.parameters();
			List<LogicalVariable> diff = Lists.difference(pVars, cVars);
			if (diff.size() == 1) {
				return StdLogicalVariable.getInstance(diff.get(0));
			} else {
				throw new IllegalArgumentException();
			}
		}
		
		/**
		 * Adds the specified constraint to this builder.
		 * @param c The constraint to add
		 * @return This builder with the constraint added
		 */
		public AggParfactorBuilder constraint(Constraint c) {
			if (c.contains(lv)) {
				constraintsOnExtra.add(c);
			} else {
				constraintsNotOnExtra.add(c);
			}
			return this;
		}
		
		/**
		 * Adds the specified constraints to this builder.
		 * @param c The constraints to add
		 * @return This builder with the constraints added
		 */
		public AggParfactorBuilder constraints(Constraint ... c) {
			for (Constraint cons : c) {
				constraint(cons);
			}
			return this;
		}
		
		/**
		 * Adds the specified constraints to this builder.
		 * @param c The constraints to add
		 * @return This builder with the constraints added
		 */
		public AggParfactorBuilder constraints(Set<Constraint> c) {
			for (Constraint cons : c) {
				constraint(cons);
			}
			return this;
		}
		
		/**
		 * Sets the factor for this builder.
		 * 
		 * @param f The factor 
		 * @return This builder with the factor updated.
		 * @throws IllegalArgumentException If the specified factor is not 
		 * consistent with this builder.
		 * @see #isConsistent(Factor)
		 */
		public AggParfactorBuilder factor(Factor f) throws IllegalArgumentException {
			if (isConsistent(f)) {
				values = f.values();
				return this;
			} else if (needsReordering(f)) {
				Factor reference = ConstantFactor.getInstance(Lists.union(Lists.listOf(p), ctxt));
				values = f.reorder(reference).values();
				return this;
			} else {
				throw new IllegalArgumentException();
			}
		}
		
		/**
		 * Returns <code>true</code> if the specified factor is consistent 
		 * with this builder, that is, prvs from the factor are the parent +
		 * context PRVs, and the parent is the first element of the list.
		 * 
		 * @param f The factor to evaluate
		 */
		private boolean isConsistent(Factor f) {
			List<Prv> varsFromBuilder = Lists.union(Lists.listOf(p), ctxt);
			return varsFromBuilder.equals(f.variables());
		}
		
		private boolean needsReordering(Factor f) {
			List<Prv> varsFromBuilder = Lists.union(Lists.listOf(p), ctxt);
			List<Prv> varsFromFactor = f.variables();
			boolean sameElements = Lists.sameElements(varsFromBuilder, varsFromFactor);
			boolean differentOrder = !varsFromBuilder.equals(varsFromFactor);
			return sameElements && differentOrder;
		}
		
		/**
		 * Sets factor values for this builder.
		 * 
		 * @param v A list of doubles
		 * @return This builder updated with the specified values
		 */
		public AggParfactorBuilder values(double ... v) {
			values.clear();
			for (double d : v) {
				values.add(BigDecimal.valueOf(d));
			}
			return this;
		}
		
		/**
		 * Sets factor values for this builder
		 * 
		 * @param v A list of {@link BigDecimal}
		 * @return This builder updated with the specified values
		 */
		public AggParfactorBuilder values(List<BigDecimal> v) {
			values = Lists.listOf(v);
			return this;
		}
		
		/**
		 * Sets the child PRV. Used only internally.
		 * 
		 * @param c The child PRV
		 * @return This builder with the new child PRV
		 */
		AggParfactorBuilder child(Prv c) {
			this.c = c;
			return this;
		}
		
		/**
		 * Sets the list of context PRVs.
		 * @param contextVars A list of context PRVs
		 * @return This builder with the list of context PRVs.
		 */
		public AggParfactorBuilder context(List<Prv> contextVars) {
			ctxt = Lists.listOf(contextVars);
			return this;
		}
		
		/**
		 * Sets the list of context PRVs.
		 * @param contextVars A list of context PRVs
		 * @return This builder with the list of context PRVs.
		 */
		public AggParfactorBuilder context(Prv ... contextVars) {
			ctxt = Lists.listOf(Arrays.asList(contextVars));
			return this;
		}
		
		@Override
		public AggParfactor build() {
			// Cannot simplify logical variables here because in aggregation
			// parfactors this operation may result in StdParfactor
			return new AggParfactor(this);
		}
		
		/**
		 * Returns the factor defined by PRVs and values in this builder.
		 * <p>
		 * If no values were set, returns a constant factor on parent + context
		 * PRVs, otherwise tries to build the factor on parent + context PRVs
		 * using the values given by {@link #values()}.
		 * </p>
		 * @return The factor defined by PRVs and values in this builder.
		 * @throws IllegalStateException 
		 */
		private Factor getFactor() throws IllegalStateException {
			Factor factor;
			List<Prv> variables = Lists.listOf(p);
			variables.addAll(ctxt);
			if (values.isEmpty()) {
				factor = ConstantFactor.getInstance(variables);
			} else {
				try {
					factor = StdFactor.getInstance("", variables, values);
				} catch (IllegalArgumentException e) {
					throw new IllegalStateException();
				}
			}
			return factor;
		}
	}
	
	
	/* ************************************************************************
	 *    Auxiliary classes
	 * ************************************************************************/
	
	/**
	 * This class encapsulates the algorithm to verify if this parfactor is 
	 * splittable on a specified substitution.
	 */
	private class Split {
		
		private final SubstitutionType substitution;
		
		private Split(LogicalVariable replaced, Term replacement) {
			// I think there should a better way to do this
			if (replaced.equals(extraVar)) {
				if (replacement.isConstant()) {
					Constant c = (Constant) replacement;
					substitution = new ExtraConstant(c);
				} else {
					LogicalVariable x = (LogicalVariable) replacement;
					substitution = new ExtraVariable(x);
				}
			} else {
				if (replacement.equals(extraVar)) {
					substitution = new VariableExtra(replaced);
				} else if (replacement.isConstant()) {
					Constant c = (Constant) replacement;
					substitution = new VariableConstant(replaced, c);
				} else {
					LogicalVariable x = (LogicalVariable) replacement;
					substitution = new VariableVariable(replaced, x);
				}
			}
		}
		
		/**
		 * Returns <code>true</code> if this parfactor can be split in this
		 * substitution.
		 * 
		 * @return <code>true</code> if this parfactor can be split in this
		 * substitution, <code>false</code> otherwise.
		 */
		private boolean isValid() {
			Constraint c = substitution.toInequalityConstraint();
			boolean isNotInConstraints = !constraints().contains(c);
			boolean isValid = substitution.isValid();
			return isNotInConstraints && isValid;
		}
		
		
		/**
		 * Represents substitutions of the type A/t, where A is the extra
		 * logical variable from the parent's PRV and t is a constant.
		 */
		private class ExtraConstant implements SubstitutionType {

			private final Constant constant;
			
			private ExtraConstant(Constant c) {
				this.constant = c;
			}
			
			@Override
			public boolean isValid() {
				return extraVar.population().contains(constant);
			}

			@Override
			public Constraint toInequalityConstraint() {
				Term a = StdLogicalVariable.getInstance(extraVar);
				Term t = Constant.getInstance(constant);
				return InequalityConstraint.getInstance(a, t);
			}
		}
		
		/**
		 * Represents substitutions of the type A/X, where A is the extra
		 * logical variable from the parent's PRV and X is a logical variable.
		 */
		private class ExtraVariable implements SubstitutionType {
			
			private final LogicalVariable var;
			
			private ExtraVariable(LogicalVariable x) {
				this.var = x;
			}

			@Override
			public boolean isValid() {
				List<LogicalVariable> param = parent.parameters();
				param.remove(extraVar);
				return param.contains(var);
			}

			@Override
			public Constraint toInequalityConstraint() {
				Term a = StdLogicalVariable.getInstance(extraVar);
				Term x = StdLogicalVariable.getInstance(var);
				return InequalityConstraint.getInstance(a, x);
			}
		}
		
		/**
		 * Represents substitutions of the type X/A, where A is the extra
		 * logical variable from the parent's PRV and X is a logical variable.
		 */
		private class VariableExtra implements SubstitutionType {
			
			private final LogicalVariable var;
			
			private VariableExtra(LogicalVariable x) {
				this.var = x;
			}

			@Override
			public boolean isValid() {
				List<LogicalVariable> param = parent.parameters();
				param.remove(extraVar);
				return param.contains(var);
			}

			@Override
			public Constraint toInequalityConstraint() {
				Term a = StdLogicalVariable.getInstance(extraVar);
				Term x = StdLogicalVariable.getInstance(var);
				return InequalityConstraint.getInstance(x, a);
			}
		}
		
		/**
		 * Represents substitutions of the type X/t, where X is a logical
		 * variable different from parent PRV's extra variable and
		 * t is a constant.
		 */
		private class VariableConstant implements SubstitutionType {

			private final LogicalVariable var;
			private final Constant constant;
			
			
			private VariableConstant(LogicalVariable x, Constant c) {
				this.var = x;
				this.constant = c;
			}
			
			@Override
			public boolean isValid() {
				return child.parameters().contains(var) 
						&& var.population().contains(constant);
			}

			@Override
			public Constraint toInequalityConstraint() {
				Term x = StdLogicalVariable.getInstance(var);
				Term t = Constant.getInstance(constant);
				return InequalityConstraint.getInstance(x, t);
			}
		}
		
		/**
		 * Represents substitutions of the type X/Y, where X is a logical
		 * variable different from parent PRV's extra variable and
		 * Y is a logical variable in the same condition as X.
		 */
		private class VariableVariable implements SubstitutionType {
			
			private final LogicalVariable x;
			private final LogicalVariable y;
			
			private VariableVariable(LogicalVariable x, LogicalVariable y) {
				this.x = x;
				this.y = y;
			}

			@Override
			public boolean isValid() {
				return child.parameters().contains(x) 
						&& child.parameters().contains(y);  
			}

			@Override
			public Constraint toInequalityConstraint() {
				Term x1 = StdLogicalVariable.getInstance(x);
				Term y1 = StdLogicalVariable.getInstance(y);
				return InequalityConstraint.getInstance(x1, y1);
			}
		}
		
		
	}
	
	/**
	 * This class represents possible types of substitutions. There are five:
	 * A/c, A/X, X/A, X/c, X/Y, where A is the extra logical variable in 
	 * parent PRV, X and Y are logical variables different from A and c is
	 * a constant.
	 */
	private interface SubstitutionType {
		
		/**
		 * Returns <code>true</code> if this parfactor can be split in this
		 * substitution.
		 * 
		 * @return <code>true</code> if this parfactor can be split in this
		 * substitution, <code>false</code> otherwise.
		 */
		boolean isValid();
		
		/**
		 * Returns a {@link InequalityConstraint} based on terms from this
		 * substitution.
		 * 
		 * @return a {@link InequalityConstraint} based on terms from this
		 * substitution.
		 */
		Constraint toInequalityConstraint();
	}
	
	
	/**
	 * This class encapsulates the splitting algorithm
	 */
	private class Splitter {
		
		private final SplitterType splitter;
		
		private Splitter(AggregationParfactor agg, Substitution s)  {
			if (s.has(extraVar)) {
				splitter = new SplitterInvolvingExtra(agg, s);
			} else {
				splitter = new SplitterWithoutExtra(agg, s);
			}
		}
		
		private SplitResult split() {
			return splitter.split();
		}
		
		private class SplitterInvolvingExtra implements SplitterType {

			private Substitution substitution;
			private AggregationParfactor parfactorToSplit;
			private Prv auxChild;
			
			private SplitterInvolvingExtra(AggregationParfactor agg, Substitution s) {
				substitution = s;
				parfactorToSplit = agg;
				setAuxChild();
			}
			
			private void setAuxChild() {
				Prv child = parfactorToSplit.child();
				auxChild = child.rename(child.name() + "'");
			}
			
			@Override
			public SplitResult split() {
				return SplitResult.getInstance(result(), residue());
			}

			private Parfactor residue() {
				Constraint c = substitution.first().toInequalityConstraint();
				return new AggParfactorBuilder(parfactorToSplit).constraint(c)
						.child(auxChild).build()
						.simplifyLogicalVariables();
			}
			
			private Parfactor result() {
				Set<Constraint> constraints = Sets.apply(substitution, parfactorToSplit.constraints());
				List<Prv> prvs = setPrvs();
				List<BigDecimal> values = setValues(prvs);
				return new StdParfactorBuilder().constraints(constraints)
						.variables(prvs).values(values).build()
						.simplifyLogicalVariables();
			}
			
			private List<Prv> setPrvs() {
				List<Prv> vars = Lists.listOf(parfactorToSplit.context());
				vars.add(0, parfactorToSplit.parent().apply(substitution));
				vars.add(auxChild);
				vars.add(parfactorToSplit.child());
				
				return vars;
			}
			
			private List<BigDecimal> setValues(List<Prv> prvs) {
				List<BigDecimal> values = new ArrayList<BigDecimal>();
				Factor newStructure = StdFactor.getInstance(prvs);
				for (Tuple<? extends RangeElement> tuple : newStructure) {
					RangeElement p = tuple.get(0);
					RangeElement cAux = tuple.get(tuple.size() - 2);
					RangeElement c = tuple.get(tuple.size() - 1);
					if (apply(operator, p, cAux).equals(c)) {
						values.add(correctedValue(p));
					} else {
						values.add(BigDecimal.ZERO);
					}
				}
				return values;
			}
			
			private BigDecimal correctedValue(RangeElement pVal) {
				Tuple<RangeElement> t = Tuple.getInstance(pVal);
				BigDecimal base = parfactorToSplit.factor().getValue(t);
				Prv p = replaceExtra();
				int rp = p.groundSetSize(constraintsNotOnExtra);
				int rc = parfactorToSplit.child().groundSetSize(constraintsNotOnExtra);
				return MathUtils.pow(base, rp, rc);
			}
			
			private Prv replaceExtra() {
				Binding b = Binding.getInstance(extraVar, extraVar.population().individualAt(0));
				Substitution s = Substitution.getInstance(b);
				Prv p = parfactorToSplit.parent().apply(s);
				return p;
			}
			
		}
		
		private class SplitterWithoutExtra implements SplitterType {

			private Substitution substitution;
			private AggregationParfactor parfactorToSplit;
			
			private SplitterWithoutExtra(AggregationParfactor agg, Substitution s) {
				substitution = s;
				parfactorToSplit = agg;
			}
			
			@Override
			public SplitResult split() {
				return SplitResult.getInstance(result(), residue());
			}
			
			private Parfactor residue() {
				Constraint c = substitution.first().toInequalityConstraint();
				return new AggParfactorBuilder(parfactorToSplit).constraint(c).build()
						.simplifyLogicalVariables();
			}
			
			private Parfactor result() {
				return parfactorToSplit.apply(substitution)
						.simplifyLogicalVariables();
			}
			
		}
	}
	
	
	/**
	 * This class represents possible types of splits. There two of them:
	 * split involving parent's extra logical variable and split not involving
	 * the extra variable.
	 *
	 */
	private interface SplitterType {
		public SplitResult split();
	}
	
	
	/**
	 * This class encapsulates multiplication algorithm.
	 */
	private class Multiplier {
		private final Parfactor operand;
		private final AggregationParfactor multiplicand;
		
		private Multiplier(AggregationParfactor thiz, Parfactor other) {
			multiplicand = thiz;
			operand = other;
		}
		
		private Parfactor multiply() {
			Factor f = multiplicand.factor().multiply(operand.factor());
			Parfactor product = new AggParfactorBuilder(multiplicand).factor(f).build();
			return product;
		}
	}
	
	
	/**
	 * This class encapsulates sum out algorithm.
	 * <p>
	 * The conversion can be made only if the set of all constraints in
	 * this aggregation parfactor is in the normal form. 
	 * </p>
	 * <p>
	 * The conversion results in two parfactors, one of them involving a 
	 * counting formula. This method returns a list of the resulting parfactors
	 * in the following order: the first does involves counting formulas and
	 * the second does not.
	 * </p>
	 */
	private class Eliminator {
		
		private final AggregationParfactor parfactor;
		private final StdParfactorBuilder builder;
		
		private Eliminator(AggregationParfactor ag) {
			this.parfactor = ag;
			this.builder = new StdParfactorBuilder();
		}
		
		private Parfactor eliminate() {
			Set<Constraint> constraints = parfactor.constraints();
			Factor factor = setFactor();
			
			Parfactor result = builder.constraints(constraints)
					.factor(factor).build();
			
			if (childHasParameterNotInParent()) {
				LogicalVariable extraParameter = getExtraParameterFromChild();
				result = result.count(extraParameter);
			}
			
			return result;
		}
		
		private Factor setFactor() {
			Factor current = getBase();
			int domainSize = parfactor.extraVariable()
					.numberOfIndividualsSatisfying(parfactor.constraintsOnExtra());
			String binSize = Integer.toBinaryString(domainSize);
			
			for (int k = 1; k < binSize.length(); k++) {
				Factor previous = StdFactor.getInstance(current);
				for (Tuple<RangeElement> x : previous) {
					BigDecimal sum;
					if (binSize.charAt(k) == '0') {
						sum = getDoubleComposition(previous, x);
					} else {
						sum = getTripleComposition(previous, x);
					}
					Tuple<RangeElement> xTuple = Tuple.getInstance(x);
					current = current.set(xTuple, sum);
				}
			}
			
			return current;
		}
		
		private BigDecimal getDoubleComposition(Factor factor, Tuple<RangeElement> x) {
			int childIndex = 0;
			RangeElement childValue = x.get(childIndex);
			BigDecimal sum = BigDecimal.ZERO;
			List<RangeElement> childRange = parfactor.child().range();
			for (RangeElement y : childRange) {
				Tuple<RangeElement> yTuple = x.set(childIndex, y);
				for (RangeElement z : childRange) {
					Tuple<RangeElement> zTuple = x.set(childIndex, z);
					if (apply(parfactor.operator(), y, z).equals(childValue)) {
						sum = sum.add(factor.getValue(yTuple).multiply(factor.getValue(zTuple), MathUtils.CONTEXT), MathUtils.CONTEXT);
					}
				}
			}
			return sum;
		}
		
		private BigDecimal getTripleComposition(Factor factor, Tuple<RangeElement> x) {
			int childIndex = 0;
			RangeElement childValue = x.get(childIndex);
			BigDecimal sum = BigDecimal.ZERO;
			List<RangeElement> childRange = parfactor.child().range();
			for (RangeElement y : childRange) {
				Tuple<RangeElement> yTuple = x.set(childIndex, y);
				for (RangeElement z : childRange) {
					Tuple<RangeElement> zTuple = x.set(childIndex, z);
					for (RangeElement w : childRange) {
						Tuple<RangeElement> wTuple = x.set(childIndex, w);
						if (apply(parfactor.operator(), w, y, z).equals(childValue)) {
							BigDecimal fw = parfactor.factor().getValue(wTuple);
							BigDecimal fy = factor.getValue(yTuple);
							BigDecimal fz = factor.getValue(zTuple);
							sum = sum.add(fw.multiply(fy, MathUtils.CONTEXT).multiply(fz, MathUtils.CONTEXT), MathUtils.CONTEXT);
						}
					}
				}
			}
			return sum;
		}
		
		/**
		 * Builds the base factor F0
		 */
		private Factor getBase() {
			List<Prv> prvs = new ArrayList<Prv>();
			prvs.add(parfactor.child());
			prvs.addAll(parfactor.context());
			
			Factor tempBase = ConstantFactor.getInstance(prvs);
			
			int size = parfactor.child().range().size();
			List<BigDecimal> vals = new ArrayList<BigDecimal>(size);
			for (Tuple<RangeElement> tuple : tempBase) {
				RangeElement childValue = tuple.get(0);
				if (parfactor.parent().range().contains(childValue)) {
					vals.add(parfactor.factor().getValue(tuple));
				} else {
					vals.add(BigDecimal.ZERO);
				}
			}
			return StdFactor.getInstance("", prvs, vals);
		}
		
		/**
		 * Returns <code>true</code> if the child PRV has exactly one extra
		 * parameter that is not present in parameters from parent PRV.
		 * 
		 * @return <code>true</code> if |param(c) \ param(p)| = 1, 
		 * <code>false</code> otherwise.
		 */
		private boolean childHasParameterNotInParent() {
			List<LogicalVariable> parentParam = parfactor.parent().parameters();
			List<LogicalVariable> childParam = parfactor.child().parameters();
			List<LogicalVariable> difference = Lists.difference(childParam, 
					parentParam);
			return (difference.size() == 1);
		}
		
		/**
		 * Returns the logical variable in the child PRV that is not present in
		 * the parent PRV.
		 * 
		 * @return The logical variable in the child PRV that is not present in
		 * the parent PRV.
		 */
		private LogicalVariable getExtraParameterFromChild() {
			List<LogicalVariable> difference = Lists.difference(
					parfactor.child().parameters(),
					parfactor.parent().parameters());
			return difference.get(0);
		}
	}
	
	
	/**
	 * This class encapsulates the algorithm to convert aggregation parfactors
	 * into standard parfactors.
	 */
	private class Converter {
		
		private final AggregationParfactor ap;
		
		/**
		 * Creates an instance of converter.
		 * @param ap The aggregation parfactor to convert to standard 
		 * parfactors.
		 */
		private Converter(AggregationParfactor ap) { 
			this.ap = ap;
		}
		
		/**
		 * Returns a list of standard parfactors whose product is 
		 * equivalent to this aggregation parfactor.
		 */
		private Distribution convert() {
			Parfactor parent = getParfactorOnParent();
			Parfactor child = getParfactorOnChild();
			Distribution dist = StdDistribution.of(parent, child);
			return dist;
		}
		
		/**
		 * Returns the parfactor involving the parent PRV.
		 */
		private Parfactor getParfactorOnParent() {
			List<Prv> vars = ap.context();
			vars.add(0, ap.parent());
			Parfactor parent = new StdParfactorBuilder()
					.constraints(ap.constraints())//.variables(vars)
					.factor(ap.factor()).build();
			return parent;
		}
		
		/**
		 * Returns the parfactor involving a counting formula on the parent
		 * and the extra logical variable.
		 */
		private Parfactor getParfactorOnChild() {
			
			List<Prv> vars = getPrvsForParfactorOnChild();
			
			Factor structure = ConstantFactor.getInstance(vars);
			List<BigDecimal> vals = new ArrayList<BigDecimal>();
			
			for (Tuple<RangeElement> tuple : structure) {
				RangeElement histogram = tuple.get(0);
				RangeElement condensedHistogram = histogram.apply(ap.operator());
				RangeElement childValue = tuple.get(1);
				if (condensedHistogram.equals(childValue)) {
					vals.add(BigDecimal.ONE);
				} else {
					vals.add(BigDecimal.ZERO);
				}
			}
			
			Parfactor child = new StdParfactorBuilder()
					.constraints(ap.constraintsNotOnExtra())
					.variables(vars).values(vals).build();
			
			return child;
		}
		
		/**
		 * Counts the extra variable from parent's PRV
		 */
		private Prv countParent() {
			return CountingFormula.getInstance(ap.extraVariable(), 
					ap.parent(), ap.constraintsOnExtra());
		}
		
		/**
		 * Builds the list of PRVs for the parfactor on child PRV. The order
		 * is counted, child, context variables.
		 */
		private List<Prv> getPrvsForParfactorOnChild() {
			Prv counted = countParent();
			List<Prv> vars = new ArrayList<Prv>(ap.context().size() + 2);
			vars.add(counted);
			vars.add(ap.child());
			vars.addAll(ap.context());
			return vars;
		}
		
	}
	
	// TODO if you like spaghetti. here it is
	private class Simplifier {
		
		private Parfactor simplified;
		private Set<Constraint> unaryConstraints;
		private LogicalVariable extraVariable;
		
		private Simplifier(AggregationParfactor parfactor) {
			this.unaryConstraints = new HashSet<Constraint>();
			setSimplified(parfactor);
			this.extraVariable = parfactor.extraVariable();
		}
		
		/**
		 * Sets the simplified parfactor with the specified parfactor, updating
		 * the set of unary constraints.
		 */
		private void setSimplified(Parfactor parfactor) throws IllegalArgumentException {
			if (parfactor == null) {
				throw new IllegalArgumentException();
			}
			simplified = parfactor;
			unaryConstraints.clear();
			for (Constraint constraint : simplified.constraints()) {
				if (constraint.isUnary()) {
					unaryConstraints.add(constraint);
				}
			}
		}
		
		/**
		 * Replaces all logical variable constrained to a single individual
		 * with this individual
		 */
		private Parfactor simplify() {
			LinkedList<LogicalVariable> queue = getVariablesInConstraints();
			while (!queue.isEmpty()) {
				LogicalVariable logicalVariable = queue.poll();
				int populationSize = logicalVariable
						.numberOfIndividualsSatisfying(unaryConstraints);
				switch (populationSize) {
				case 0:
					return StdParfactor.getInstance();
				case 1:
					Substitution sub = getSubstitution(logicalVariable);
					if (logicalVariable.equals(extraVariable)) {
						return simplifyAggregation(sub).simplifyLogicalVariables();
					} else {
						queue.addAll(variablesInBinaryConstraintsInvolving(logicalVariable));
						setSimplified(simplified.apply(sub));
					}
					break;
				default:
					break;
				}
			}
			
			// stupid
			for (LogicalVariable lv : logicalVariables()) {
				int populationSize = lv.numberOfIndividualsSatisfying(unaryConstraints);
				switch (populationSize) {
				case 0:
					return StdParfactor.getInstance();
				case 1:
					Substitution sub = getSubstitution(lv);
					if (lv.equals(extraVariable)) {
						return simplifyAggregation(sub).simplifyLogicalVariables();
					} else {
						setSimplified(simplified.apply(sub));
					}
					break;
				default:
					break;
				}
			}
			
			return simplified;
		}
		
		/**
		 * Add logical variables from constraints from parfactor to a queue
		 */
		private LinkedList<LogicalVariable> getVariablesInConstraints() {
			Set<LogicalVariable> buffer = new HashSet<LogicalVariable>();
			for (Constraint c : simplified.constraints()) {
				buffer.addAll(c.logicalVariables());
			}
			return new LinkedList<LogicalVariable>(buffer);
		}
		
		/**
		 * When a logical variable is constrained to a single individual, 
		 * builds the substitution that replaces the logical variable by this
		 * individual.
		 */
		private Substitution getSubstitution(LogicalVariable lv) {
			Term loneGuy = lv.individualsSatisfying(unaryConstraints).iterator().next();
			Binding bind = Binding.getInstance(lv, loneGuy);
			return Substitution.getInstance(bind);
		}
		
		/**
		 * Returns the set of logical variables belonging to binary constraints
		 * that involve the specified logical variable.
		 */
		private Set<LogicalVariable> variablesInBinaryConstraintsInvolving(LogicalVariable lv) {
			Set<Constraint> binaryConstraints = new HashSet<Constraint>(simplified.constraints());
			binaryConstraints.removeAll(unaryConstraints);
			
			Set<LogicalVariable> otherVariables = new HashSet<LogicalVariable>();
			for (Constraint binary : binaryConstraints) {
				if (binary.contains(lv) && binary.firstTerm().equals(lv)) {
					otherVariables.add((LogicalVariable) binary.secondTerm());
				} else if (binary.contains(lv) && binary.secondTerm().equals(lv)) {
					otherVariables.add((LogicalVariable) binary.firstTerm());
				}
			}
			return otherVariables;
		}
		
		
		/// buiuuuuuuuu
		private Parfactor simplifyAggregation(Substitution sub) {
			
			/*
			 * When the extra variable is constrained to a single individual
			 * there is no aggregation needed.
			 */
			List<Prv> parentChild = Lists.listOf(parent(), child());
			Factor newStructure = ConstantFactor.getInstance(parentChild);
			List<BigDecimal> values = new ArrayList<BigDecimal>();
			for (Tuple<RangeElement> tuple : newStructure) {
				if (tuple.get(0).equals(tuple.get(1))) {
					values.add(BigDecimal.ONE);
				} else {
					values.add(BigDecimal.ZERO);
				}
			}

			AggregationParfactor ap = (AggregationParfactor) simplified;
			Parfactor result = new StdParfactorBuilder()
					.constraints(ap.constraintsNotOnExtra())
					.variables(parentChild).values(values).build();
			return result;
			
//			// casting aggregation parfactor
//			AggregationParfactor ap = (AggregationParfactor) simplified;
//			
//			// list with parent, child and context variables
//			List<Prv> parentChildContext = Lists.union(ap.prvs(), ap.context());
//			parentChildContext = Lists.apply(sub, parentChildContext);
//			
//			// new factor structure after simplification
//			Factor newStructure = ConstantFactor.getInstance(parentChildContext);
//			
//			// lets fill the array of factor values
//			List<BigDecimal> values = new ArrayList<BigDecimal>();
//			for (Tuple<RangeElement> tuple : newStructure) {
//				if (tuple.get(0).equals(tuple.get(1))) {
//					// correction factors
//					Prv parent = parentChildContext.get(0);
//					int rp = parent.groundSetSize(ap.constraintsNotOnExtra());
//					Prv child = parentChildContext.get(1);
//					int rc = child.groundSetSize(ap.constraintsNotOnExtra());
//					// gets the value from old factor by removing the child
//					BigDecimal value = ap.factor().getValue(tuple.remove(1));
//					BigDecimal correctedValue = MathUtils.pow(value, rp, rc);
//					values.add(correctedValue);
//				} else {
//					values.add(BigDecimal.ZERO);
//				}
//			}
//			Parfactor result = new StdParfactorBuilder()
//					.constraints(ap.constraintsNotOnExtra())
//					.variables(parentChildContext).values(values).build();
//			return result;
		}
	}
	
	
	/* ************************************************************************
	 *    Constructors
	 * ************************************************************************/
	
	/**
	 * Creates an AggParfactor using the specified builder.
	 * 
	 * @param builder A {@link AggParfactorBuilder}
	 */
	private AggParfactor(AggParfactorBuilder builder) {
		this.parent = builder.p;
		this.child = builder.c;
		this.context = builder.ctxt;
		this.factor = builder.getFactor();
		this.operator = builder.op;
		this.constraintsNotOnExtra = builder.constraintsNotOnExtra;
		this.constraintsOnExtra = builder.constraintsOnExtra;
		this.extraVar = builder.lv;
	}

	
	/* ************************************************************************
	 *    Getters
	 * ************************************************************************/

	
	@Override
	public Set<Constraint> constraints() {
		return Sets.union(constraintsNotOnExtra, constraintsOnExtra);
	}


	@Override
	public Set<Constraint> constraintsOnExtra() {
		return new HashSet<Constraint>(constraintsOnExtra);
	}


	@Override
	public Set<Constraint> constraintsNotOnExtra() {
		return new HashSet<Constraint>(constraintsNotOnExtra);
	}
	

	@Override
	public Factor factor() {
		return factor;
	}

	
	@Override
	public Set<LogicalVariable> logicalVariables() {
		List<LogicalVariable> variables = Lists.union(child.parameters(), 
				parent.parameters());
		return new HashSet<LogicalVariable>(variables);
	}

	
	/**
	 * Returns a list containing the parent PRV, the child PRV and context
	 * PRVs, in this order. The order of context PRVs will be same as defined
	 * when constructing this parfactor.
	 */
	@Override
	public List<Prv> prvs() {
		List<Prv> prvs = new ArrayList<Prv>(context.size() + 2);
		prvs.add(parent);
		prvs.add(child);
		prvs.addAll(context);
		return prvs;
	}
	

	@Override
	public Prv parent() {
		return StdPrv.getInstance(parent);
	}

	
	@Override
	public Prv child() {
		return StdPrv.getInstance(child);
	}
	

	@Override
	public List<Prv> context() {
		return Lists.listOf(context);
	}
	

	@Override
	public Operator<? extends RangeElement> operator() {
		return operator;
	}
	

	@Override
	public LogicalVariable extraVariable() {
		return StdLogicalVariable.getInstance(extraVar);
	}
	
	
	@Override
	public int size() {
		Set<LogicalVariable> vars = logicalVariables();
		vars.remove(extraVar);
		int size = 1;
		for (LogicalVariable lv : vars) {
			size = size * lv.numberOfIndividualsSatisfying(constraintsNotOnExtra);
		}
		return size;
	}
	

	@Override
	public Parfactor apply(Substitution s) {
		Set<Constraint> substitutedConstraints = Sets.apply(s, constraints());//applyToConstraints(s);
		Factor substitutedFactor = factor.apply(s);
		Prv substitutedParent = parent.apply(s);
		Prv substututedChild = child.apply(s);
		List<Prv> substitutedContext = Lists.apply(s, context);
		
		return new AggParfactorBuilder(substitutedParent, substututedChild, 
				operator).constraints(substitutedConstraints)
				.context(substitutedContext).factor(substitutedFactor).build();
	}
		
	
	@Override
	public boolean contains(Prv prv) {
		return (prv.equals(parent) || prv.equals(child) || context.contains(prv));
	}
	

	@Override
	public boolean isConstant() {
		boolean hasNoConstraints = constraints().isEmpty();
		boolean hasConstantFactor = factor.isConstant();
		return hasNoConstraints && hasConstantFactor;
	}

	
	/**
	 * Returns <code>false</code>.
	 * <p>
	 * A logical variable cannot be eliminated from 
	 * {@link AggregationParfactor}s.
	 * </p>
	 */
	@Override
	public boolean isCountable(LogicalVariable lv) {
		return false;
	}
	

	/**
	 * Returns <code>false</code>.
	 * <p>
	 * {@link AggregationParfactor}s do not contain {@link CountingFormula}s.
	 * </p>
	 */
	@Override
	public boolean isExpandable(Prv cf, Substitution s) {
		return false;
	}
	

	@Override
	public boolean isMultipliable(Parfactor other) {
		/*
		 * Uses a ParfactorVisitor to discover the type of 'other'.
		 * The algorithm to check if parfactors are multipliable is
		 * encapsulated in MultiplicationChecker.
		 */
		MultiplicationChecker parfactors = new MultiplicationChecker();
		accept(parfactors, other);
		return parfactors.areMultipliable();
	}

	
	@Override
	public boolean isSplittable(Substitution s) {
		boolean isSplittable = false;
		if (s.size() != 1) {
			isSplittable = false;
		} else {
			Binding bind = s.first();
			Split split = new Split(bind.firstTerm(), bind.secondTerm());
			isSplittable = split.isValid();
		}
		return isSplittable;
	}
	
	
	@Override
	public boolean isEliminable(Prv prv) {
		
		/*
		 * There is one condition not checked here: no other parfactor from the
		 * set has PRVs representing random variables from ground(p).
		 */
		
		// param(p(...A...)) \ {A}
		Set<LogicalVariable> parentParameters = new HashSet<LogicalVariable>(parent.parameters());
		parentParameters.remove(extraVar);
		
		// param(c(...E...)) \ {E}
		Set<LogicalVariable> childParameters = new HashSet<LogicalVariable>(child.parameters());
		List<LogicalVariable> extraVariablesInChild = child.parameters();
		extraVariablesInChild.removeAll(parent.parameters());
		
		if (extraVariablesInChild.size() == 1) {
			LogicalVariable e = extraVariablesInChild.get(0);
			childParameters.remove(e);
		}
		
		boolean oneExtraForEach = childParameters.equals(parentParameters);
		
		return isInNormalForm() && oneExtraForEach;
	}


	/**
	 * Returns <code>true</code> if this parfactor is in normal form.
	 * <p>
	 * A parfactor is in normal form if, for each inequality constraint 
	 * (X &ne; Y) &in; C we have &epsilon;<sub>X</sub><sup>C</sup>\{Y} = 
	 *  &epsilon;<sub>Y</sub><sup>C</sup>\{X}. X and Y are logical variables.
	 * </p>
	 * @return <code>true</code> if this parfactor is in normal form, 
	 * <code>false</code> otherwise
	 */
	private boolean isInNormalForm() {
		for (Constraint c : constraints()) {
			if (c.firstTerm().isVariable() && c.secondTerm().isVariable()) {
				LogicalVariable x = (LogicalVariable) c.firstTerm();
				LogicalVariable y = (LogicalVariable) c.secondTerm();
				Set<Term> ex = x.excludedSet(constraints());
				Set<Term> ey = y.excludedSet(constraints());
				ex.remove(y);
				ey.remove(x);
				if (!ex.equals(ey)) {
					return false;
				}
			}
		}
		return true;
	}
	
	
	/**
	 * Throws {@link UnsupportedOperationException}.
	 */
	@Override
	public Parfactor count(LogicalVariable lv) {
		throw new UnsupportedOperationException("Aggregation Parfactors are not countable");
	}
	

	/**
	 * Throws {@link UnsupportedOperationException}.
	 */
	@Override
	public Parfactor expand(Prv cf, Term t) {
		throw new UnsupportedOperationException("Aggregation Parfactors are not expandable");
	}

	
	@Override
	public Parfactor multiply(Parfactor other) {
		// TODO check if multiplication is valid
		Multiplier result = new Multiplier(this, other);
		return result.multiply();
	}

		
	@Override
	public Parfactor multiplicationHelper(Parfactor other) {
		/* 
		 * I know that 'other' is a StdParfactor, because 
		 * AggregationParfactor.multiply() does not call this helper method. 
		 */
		return multiply(other);
	}

	
	@Override
	public SplitResult splitOn(Substitution s) throws IllegalArgumentException {
		Splitter result = new Splitter(this, s);
		return result.split();
	}

	@Override
	public Parfactor sumOut(Prv prv) {
		// TODO check if prv is parent
		
		Eliminator eliminator = new Eliminator(this);
		Parfactor result = eliminator.eliminate();
		return result;
	}
	

	@Override
	public Distribution toStdParfactors() {
		Converter converter = new Converter(this);
		Distribution result = converter.convert();
		return result;
	}
	
	
	@Override
	public Parfactor simplifyLogicalVariables() {
		Simplifier parfactor = new Simplifier(this);
		return parfactor.simplify();
	}
	
	
	@Override
	public void accept(ParfactorVisitor visitor, Parfactor p) {
		/*
		 * As p has an unknown type, it is necessary to call its accept()
		 * method. JVM will infer its runtime type and call the appropriate
		 * method, which is one of the methods with the signature below. 
		 */
		p.accept(visitor, this);
	}
	
	
	@Override
	public void accept(ParfactorVisitor visitor, StdParfactor p) {
		/*
		 * I know this parfactor is an AggregationParfactor, and that p is a
		 * StdParfactor, thus types for visit() are defined.
		 */
		visitor.visit(this, p);
	}
	
	
	@Override
	public void accept(ParfactorVisitor visitor, AggregationParfactor p) {
		/*
		 * I know this parfactor is an AggregationParfactor, and that p is a
		 * AggregationParfactor, thus types for visit() are defined.
		 */
		visitor.visit(this, p);
	}
	
	/*
	 * Life savior:
	 * http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ207
	 */
	
	public <T extends RangeElement> T apply(Operator<T> op, RangeElement e1, RangeElement e2) {
		T t1 = op.getTypeArgument().cast(e1);
		T t2 = op.getTypeArgument().cast(e2);
		return op.applyOn(t1, t2);
	}
	
	
	public <T extends RangeElement> T apply(Operator<T> op, RangeElement e1, RangeElement e2, RangeElement e3) {
		T t1 = op.getTypeArgument().cast(e1);
		T t2 = op.getTypeArgument().cast(e2);
		T t3 = op.getTypeArgument().cast(e3);
		return op.applyOn(t1, t2, t3);
	}
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((child == null) ? 0 : child.hashCode());
		result = prime
				* result
				+ ((constraintsNotOnExtra == null) ? 0 : constraintsNotOnExtra
						.hashCode());
		result = prime
				* result
				+ ((constraintsOnExtra == null) ? 0 : constraintsOnExtra
						.hashCode());
		result = prime * result
				+ ((extraVar == null) ? 0 : extraVar.hashCode());
		result = prime * result + ((factor == null) ? 0 : factor.hashCode());
		result = prime * result
				+ ((operator == null) ? 0 : operator.hashCode());
		result = prime * result + ((parent == null) ? 0 : parent.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AggParfactor)) {
			return false;
		}
		AggParfactor other = (AggParfactor) obj;
		if (child == null) {
			if (other.child != null) {
				return false;
			}
		} else if (!child.equals(other.child)) {
			return false;
		}
		if (constraintsNotOnExtra == null) {
			if (other.constraintsNotOnExtra != null) {
				return false;
			}
		} else if (!constraintsNotOnExtra.equals(other.constraintsNotOnExtra)) {
			return false;
		}
		if (constraintsOnExtra == null) {
			if (other.constraintsOnExtra != null) {
				return false;
			}
		} else if (!constraintsOnExtra.equals(other.constraintsOnExtra)) {
			return false;
		}
		if (extraVar == null) {
			if (other.extraVar != null) {
				return false;
			}
		} else if (!extraVar.equals(other.extraVar)) {
			return false;
		}
		if (factor == null) {
			if (other.factor != null) {
				return false;
			}
		} else if (!factor.equals(other.factor)) {
			return false;
		}
		if (operator == null) {
			if (other.operator != null) {
				return false;
			}
		} else if (!operator.equals(other.operator)) {
			return false;
		}
		if (parent == null) {
			if (other.parent != null) {
				return false;
			}
		} else if (!parent.equals(other.parent)) {
			return false;
		}
		return true;
	}
	
	
	@Override
	public String toString() {
		return "\np = " + parent 
				+ ", c = " + child
				+ ", V = " + context
				+ ", C_A = " + constraintsOnExtra
				+ ", C = " + constraintsNotOnExtra 
				+ "\n" + factor;	
	}

}
