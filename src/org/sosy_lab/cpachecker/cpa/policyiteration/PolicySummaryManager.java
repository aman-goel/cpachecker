/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.policyiteration;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.summary.SummaryManager;
import org.sosy_lab.cpachecker.cpa.summary.blocks.Block;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Formula;

/**
 * Summaries for LPI
 */
public class PolicySummaryManager implements SummaryManager {
  private final PathFormulaManager pfmgr;
  private final StateFormulaConversionManager stateFormulaConversionManager;
  private final BooleanFormulaManager bfmgr;
  private final FormulaManagerView fmgr;

  private static final int SSA_NAMESPACING_CONST = 1000;

  /**
   * cf. {@link org.sosy_lab.cpachecker.cfa.postprocessing.function.SummaryGeneratorHelper}.
   */
  private final String copyVarPostfix;

  public PolicySummaryManager(
      PathFormulaManager pPfmgr,
      StateFormulaConversionManager pStateFormulaConversionManager,
      FormulaManagerView pFmgr,
      Configuration pConfig,
      LogManager pLogger) throws InvalidConfigurationException {
    fmgr = pFmgr;
    pfmgr = pPfmgr;
    stateFormulaConversionManager = pStateFormulaConversionManager;
    bfmgr = pFmgr.getBooleanFormulaManager();
    CFACreator cfaCreator = new CFACreator(
        pConfig, pLogger, ShutdownNotifier.createDummy());

    copyVarPostfix = cfaCreator.getPostfixForCopiedVars();
  }

  @Override
  public List<? extends AbstractState> getEntryStates(
      AbstractState callSite, CFANode callNode, Block calledBlock)
      throws CPAException, InterruptedException {
    return getEntryStates0((PolicyAbstractedState) callSite, callNode);
  }

  private List<? extends AbstractState> getEntryStates0(
      PolicyAbstractedState aCallState, CFANode callNode)
      throws CPAException, InterruptedException {

    PolicyIntermediateState iCallState =
        stateFormulaConversionManager.abstractStateToIntermediate(aCallState);
    PathFormula context = iCallState.getPathFormula();

    assert callNode.getNumLeavingEdges() == 1;

    CFunctionCallEdge callEdge = (CFunctionCallEdge) callNode.getLeavingEdge(0);
    List<CExpression> arguments = callEdge.getArguments();
    List<CParameterDeclaration> parameters = callEdge.getSuccessor().getFunctionParameters();
    Preconditions.checkState(arguments.size() == parameters.size());

    PathFormula entryState = pfmgr.makeAnd(
        context, callEdge
    );

    // todo: fix the precision inside the function, only consider the vars
    // *read* inside the func.
    return Collections.singletonList(PolicyIntermediateState.of(
        callEdge.getSuccessor(),
        entryState,
        aCallState
    ));
  }

  @Override
  public List<? extends AbstractState> applyFunctionSummary(
      AbstractState callSite, AbstractState exitState, CFANode callNode, Block calledBlock)
      throws CPATransferException, InterruptedException {
    PolicyAbstractedState aCallState = (PolicyAbstractedState) callSite;
    PolicyAbstractedState aExitState = (PolicyAbstractedState) exitState;

    assert callNode.getNumLeavingEdges() == 1;
    CFunctionCallEdge callEdge = (CFunctionCallEdge) callNode.getLeavingEdge(0);
    CFANode returnNode = callNode.getLeavingSummaryEdge().getSuccessor();
    assert returnNode.getNumEnteringEdges() == 1;
    return Collections.singletonList(
        applySummary(
            aCallState, aExitState,
            returnNode,
            callEdge,
            calledBlock
        )
    );
  }

  private PolicyIntermediateState applySummary(
    PolicyAbstractedState callState,
    PolicyAbstractedState exitState,
    CFANode returnNode,
    CFunctionCallEdge callEdge,
    Block calledBlock
  ) throws CPATransferException, InterruptedException {

    Map<String, Integer> ssaUpdatesToExit = new HashMap<>();

    SSAMap outMap = getOutSSAMap(
        callEdge, callState, exitState, calledBlock, ssaUpdatesToExit);

    SSAMap exitSsa = exitState.getSSA();
    SSAMap callSsa = callState.getSSA();

    SSAMapBuilder newExitSsaBuilder = SSAMap.emptySSAMap().builder();
    exitSsa.allVariables().forEach(
        varName -> {
          int newIdx = ssaUpdatesToExit.getOrDefault(varName, exitSsa.getIndex(varName));
          newExitSsaBuilder.setIndex(varName, exitSsa.getType(varName), newIdx);
        }
    );
    SSAMap newExitSsa = newExitSsaBuilder.build();

    PolicyAbstractedState weakenedExitState = rebaseExitState(exitState, newExitSsa);

    BooleanFormula paramRenamingConstraint = getParamRenamingConstraint(
        callEdge, callSsa, newExitSsa
    );

    BooleanFormula returnRenamingConstraint = getReturnRenamingConstraint(
        callEdge.getSummaryEdge(), callSsa, newExitSsa
    );

    BooleanFormula modifiedGlobalsRenamingConstraint = getModifiedGlobalsRenamingConstraint(
        callEdge.getSummaryEdge(), callSsa, newExitSsa, calledBlock
    );

    PathFormula outConstraint = new PathFormula(
        bfmgr.and(
            paramRenamingConstraint,
            returnRenamingConstraint,
            modifiedGlobalsRenamingConstraint
        ),
        outMap,
        callState.getPointerTargetSet(),
        1
    );

    return PolicyIntermediateState.of(
        returnNode, outConstraint, callState, weakenedExitState
    );
  }

  /**
   * Rebase exit state on top of the new SSA.
   */
  private PolicyAbstractedState rebaseExitState(
      PolicyAbstractedState exitState,
      SSAMap newSsa
  ) {
    return exitState.withNewSSA(newSsa);
  }

  /**
   * Generate {@link SSAMap} associated with the linked state.
   *
   * @param ssaUpdatesToIndex Write-into parameters for specifying updates which should
   *                          be performed on the exit state.
   */
  private SSAMap getOutSSAMap(
      CFunctionCallEdge pCallEdge,
      PolicyAbstractedState pCallState,
      PolicyAbstractedState pExitState,
      Block pBlock,
      Map<String, Integer> ssaUpdatesToIndex
  ) {
    SSAMapBuilder outSSABuilder = SSAMap.emptySSAMap().builder();
    SSAMap exitSsa = pExitState.getSSA();
    SSAMap callSsa = pCallState.getSSA();

    CFunctionSummaryEdge summaryEdge = pCallEdge.getSummaryEdge();

    Set<String> processed = new HashSet<>();

    // For modified globals:
    // the SSA index should be larger than that of
    // the one currently in {@code callSsa} and should agree with
    // {@code exitSsa}.
    pBlock.getModifiedGlobals().forEach(s -> {
          String varName = s.getQualifiedName();
          if (varName.contains(copyVarPostfix)) {
            return; // do not process twice.
          }

          int callIdx = callSsa.getIndex(varName);
          int newIdx = callIdx + 1;
          ssaUpdatesToIndex.put(varName, newIdx);
          processed.add(varName);
          outSSABuilder.setIndex(varName, callSsa.getType(varName), newIdx);
        }
    );

    // For the variable written into, the index should be one bigger
    // than that of a callsite.
    getWrittenIntoVar(summaryEdge).ifPresent(s -> {
      String varName = s.getDeclaration().getQualifiedName();
      int callIdx = callSsa.getIndex(varName);
      int newIdx = callIdx + 1;
      ssaUpdatesToIndex.put(varName, newIdx);
      processed.add(varName);
      outSSABuilder.setIndex(varName, callSsa.getType(varName), newIdx);
    });

    // For read globals which are NOT modified:
    // the SSA index should match on call and exit site.
    pBlock.getReadGlobals().stream()
        .map(s -> s.getQualifiedName())
        .filter(s -> !pBlock.getModifiedVariableNames().contains(s))
        .forEach(varName -> {
          int callIdx = callSsa.getIndex(varName);
          ssaUpdatesToIndex.put(varName, callIdx);
          processed.add(varName);
          outSSABuilder.setIndex(varName, callSsa.getType(varName), callIdx);
        });


    // For all variables from calling site which weren't processed yet:
    // the output SSA index should be the same.
    callSsa.allVariables().stream().filter(
        varName -> !processed.contains(varName)
    ).forEach(
        varName -> outSSABuilder.setIndex(
            varName, callSsa.getType(varName), callSsa.getIndex(varName)));

    // For all other variables: namespace them away in order to avoid the
    // collision.
    exitSsa.allVariables().stream().filter(
        varName -> !processed.contains(varName)
    ).forEach(
        varName -> ssaUpdatesToIndex.put(varName, namespaceSsaIdx(callSsa.getIndex(varName))));

    return outSSABuilder.build();
  }

  /**
   * Generate a set of constraints stating that the value of
   * the globals at the function call is equal to the value
   * of the global with {@link #copyVarPostfix} appended.
   *
   * @param callSSA Used for global value
   * @param exitSSA Used for global original copy
   */
  private BooleanFormula getModifiedGlobalsRenamingConstraint(
      CFunctionSummaryEdge pEdge,
      SSAMap callSSA,
      SSAMap exitSSA,
      Block pCalledBlock
  ) throws UnrecognizedCCodeException {

    PathFormula callContext = new PathFormula(bfmgr.makeTrue(),
        callSSA, PointerTargetSet.emptyPointerTargetSet(), 0);
    PathFormula exitContext = new PathFormula(bfmgr.makeTrue(),
        exitSSA, PointerTargetSet.emptyPointerTargetSet(), 0);

    List<BooleanFormula> constraints = new ArrayList<>(pCalledBlock.getModifiedGlobals().size());

    for (CVariableDeclaration decl : pCalledBlock.getModifiedGlobals()) {
      if (decl.getQualifiedName().contains(copyVarPostfix)) {

        // Basically, we should not apply those
        // constraints to already namespaced variables.
        continue;
      }

      CVariableDeclaration origDecl = addPostfixToDeclarationName(decl);

      CIdExpression callGlobalExpr = new CIdExpression(pEdge.getFileLocation(), decl);
      CIdExpression exitGlobalExpr = new CIdExpression(pEdge.getFileLocation(), origDecl);

      Formula callF = pfmgr.expressionToFormula(callContext, callGlobalExpr, pEdge);
      Formula exitF = pfmgr.expressionToFormula(exitContext, exitGlobalExpr, pEdge);
      constraints.add(fmgr.makeEqual(callF, exitF));
    }

    return bfmgr.and(constraints);
  }

  /**
   * @return constraint for renaming returned parameters.
   *         In order to be consistent with {@link #getOutSSAMap},
   *         increments the SSA index of the variable written into by one.
   *
   * @param exitSSA {@link SSAMap} used for returned parameter.
   * @param callSSA {@link SSAMap} used for parameter overriden by the function call.
   */
  private BooleanFormula getReturnRenamingConstraint(
      CFunctionSummaryEdge pEdge,
      SSAMap callSSA,
      SSAMap exitSSA
  ) throws CPATransferException, InterruptedException {
    SSAMapBuilder usedSSABuilder = SSAMap.emptySSAMap().builder();
    Optional<CIdExpression> writtenInto = getWrittenIntoVar(pEdge);
    if (!writtenInto.isPresent()) {
      return bfmgr.makeTrue();
    }

    String writtenIntoVarName = writtenInto.get().getDeclaration().getQualifiedName();
    usedSSABuilder.setIndex(
        writtenIntoVarName,
        callSSA.getType(writtenIntoVarName),
        callSSA.getIndex(writtenIntoVarName));

    for (String var : exitSSA.allVariables()) {
      if (!var.equals(writtenIntoVarName)) {
        usedSSABuilder.setIndex(var, exitSSA.getType(var), exitSSA.getIndex(var));
      }
    }

    CFAEdge returnEdge = pEdge.getSuccessor().getEnteringEdge(0);
    PathFormula context = new PathFormula(
        bfmgr.makeTrue(),
        usedSSABuilder.build(),
        PointerTargetSet.emptyPointerTargetSet(),
        1);

    PathFormula out = pfmgr.makeAnd(context, returnEdge);
    assert out.getSsa().getIndex(writtenIntoVarName) == callSSA.getIndex(writtenIntoVarName) + 1;

    return out.getFormula();
  }


  /**
   * Get constraint for parameter renaming.
   */
  private BooleanFormula getParamRenamingConstraint(
      CFunctionCallEdge pCallEdge,
      SSAMap pCallSSA,
      SSAMap pExitSSA
  ) throws CPATransferException, InterruptedException {

    PathFormula callingContext = new PathFormula(
        bfmgr.makeTrue(), pCallSSA, PointerTargetSet.emptyPointerTargetSet(), 0
    );
    PathFormula exitContext = new PathFormula(
        bfmgr.makeTrue(), pExitSSA, PointerTargetSet.emptyPointerTargetSet(), 0);

    CFunctionEntryNode entryNode = pCallEdge.getSuccessor();
    List<CExpression> args = pCallEdge.getArguments();
    List<CParameterDeclaration> params = entryNode.getFunctionParameters();
    assert args.size() == params.size();
    List<BooleanFormula> constraints = new ArrayList<>();

    for (int i=0; i<args.size(); i++) {
      CExpression arg = args.get(i);

      CVariableDeclaration paramVarDeclaration =
          params.get(i).asVariableDeclaration();

      // Created by SummaryGeneratorHelper.
      CVariableDeclaration renamedOrigDeclaration = addPostfixToDeclarationName(paramVarDeclaration);

      CIdExpression paramExpression =
          new CIdExpression(pCallEdge.getFileLocation(), renamedOrigDeclaration);
      Formula argF = pfmgr.expressionToFormula(
          callingContext, arg, pCallEdge
      );
      Formula paramF = pfmgr.expressionToFormula(
          exitContext, paramExpression, pCallEdge
      );
      constraints.add(fmgr.makeEqual(paramF, argF));
    }
    return bfmgr.and(constraints);
  }

  /**
   * @return var written into by the function call.
   * E.g. {@code a} in {@code a = f(42);},
   * empty in {@code void f() {}; f(120);}
   */
  private Optional<CIdExpression> getWrittenIntoVar(CFunctionSummaryEdge pEdge) {
    if (pEdge.getExpression() instanceof CFunctionCallAssignmentStatement) {
      CLeftHandSide lhs = ((CFunctionCallAssignmentStatement) pEdge.getExpression()).getLeftHandSide();
      if (lhs instanceof CIdExpression) {
        return Optional.of((CIdExpression) lhs);

      } else {
        throw new UnsupportedOperationException("Only writes to variables "
            + "are currently supported by LPI + summaries.");
      }
    }
    return Optional.empty();
  }

  /**
   * "Namespace" the SSA index in order to guarantee no collisions between exit and call state.
   */
  private int namespaceSsaIdx(int idx) {
    return SSA_NAMESPACING_CONST + idx;
  }


  private CVariableDeclaration addPostfixToDeclarationName(CVariableDeclaration origDeclaration) {
//     todo: reduce code duplication with TemplatePrecision and SummaryGenerationHelper.
    return new CVariableDeclaration(
            origDeclaration.getFileLocation(),
            origDeclaration.isGlobal(),
            origDeclaration.getCStorageClass(),
            origDeclaration.getType(),
            origDeclaration.getName() + copyVarPostfix,
            origDeclaration.getOrigName() + copyVarPostfix,
            origDeclaration.getQualifiedName() + copyVarPostfix,
            new CInitializerExpression(
                origDeclaration.getFileLocation(),
                new CIdExpression(origDeclaration.getFileLocation(), origDeclaration)));
  }
}