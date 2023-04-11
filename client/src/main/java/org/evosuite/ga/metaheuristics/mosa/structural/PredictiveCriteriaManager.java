/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */

package org.evosuite.ga.metaheuristics.mosa.structural;

import org.evosuite.Properties;
import org.evosuite.coverage.branch.BranchCoverageTestFitness;
import org.evosuite.coverage.method.MethodCoverageTestFitness;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PredictiveCriteriaManager extends MultiCriteriaManager {

    private static final Logger logger = LoggerFactory.getLogger(PredictiveCriteriaManager.class);

    /** Current methods in the search */
    private final Set<MethodCoverageTestFitness> methods = new HashSet<>();

    private final Set<MethodCoverageTestFitness> nonBuggyMethods = new HashSet<>();

    private Set<TestFitnessFunction> nonBuggyGoals;

    /** Branch(less) coverage maps of non-buggy goals */
    private final Map<Integer, TestFitnessFunction> nBBranchCoverageTrueMap = new LinkedHashMap<>();
    private final Map<Integer, TestFitnessFunction> nBBranchCoverageFalseMap = new LinkedHashMap<>();
    private final Map<String, TestFitnessFunction> nBBranchlessMethodCoverageMap = new LinkedHashMap<>();

    /**
     * Creates a new {@code MultiCriteriaManager} with the given list of targets. The targets are
     * encoded as fitness functions, which are expected to be minimization functions.
     *
     * @param targets The targets to cover encoded as minimization functions
     */
    public PredictiveCriteriaManager(List<TestFitnessFunction> targets) {
        super(targets);

        nonBuggyGoals = new HashSet<>(targets.size());

        // initialize uncovered goals and find nonBuggyGoals
        for (TestFitnessFunction ff : targets) {
            if (ff instanceof BranchCoverageTestFitness) {
                if (((BranchCoverageTestFitness) ff).isBuggy()) {
                    getUncoveredGoals().add(ff);
                } else {
                    nonBuggyGoals.add(ff);
                }
            } else if (ff instanceof MethodCoverageTestFitness) {
                if (((MethodCoverageTestFitness) ff).isBuggy()) {
                    getUncoveredGoals().add(ff);
                } else {
                    nonBuggyGoals.add(ff);
                }
            } else {
                getUncoveredGoals().add(ff);
            }
        }

        LoggingUtils.getEvoLogger().info("* Total Number of Buggy Goals: " + getUncoveredGoals().size());
        LoggingUtils.getEvoLogger().info("* Total Number of Non-Buggy Goals: " + nonBuggyGoals.size());

        // initialize the dependency graph among branches
        this.graph = getControlDependencies4Branches();

        // initialize methods and non-buggy methods
        initMethods(targets);

        // initialize the dependency graph between branches and other coverage targets (e.g., statements)
        // let's derive the dependency graph between branches and other coverage targets (e.g., statements)
        for (org.evosuite.Properties.Criterion criterion : org.evosuite.Properties.CRITERION){
            switch (criterion){
                case BRANCH:
                    break; // branches have been handled by getControlDepencies4Branches
                case EXCEPTION:
                    break; // exception coverage is handled by calculateFitness
                case LINE:
                    addDependencies4Line();
                    break;
                case STATEMENT:
                    addDependencies4Statement();
                    break;
                case WEAKMUTATION:
                    addDependencies4WeakMutation();
                    break;
                case STRONGMUTATION:
                    addDependencies4StrongMutation();
                    break;
                case METHOD:
                    addDependencies4Methods();
                    break;
                case INPUT:
                    addDependencies4Input();
                    break;
                case OUTPUT:
                    addDependencies4Output();
                    break;
                case TRYCATCH:
                    addDependencies4TryCatch();
                    break;
                case METHODNOEXCEPTION:
                    addDependencies4MethodsNoException();
                    break;
                case CBRANCH:
                    addDependencies4CBranch();
                    break;
                default:
                    LoggingUtils.getEvoLogger().error("The criterion {} is not currently supported in PreMOSA", criterion.name());
            }
        }

        // initialize current goals
        for (TestFitnessFunction ff : graph.getRootBranches()) {
            if (((BranchCoverageTestFitness) ff).isBuggy()) {
                this.currentGoals.add(ff);
            }
        }

        if (Properties.BALANCE_TEST_COV) {
            // Calculate number of independent paths leading up from each target (goal)
            calculateIndependentPaths(targets);
        }
    }

    private void initMethods(List<TestFitnessFunction> fitnessFunctions) {
        for (TestFitnessFunction ff : fitnessFunctions) {
            if (ff instanceof MethodCoverageTestFitness) {
                if (((MethodCoverageTestFitness) ff).isBuggy()) {
                    this.methods.add((MethodCoverageTestFitness) ff);
                } else {
                    this.nonBuggyMethods.add((MethodCoverageTestFitness) ff);
                }
            }
        }
    }

    public void updateCurrentGoals() {
        for (TestFitnessFunction ff : graph.getRootBranches()) {
            if (!((BranchCoverageTestFitness) ff).isBuggy()) {
                this.currentGoals.add(ff);
            }
        }
    }

    public void updateUncoveredGoals() {
        this.getUncoveredGoals().addAll(this.nonBuggyGoals);
    }

    public void updateMethods() {
        this.methods.addAll(this.nonBuggyMethods);
    }

    public void updateBranchCoverageMaps() {
        branchCoverageTrueMap.putAll(nBBranchCoverageTrueMap);
        branchCoverageFalseMap.putAll(nBBranchCoverageFalseMap);
        branchlessMethodCoverageMap.putAll(nBBranchlessMethodCoverageMap);
    }

}
