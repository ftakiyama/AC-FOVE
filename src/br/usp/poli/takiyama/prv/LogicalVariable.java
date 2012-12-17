package br.usp.poli.takiyama.prv;

import java.util.ArrayList;
import java.util.Set;

import br.usp.poli.takiyama.common.Constraint;

/**
 * A logical variable is a word starting with an upper-case letter or the 
 * underscore. [Poole, 2010]
 * @author ftakiyama
 *
 */
public final class LogicalVariable implements Term {
	
	private final String name;
	private final Population population;
	
	/**
	 * Constructor. Creates a logical variable.
	 * @param name The name of the logical variable. The name must start with
	 * an upper case letter or underscore ("_").
	 * @param individuals The individuals that constitute the population of
	 * this logical variable
	 * @throws IllegalArgumentException If the name requirements are not met.
	 */
	public LogicalVariable(String name, ArrayList<Constant> individuals) throws IllegalArgumentException {
		// The value of a logical variable is its name
		this.name = new String(name);
		
		// Population should be ordered.
		this.population = new Population(individuals);
		
		// Checks if the name of the variable is valid
		if (!name.startsWith("_") && !Character.isUpperCase(name.charAt(0))) {
			throw new IllegalArgumentException("Exception while creating " +
					"Logical Variable: '" + name + "' must start with " +
					"uppercase letter or underscore.");
		}
	}
	
	/**
	 * Constructor. Creates a logical variable.
	 * @param name The name of the logical variable
	 * @param population The population of the logical variable. The new variable
	 * will have a copy of the population specified. 
	 * @throws IllegalArgumentException If the name of the variable does not
	 * start with a upper case letter or underscore.
	 */
	private LogicalVariable(String name, Population population) throws IllegalArgumentException {
		this.name = new String(name);
		this.population = Population.copyOf(population);
		if (!name.startsWith("_") && !Character.isUpperCase(name.charAt(0))) {
			throw new IllegalArgumentException("Exception while creating " +
					"Logical Variable: '" + name + "' must start with " +
					"uppercase letter or underscore.");
		}
	}
	
	/**
	 * Returns the name of this logical variable.
	 * @return The name of this logical variable.
	 */
	public String getValue() {
		return new String(name);
	}
	
	/**
	 * Returns a copy of the population of this logical variable.
	 * @return A copy of the population of this logical variable.
	 */
	public Population getPopulation() {
		return Population.copyOf(population);
	}
	
	/**
	 * Not implemented yet
	 * @param constraints
	 * @return
	 */
	// TODO: put constraint processing
	public Population getIndividualsSatisfying(Set<Constraint> constraints) {
		return Population.copyOf(population);
	}
	
	/**
	 * Renames this logical variable. 
	 * A new instance of LogicalVariable will be created.
	 * @param newName The new name
	 */
	LogicalVariable rename(String newName) {
		return new LogicalVariable(newName, this.population);
	}
	
	@Override
	public boolean isLogicalVariable() {
		return true;
	}
	
	@Override
	public boolean isConstant() {
		return false;
	}
	
	@Override
	public String toString() {
		return this.name;
	}
	
	@Override
	public boolean equals(Object other) {
		// Tests if both refer to the same object
		if (this == other)
	    	return true;
		// Tests if the Object is an instance of this class
	    if (!(other instanceof LogicalVariable))
	    	return false;
	    // Tests if both have the same attributes
	    LogicalVariable targetObject = (LogicalVariable) other;
	    return this.name.equals(targetObject.name);	    		
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
}