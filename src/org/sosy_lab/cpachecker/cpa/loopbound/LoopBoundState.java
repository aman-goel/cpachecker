/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.loopbound;

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.LoopIterationReportingState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AvoidanceReportingState;
import org.sosy_lab.cpachecker.cpa.loopbound.LoopIterationState.DeterminedLoopIterationState;
import org.sosy_lab.cpachecker.cpa.loopbound.LoopIterationState.UndeterminedLoopIterationState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.assumptions.PreventingHeuristic;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

public class LoopBoundState
    implements AbstractState, Partitionable, AvoidanceReportingState, LoopIterationReportingState {

  private final LoopStack loopStack;

  private final boolean stopIt;

  private int hashCache = 0;

  public LoopBoundState() {
    this(new LoopStack(UndeterminedLoopIterationState.newState()), false);
  }

  private LoopBoundState(LoopStack pLoopStack, boolean pStopIt) {
    this.loopStack = Objects.requireNonNull(pLoopStack);
    Preconditions.checkArgument(!pLoopStack.isEmpty(), "Always initialize the stack with an UndeterminedLoopIterationState");
    Preconditions.checkArgument(
        (pLoopStack.getSize() == 1 && !pLoopStack.peek().isEntryKnown())
        || (pLoopStack.getSize() > 1 && pLoopStack.peek().isEntryKnown()),
        "The deepest element in the stack must be an UndeterminedLoopIterationState, and all other elements must be determined.");
    this.stopIt = pStopIt;
  }

  public LoopBoundState exit(Loop pOldLoop) throws CPATransferException {
    assert !loopStack.isEmpty() : "Visiting loop head without entering the loop. Explicitly use an UndeterminedLoopIterationState if you cannot determine the loop entry.";
    LoopIterationState loopIterationState = loopStack.peek();
    if (loopIterationState.isEntryKnown()) {
      if (!pOldLoop.equals(loopIterationState.getLoopEntry().getLoop())) {
        throw new CPATransferException("Unexpected exit from loop " + pOldLoop + " when loop stack is " + this);
      }
      return new LoopBoundState(loopStack.pop(), stopIt);
    }
    return this;
  }

  public LoopBoundState enter(LoopEntry pLoopEntry) {
    return new LoopBoundState(
        loopStack.push(DeterminedLoopIterationState.newState(pLoopEntry)),
        stopIt);
  }

  public LoopBoundState visitLoopHead(LoopEntry pLoopEntry) {
    assert !loopStack.isEmpty() : "Visiting loop head without entering the loop. Explicitly use an UndeterminedLoopIterationState if you cannot determine the loop entry.";
    if (isLoopCounterAbstracted()) {
      return this;
    }
    LoopIterationState loopIterationState = loopStack.peek();
    LoopIterationState newLoopIterationState = loopIterationState.visitLoopHead(pLoopEntry);
    if (newLoopIterationState != loopIterationState) {
      return new LoopBoundState(
          loopStack.pop().push(newLoopIterationState),
          stopIt);
    }
    return this;
  }

  public LoopBoundState setStop(boolean pStop) {
    if (stopIt == pStop) {
      return this;
    }
    return new LoopBoundState(loopStack, pStop);
  }

  public boolean isLoopCounterAbstracted() {
    return loopStack.peek().isLoopCounterAbstracted();
  }

  @Override
  public Object getPartitionKey() {
    return this;
  }

  @Override
  public boolean mustDumpAssumptionForAvoidance() {
    return stopIt;
  }

  @Override
  public String toString() {
    return loopStack.peek().toString() + ", stack depth " + getDepth() + " [" + Integer.toHexString(System.identityHashCode(loopStack.pop())) + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LoopBoundState)) {
      return false;
    }

    LoopBoundState other = (LoopBoundState)obj;
    return this.stopIt == other.stopIt
        && this.loopStack == other.loopStack;
  }

  @Override
  public int hashCode() {
    if (hashCache == 0) {
      hashCache = Objects.hash(stopIt, loopStack);
    }
    return hashCache;
  }

  @Override
  public BooleanFormula getReasonFormula(FormulaManagerView manager) {
    BooleanFormulaManager bfmgr = manager.getBooleanFormulaManager();
    BooleanFormula reasonFormula = bfmgr.makeTrue();
    if (stopIt) {
      reasonFormula = bfmgr.and(reasonFormula, PreventingHeuristic.LOOPITERATIONS.getFormula(manager, getDeepestIteration()));
    }
    return reasonFormula;
  }

  @Override
  public int getIteration(Loop pLoop) {
    for (LoopIterationState loopIterationState : loopStack) {
      if (!loopIterationState.isEntryKnown()) {
        return loopIterationState.getLoopIterationCount(pLoop);
      }
      if (loopIterationState.getLoopEntry().getLoop().equals(pLoop)) {
        return loopIterationState.getLoopIterationCount(pLoop);
      }
    }
    return 0;
  }

  @Override
  public int getDeepestIteration() {
    int deepestIteration = 0;
    for (LoopIterationState loopIterationState : loopStack) {
      deepestIteration = Math.max(deepestIteration, loopIterationState.getMaxIterationCount());
    }
    return deepestIteration;
  }

  @Override
  public Set<Loop> getDeepestIterationLoops() {
    if (loopStack.isEmpty()) {
      return Collections.emptySet();
    }
    int deepestIteration = getDeepestIteration();
    return FluentIterable.from(loopStack)
        .filter(l -> l.getMaxIterationCount() == deepestIteration)
        .transformAndConcat(l -> l.getDeepestIterationLoops())
        .toSet();
  }

  public int getDepth() {
    // Subtract 1 to account for the "undetermined" element at the bottom of the stack
    return loopStack.getSize() - 1;
  }

  boolean deepEquals(LoopBoundState pOther) {
    // Quick checks for common case (inequality) first
    if (this.stopIt != pOther.stopIt) {
      return false;
    }
    // Hash code is cached, so this is also quick
    if (loopStack.hashCode() != pOther.loopStack.hashCode()) {
      return false;
    }
    return loopStack.equals(pOther.loopStack);
  }

  public LoopBoundState enforceAbstraction(int pLoopIterationsBeforeAbstraction) {
    if (loopStack.isEmpty()) {
      return this;
    }
    LoopIterationState currentLoopIterationState = loopStack.peek();
    LoopIterationState newLoopIterationState = currentLoopIterationState.enforceAbstraction(pLoopIterationsBeforeAbstraction);
    if (currentLoopIterationState == newLoopIterationState) {
      return this;
    }
    return new LoopBoundState(loopStack.pop().push(newLoopIterationState), stopIt);
  }
}