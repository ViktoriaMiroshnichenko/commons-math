/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.math4.ga;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.math4.ga.chromosome.ChromosomePair;
import org.apache.commons.math4.ga.crossover.CrossoverPolicy;
import org.apache.commons.math4.ga.internal.exception.GeneticIllegalArgumentException;
import org.apache.commons.math4.ga.listener.ConvergenceListener;
import org.apache.commons.math4.ga.mutation.MutationPolicy;
import org.apache.commons.math4.ga.population.Population;
import org.apache.commons.math4.ga.selection.SelectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a genetic algorithm. All factors that govern the operation
 * of the algorithm can be configured for a specific problem.
 *
 * @param <P> phenotype of chromosome
 * @since 4.0
 */
public class GeneticAlgorithm<P> extends AbstractGeneticAlgorithm<P> {

    /** instance of logger. **/
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneticAlgorithm.class);
    /** crossover rate string. **/
    private static final String CROSSOVER_RATE = "CROSSOVER_RATE";
    /** mutation rate string. **/
    private static final String MUTATION_RATE = "MUTATION_RATE";
    /** the rate of crossover for the algorithm. */
    private final double crossoverRate;
    /** the rate of mutation for the algorithm. */
    private final double mutationRate;

    /**
     * Create a new genetic algorithm.
     * @param crossoverPolicy      The {@link CrossoverPolicy}
     * @param crossoverRate        The crossover rate as a percentage (0-1
     *                             inclusive)
     * @param mutationPolicy       The {@link MutationPolicy}
     * @param mutationRate         The mutation rate as a percentage (0-1 inclusive)
     * @param selectionPolicy      The {@link SelectionPolicy}
     * @param elitismRate          The rate of elitism
     * @param convergenceListeners An optional collection of
     *                             {@link ConvergenceListener} with variable arity
     */
    @SafeVarargs
    public GeneticAlgorithm(final CrossoverPolicy<P> crossoverPolicy,
            final double crossoverRate,
            final MutationPolicy<P> mutationPolicy,
            final double mutationRate,
            final SelectionPolicy<P> selectionPolicy,
            final double elitismRate,
            ConvergenceListener<P>... convergenceListeners) {
        super(crossoverPolicy, mutationPolicy, selectionPolicy, elitismRate, convergenceListeners);

        checkValidity(crossoverRate, mutationRate);
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
    }

    private void checkValidity(final double crossoverRateInput, final double inputMutationRate) {
        if (crossoverRateInput < 0 || crossoverRateInput > 1) {
            throw new GeneticIllegalArgumentException(GeneticIllegalArgumentException.OUT_OF_RANGE, crossoverRateInput,
                    CROSSOVER_RATE, 0, 1);
        }
        if (inputMutationRate < 0 || inputMutationRate > 1) {
            throw new GeneticIllegalArgumentException(GeneticIllegalArgumentException.OUT_OF_RANGE, inputMutationRate,
                    MUTATION_RATE, 0, 1);
        }
    }

    /**
     * Evolve the given population into the next generation.
     * <ol>
     * <li>Get nextGeneration population to fill from <code>current</code>
     * generation, using its nextGeneration method</li>
     * <li>Loop until new generation is filled:
     * <ul>
     * <li>Apply configured SelectionPolicy to select a pair of parents from
     * <code>current</code></li>
     * <li>With probability = {@link #getCrossoverRate()}, apply configured
     * {@link CrossoverPolicy} to parents</li>
     * <li>With probability = {@link #getMutationRate()}, apply configured
     * {@link MutationPolicy} to each of the offspring</li>
     * <li>Add offspring individually to nextGeneration, space permitting</li>
     * </ul>
     * </li>
     * <li>Return nextGeneration</li>
     * </ol>
     *
     * @param current the current population.
     * @return the population for the next generation.
     */
    @Override
    protected Population<P> nextGeneration(final Population<P> current, ExecutorService executorService) {

        LOGGER.debug("Reproducing next generation.");
        final Population<P> nextGeneration = current.nextGeneration(getElitismRate());
        List<Future<ChromosomePair<P>>> chromosomePairs = new ArrayList<>();

        int maxOffspringCount = nextGeneration.getPopulationLimit() - nextGeneration.getPopulationSize();

        for (int i = maxOffspringCount / 2; i > 0; i--) {

            chromosomePairs.add(executorService.submit(() -> {
                // select parent chromosomes
                ChromosomePair<P> pair = getSelectionPolicy().select(current);
                LOGGER.debug("Selected Chromosomes: " + System.lineSeparator() + pair.toString());

                // apply crossover policy to create two offspring
                pair = getCrossoverPolicy().crossover(pair.getFirst(), pair.getSecond(), crossoverRate);
                LOGGER.debug("Offsprings after Crossover: " + System.lineSeparator() + pair.toString());

                // apply mutation policy to the chromosomes
                pair = new ChromosomePair<>(getMutationPolicy().mutate(pair.getFirst(), mutationRate),
                        getMutationPolicy().mutate(pair.getSecond(), mutationRate));
                LOGGER.debug("Offsprings after Mutation: " + System.lineSeparator() + pair.toString());

                return pair;
            }));
        }

        try {
            for (Future<ChromosomePair<P>> chromosomePair : chromosomePairs) {
                ChromosomePair<P> pair = chromosomePair.get();
                nextGeneration.addChromosome(pair.getFirst());
                nextGeneration.addChromosome(pair.getSecond());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new GeneticIllegalArgumentException(e);
        }
        LOGGER.debug("New Generation :" + System.lineSeparator() + nextGeneration.toString());

        return nextGeneration;
    }

    /**
     * Returns the crossover rate.
     * @return crossover rate
     */
    public double getCrossoverRate() {
        return crossoverRate;
    }

    /**
     * Returns the mutation rate.
     * @return mutation rate
     */
    public double getMutationRate() {
        return mutationRate;
    }

}