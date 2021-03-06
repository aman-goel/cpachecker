/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.value.symbolic.refiner;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Multimap;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.c.CStorageClass;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.path.PathIterator;
import org.sosy_lab.cpachecker.cpa.constraints.ConstraintsCPA;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.Constraint;
import org.sosy_lab.cpachecker.cpa.constraints.domain.ConstraintsState;
import org.sosy_lab.cpachecker.cpa.constraints.refiner.precision.ConstraintsPrecision;
import org.sosy_lab.cpachecker.cpa.constraints.refiner.precision.RefinableConstraintsPrecision;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisCPA;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState.ValueAndType;
import org.sosy_lab.cpachecker.cpa.value.symbolic.refiner.interpolant.SymbolicInterpolant;
import org.sosy_lab.cpachecker.cpa.value.symbolic.refiner.interpolant.SymbolicInterpolantManager;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.cpa.value.type.ValueToCExpressionTransformer;
import org.sosy_lab.cpachecker.cpa.value.type.ValueVisitor;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.cpachecker.util.refinement.FeasibilityChecker;
import org.sosy_lab.cpachecker.util.refinement.GenericPrefixProvider;
import org.sosy_lab.cpachecker.util.refinement.GenericRefiner;
import org.sosy_lab.cpachecker.util.refinement.InterpolationTree;
import org.sosy_lab.cpachecker.util.refinement.PathExtractor;
import org.sosy_lab.cpachecker.util.refinement.PathInterpolator;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Refiner for value analysis using symbolic values.
 */
@Options(prefix = "cpa.value.refinement")
public class SymbolicValueAnalysisRefiner
    extends GenericRefiner<ForgettingCompositeState, SymbolicInterpolant> {

  @Option(secure = true, description = "whether or not to do lazy-abstraction", name = "restart", toUppercase = true)
  private RestartStrategy restartStrategy = RestartStrategy.PIVOT;

  @Option(secure = true, description = "if this option is set to false, constraints are never kept")
  private boolean trackConstraints = true;

  @Option(
    secure = true,
    name = "pathConstraintsFile",
    description =
        "File to which path constraints should be written. If null, no path constraints are written"
  )
  @FileOption(Type.OUTPUT_FILE)
  private PathTemplate pathConstraintsOutputFile =
      PathTemplate.ofFormatString("Counterexample.%d.symbolic-trace.txt");

  private SymbolicStrongestPostOperator strongestPost;
  private Precision fullPrecision;

  public static SymbolicValueAnalysisRefiner create(final ConfigurableProgramAnalysis pCpa)
      throws InvalidConfigurationException {

    final ARGCPA argCpa =
        CPAs.retrieveCPAOrFail(pCpa, ARGCPA.class, SymbolicValueAnalysisRefiner.class);
    final ValueAnalysisCPA valueAnalysisCpa =
        CPAs.retrieveCPAOrFail(pCpa, ValueAnalysisCPA.class, SymbolicValueAnalysisRefiner.class);
    final ConstraintsCPA constraintsCpa =
        CPAs.retrieveCPAOrFail(pCpa, ConstraintsCPA.class, SymbolicValueAnalysisRefiner.class);

    final Configuration config = valueAnalysisCpa.getConfiguration();

    valueAnalysisCpa.injectRefinablePrecision();
    constraintsCpa.injectRefinablePrecision(new RefinableConstraintsPrecision(config));

    final LogManager logger = valueAnalysisCpa.getLogger();
    final CFA cfa = valueAnalysisCpa.getCFA();
    final ShutdownNotifier shutdownNotifier = valueAnalysisCpa.getShutdownNotifier();

    final Solver solver = constraintsCpa.getSolver();

    final SymbolicStrongestPostOperator strongestPostOperator =
        new ValueTransferBasedStrongestPostOperator(solver, logger, config, cfa, shutdownNotifier);

    final SymbolicFeasibilityChecker feasibilityChecker =
        new SymbolicValueAnalysisFeasibilityChecker(strongestPostOperator,
                                                    config,
                                                    logger,
                                                    cfa);


    final GenericPrefixProvider<ForgettingCompositeState> prefixProvider =
        new GenericPrefixProvider<>(
            strongestPostOperator,
            ForgettingCompositeState.getInitialState(cfa.getMachineModel()),
            logger,
            cfa,
            config,
            ValueAnalysisCPA.class,
            shutdownNotifier);

    final ElementTestingSymbolicEdgeInterpolator edgeInterpolator =
        new ElementTestingSymbolicEdgeInterpolator(feasibilityChecker,
                                        strongestPostOperator,
                                        SymbolicInterpolantManager.getInstance(),
                                        config,
                                        shutdownNotifier,
                                        cfa);

    final SymbolicPathInterpolator pathInterpolator =
        new SymbolicPathInterpolator(edgeInterpolator,
                                    feasibilityChecker,
                                    prefixProvider,
                                    config,
                                    logger,
                                    shutdownNotifier,
                                    cfa);

    return new SymbolicValueAnalysisRefiner(
        argCpa,
        cfa,
        feasibilityChecker,
        strongestPostOperator,
        pathInterpolator,
        new PathExtractor(logger, config),
        config,
        logger);
  }

  public SymbolicValueAnalysisRefiner(
      final ARGCPA pCpa,
      final CFA pCfa,
      final FeasibilityChecker<ForgettingCompositeState> pFeasibilityChecker,
      final SymbolicStrongestPostOperator pStrongestPostOperator,
      final PathInterpolator<SymbolicInterpolant> pInterpolator,
      final PathExtractor pPathExtractor,
      final Configuration pConfig,
      final LogManager pLogger)
      throws InvalidConfigurationException {

    super(pCpa,
          pFeasibilityChecker,
          pInterpolator,
          SymbolicInterpolantManager.getInstance(),
          pPathExtractor,
          pConfig,
          pLogger);

    pConfig.inject(this);

    strongestPost = pStrongestPostOperator;
    fullPrecision =
        VariableTrackingPrecision.createStaticPrecision(
            pConfig, pCfa.getVarClassification(), ValueAnalysisCPA.class);

    if (pathConstraintsOutputFile != null && !pCfa.getLanguage().equals(Language.C)) {
      throw new InvalidConfigurationException(
          "At the moment, writing path constraints is only supported for C");
    }
  }

  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    CounterexampleInfo cex = performRefinementAndGetCex(pReached);

    if (cex.isSpurious()) {
      return true;
    } else if (pathConstraintsOutputFile != null) {
      addSymbolicInformationToCex(cex, pathConstraintsOutputFile);
    }
    return false;
  }

  private void addSymbolicInformationToCex(CounterexampleInfo pCex, PathTemplate pOutputFile)
      throws CPAException, InterruptedException {
    ARGPath tp = pCex.getTargetPath();
    PathIterator fullPath = tp.fullPathIterator();
    ARGState first = tp.getFirstState();
    ValueAnalysisState firstValue =
        checkNotNull(AbstractStates.extractStateByType(first, ValueAnalysisState.class));
    ConstraintsState firstConstraints =
        checkNotNull(AbstractStates.extractStateByType(first, ConstraintsState.class));
    ForgettingCompositeState currentState =
        new ForgettingCompositeState(firstValue, firstConstraints);
    Deque<ForgettingCompositeState> callstack = new ArrayDeque<>();

    StringBuilder symbolicInfo = new StringBuilder();
    ValueVisitor<CExpression> toCExpressionVisitor;

    while (fullPath.hasNext()) {
      CFAEdge currentEdge = fullPath.getOutgoingEdge();
      Collection<AExpressionStatement> assumptions = new ArrayList<>(1);

      Optional<ForgettingCompositeState> maybeNext =
          strongestPost.step(currentState, currentEdge, fullPrecision, callstack, tp);

      if (!maybeNext.isPresent()) {
        throw new IllegalStateException("Counterexample said to be feasible but spurious");
      } else {
        ForgettingCompositeState nextState = maybeNext.get();
        ValueAnalysisState nextVals = nextState.getValueState();
        ValueAnalysisState oldVals = currentState.getValueState();

        Set<Entry<MemoryLocation, ValueAndType>> newAssignees =
            new HashSet<>(nextVals.getConstants());
        newAssignees.removeAll(oldVals.getConstants());
        for (Entry<MemoryLocation, ValueAndType> e : newAssignees) {
          Value v = e.getValue().getValue();
          CType t = (CType) e.getValue().getType();
          CExpressionStatement exp;
          toCExpressionVisitor = new ValueToCExpressionTransformer(t);
          CExpression rhs = v.accept(toCExpressionVisitor);
          CExpression lhs = getCorrespondingIdExpression(e.getKey(), t);
          CExpression assignment =
              new CBinaryExpression(FileLocation.DUMMY, t, t, lhs, rhs, BinaryOperator.EQUALS);
          exp = new CExpressionStatement(FileLocation.DUMMY, assignment);
          assumptions.add(exp);
        }

        ConstraintsState nextConstraints = nextState.getConstraintsState();
        ConstraintsState oldConstraints = currentState.getConstraintsState();

        ConstraintsState newConstraints = nextConstraints.copyOf();
        newConstraints.removeAll(oldConstraints);
        for (Constraint c : newConstraints) {
          toCExpressionVisitor = new ValueToCExpressionTransformer((CType) c.getType());
          CExpressionStatement exp =
              new CExpressionStatement(FileLocation.DUMMY, c.accept(toCExpressionVisitor));
          assumptions.add(exp);
        }

        CFAEdgeWithAssumptions edgeWithAssumption =
            new CFAEdgeWithAssumptions(currentEdge, assumptions, "");

        symbolicInfo.append(edgeWithAssumption.getCFAEdge().toString());
        symbolicInfo.append(System.lineSeparator());
        String cCode = edgeWithAssumption.prettyPrintCode(1);
        if (!cCode.isEmpty()) {
          symbolicInfo.append(edgeWithAssumption.prettyPrintCode(1));
        }

        currentState = nextState;
      }

      fullPath.advance();
    }

    pCex.addFurtherInformation(symbolicInfo, pOutputFile);
    tp.getLastState().addCounterexampleInformation(pCex);
  }

  private CIdExpression getCorrespondingIdExpression(MemoryLocation pMemLoc, CType pType) {
    String funcName = pMemLoc.getFunctionName();
    String varName = pMemLoc.getIdentifier();
    boolean isGlobal = funcName.isEmpty();
    CSimpleDeclaration idDeclaration =
        new CVariableDeclaration(
            FileLocation.DUMMY,
            isGlobal,
            CStorageClass.AUTO,
            pType,
            varName,
            varName,
            varName,
            null);
    CIdExpression idExpression =
        new CIdExpression(FileLocation.DUMMY, pType, varName, idDeclaration);
    return idExpression;
  }

  @Override
  protected void refineUsingInterpolants(
      final ARGReachedSet pReached,
      final InterpolationTree<ForgettingCompositeState, SymbolicInterpolant> pInterpolants)
      throws InterruptedException {
    final Collection<ARGState> roots = pInterpolants.obtainRefinementRoots(restartStrategy);

    for (ARGState r : roots) {
      Multimap<CFANode, MemoryLocation> valuePrecInc = pInterpolants.extractPrecisionIncrement(r);
      ConstraintsPrecision.Increment constrPrecInc =
          getConstraintsIncrement(r, pInterpolants);

      ARGTreePrecisionUpdater.updateARGTree(pReached, r, valuePrecInc, constrPrecInc);
    }
  }

  private ConstraintsPrecision.Increment getConstraintsIncrement(
      final ARGState pRefinementRoot,
      final InterpolationTree<ForgettingCompositeState, SymbolicInterpolant> pTree
  ) {
    ConstraintsPrecision.Increment.Builder increment =
        ConstraintsPrecision.Increment.builder();

    if (trackConstraints) {
      Deque<ARGState> todo =
          new ArrayDeque<>(Collections.singleton(pTree.getPredecessor(pRefinementRoot)));

      while (!todo.isEmpty()) {
        final ARGState currentState = todo.removeFirst();

        if (!currentState.isTarget()) {
          SymbolicInterpolant itp = pTree.getInterpolantForState(currentState);

          if (itp != null && !itp.isTrivial()) {
            for (Constraint c : itp.getConstraints()) {
              increment.locallyTracked(AbstractStates.extractLocation(currentState), c);
            }
          }
        }

        Collection<ARGState> successors = pTree.getSuccessors(currentState);
        todo.addAll(successors);
      }
    }

    return increment.build();
  }

  @Override
  protected void printAdditionalStatistics(
      PrintStream out, Result pResult, UnmodifiableReachedSet pReached) {
    // DO NOTHING for now
  }
}
