/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.core.algorithm.pdr.ctigar;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

/**
 * A straightforward implementation of a frame set using the delta encoding as described in
 * "Efficient Implementation of Property Directed Reachability" by Niklas Een, Alan Mishchenko and
 * Robert Brayton.
 */
public class DeltaEncodedFrameSet implements FrameSet {

  private static final int INITIAL_CAPACITY = 10;

  private final List<Set<BooleanFormula>> frames;
  private final Solver solver;
  private final FormulaManagerView fmgr;
  private final BooleanFormulaManager bfmgr;
  private final TransitionSystem transition;
  private final FrameSetStatistics stats;

  private int currentFrontierLevel;

  /**
   * Creates a new frame set. It has a single frame at level 0 which contains the initial states
   * given by the transition system. The frontier level is 0.
   *
   * @param pSolver The solver used for propagation.
   * @param pFmgr A formula manager used for instantiating formulas.
   * @param pTransition The global transition system providing the initial condition, the safety
   *     property and the transition formula.
   * @param pCompStats The statistics delegator that this frame set should be registered at. It
   *     takes care of printing this frame set's statistics.
   */
  public DeltaEncodedFrameSet(
      Solver pSolver,
      FormulaManagerView pFmgr,
      TransitionSystem pTransition,
      StatisticsDelegator pCompStats) {
    stats = new FrameSetStatistics();
    Objects.requireNonNull(pCompStats).register(stats);

    solver = Objects.requireNonNull(pSolver);
    fmgr = Objects.requireNonNull(pFmgr);
    bfmgr = Objects.requireNonNull(pFmgr.getBooleanFormulaManager());
    transition = Objects.requireNonNull(pTransition);
    currentFrontierLevel = 0;
    frames = new ArrayList<>(INITIAL_CAPACITY);

    // Add frame F_0 with the initial condition.
    addNewFrameWith(PDRUtils.asUnprimed(transition.getInitialCondition(), fmgr, transition));
  }

  private void addNewFrameWith(BooleanFormula pFormula) {
    Set<BooleanFormula> newFrame = newFrame();
    newFrame.add(pFormula);
    frames.add(newFrame);
    stats.numberFrames++;
  }

  private static Set<BooleanFormula> newFrame() {
    return new HashSet<>();
  }

  @Override
  public void openNextFrame() {
    addNewFrameWith(PDRUtils.asUnprimed(transition.getSafetyProperty(), fmgr, transition));
    currentFrontierLevel++;
  }

  @Override
  public int getFrontierLevel() {
    return currentFrontierLevel;
  }

  @Override
  public Set<BooleanFormula> getStates(int pLevel) {
    Preconditions.checkPositionIndex(pLevel, currentFrontierLevel);
    if (pLevel == 0) { // States in F_0 are only the initial states
      return frames.get(0);
    }

    /*
     *  Start with states at the specified level
     *  and add all states of higher levels afterwards (delta encoding).
     *  See functionality of blockStates().
     */
    return frames.stream().skip(pLevel).flatMap(frame -> frame.stream()).collect(toImmutableSet());
  }

  @Override
  public void blockStates(BooleanFormula pStates, int pMaxLevel) {
    Preconditions.checkPositionIndex(pMaxLevel, currentFrontierLevel);

    // Only need to add to highest level (delta encoding). See functionality of getStates().
    frames.get(pMaxLevel).add(bfmgr.not(pStates));
    stats.numberClauses++;
  }

  @Override
  public boolean propagate(ShutdownNotifier pShutdownNotifier)
      throws SolverException, InterruptedException {
    stats.propagationTimer.start();
    try {

      // For all levels i and all clauses c in F_i, check if UNSAT[F_i & T & not(c)'].
      // Yes : c is inductive relative to F_i and can therefore be moved to F_(i+1).
      for (int level = 1; level < currentFrontierLevel; ++level) {
        Set<BooleanFormula> currentFrame = frames.get(level);

        try (ProverEnvironment prover = solver.newProverEnvironment()) {

          // Push transition relation and clauses in frame.
          prover.push(transition.getTransitionRelationFormula());
          for (BooleanFormula clauseAtCurrentLevel : getStates(level)) {
            prover.push(clauseAtCurrentLevel);
          }

          // Push not(clause)' and try to propagate.
          Iterator<BooleanFormula> it = currentFrame.iterator();
          while (it.hasNext()) {
            BooleanFormula clauseToPropagate = it.next();
            prover.push(PDRUtils.asPrimed(bfmgr.not(clauseToPropagate), fmgr, transition));

            if (PDRUtils.isUnsat(prover, stats.propagationSolverTimer)) {

              // Move clause to next frame.
              it.remove();
              frames.get(level + 1).add(clauseToPropagate);
            }
            prover.pop();
            pShutdownNotifier.shutdownIfNecessary();
          }
        }

        // Remove all clauses in the current frame that are subsumed by any clause in a higher frame.
        stats.subsumptionTimer.start();
        try {
          for (BooleanFormula clauseAtNextLevel : getStates(level + 1)) {
            Iterator<BooleanFormula> currentLevelClauseIterator = currentFrame.iterator();
            while (currentLevelClauseIterator.hasNext()) {
              BooleanFormula c = currentLevelClauseIterator.next();
              if (subsumes(clauseAtNextLevel, c)) {
                currentLevelClauseIterator.remove();
                pShutdownNotifier.shutdownIfNecessary();
              }
            }
          }
        } finally {
          stats.subsumptionTimer.stop();
        }

        // Early termination if current frame became empty.
        if (currentFrame.isEmpty()) {
          return true;
        }
      }

      // Normal termination if no frame became empty.
      return false;
    } finally {
      stats.propagationTimer.stop();
    }
  }

  /** Checks if pF1 => pF2 is valid / pF1 & not(pF2) is unsat. */
  private boolean subsumes(BooleanFormula pF1, BooleanFormula pF2)
      throws SolverException, InterruptedException {
    BooleanFormula implicationAsUnsat = bfmgr.not(bfmgr.implication(pF1, pF2));
    boolean subsumes = solver.isUnsat(implicationAsUnsat);
    if (subsumes) {
      stats.numberSubsumptions++;
      stats.numberClauses--;
    }
    return subsumes;
  }

  /**
   * Creates a formatted String representation of all individual frames including all their clauses.
   * Be aware that this String can be very large.
   */
  @Override
  public String toString() {
    StringBuilder stringRepresentation = new StringBuilder("Frame set : ");

    for (int level = 0; level <= currentFrontierLevel; ++level) {
      stringRepresentation.append("\n  Level ").append(level).append("\n :");
      Set<BooleanFormula> states = getStates(level);
      for (BooleanFormula state : states) {
        stringRepresentation.append("     ").append(state).append("\n");
      }
    }
    return stringRepresentation.toString();
  }

  private static class FrameSetStatistics implements Statistics {

    private int numberFrames = 0;
    private int numberClauses = 0;
    private int numberSubsumptions = 0;
    private final Timer propagationTimer = new Timer();
    private final Timer propagationSolverTimer = new Timer();
    private final Timer subsumptionTimer = new Timer();

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
      pOut.println("Number of frames:                        " + numberFrames);
      pOut.println("Final number of clauses in all frames:   " + numberClauses);
      pOut.println("Number of subsumed clauses during run:   " + numberSubsumptions);
      pOut.println(
          "Total number of clauses during run:      "
              + String.valueOf(numberClauses + numberSubsumptions));
      if (propagationTimer.getNumberOfIntervals() > 0) {
        pOut.println("Total time for propagation:          " + propagationTimer);
        pOut.println(
            "  Average time for propagation:      "
                + propagationTimer.getAvgTime().formatAs(TimeUnit.SECONDS));
      }
      if (propagationSolverTimer.getNumberOfIntervals() > 0) {
        pOut.println("Time spent in solver:                " + propagationSolverTimer);
      }
      if (subsumptionTimer.getNumberOfIntervals() > 0) {
        pOut.println("Total time for subsumption:          " + subsumptionTimer);
        pOut.println(
            "  Average time for subsumption:      "
                + subsumptionTimer.getAvgTime().formatAs(TimeUnit.SECONDS));
      }
    }

    @Override
    public @Nullable String getName() {
      return "Frame set";
    }
  }
}
