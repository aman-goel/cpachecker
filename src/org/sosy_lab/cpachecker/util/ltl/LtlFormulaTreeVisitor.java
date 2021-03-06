/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.ltl;

import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.sosy_lab.cpachecker.util.ltl.formulas.BooleanConstant;
import org.sosy_lab.cpachecker.util.ltl.formulas.Conjunction;
import org.sosy_lab.cpachecker.util.ltl.formulas.Disjunction;
import org.sosy_lab.cpachecker.util.ltl.formulas.Finally;
import org.sosy_lab.cpachecker.util.ltl.formulas.LtlFormula;
import org.sosy_lab.cpachecker.util.ltl.formulas.Globally;
import org.sosy_lab.cpachecker.util.ltl.formulas.Literal;
import org.sosy_lab.cpachecker.util.ltl.formulas.Next;
import org.sosy_lab.cpachecker.util.ltl.formulas.Release;
import org.sosy_lab.cpachecker.util.ltl.formulas.StrongRelease;
import org.sosy_lab.cpachecker.util.ltl.formulas.Until;
import org.sosy_lab.cpachecker.util.ltl.formulas.WeakUntil;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarBaseVisitor;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.AndExpressionContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.BinaryOpContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.BinaryOperationContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.BoolContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.BooleanContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.ExpressionContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.FormulaContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.LtlPropertyContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.NestedContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.OrExpressionContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.PropertyContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.QuotedVariableContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.UnaryOpContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.UnaryOperationContext;
import org.sosy_lab.cpachecker.util.ltl.generated.LtlGrammarParser.VariableContext;

public class LtlFormulaTreeVisitor extends LtlGrammarBaseVisitor<LtlFormula> {

  @Override
  public LtlFormula visitProperty(PropertyContext ctx) {
    // Contains: CHECK LPAREN initFunction COMMA ltlProperty RPAREN EOF
    assert ctx.getChildCount() == 7 : ctx.getChildCount();

    // For now, we only want to retrieve the 'ltlProperty', so we ditch everything else
    return visit(ctx.getChild(4));
  }

  @Override
  public LtlFormula visitLtlProperty(LtlPropertyContext ctx) {
    assert ctx.getChildCount() == 4 : ctx.getChildCount();
    return visit(ctx.getChild(2));
  }

  @Override
  public LtlFormula visitFormula(FormulaContext ctx) {
    // Contains formula + EOF
    assert ctx.getChildCount() == 2 : ctx.getChildCount();
    return visit(ctx.getChild(0));
  }

  @Override
  public LtlFormula visitExpression(ExpressionContext ctx) {
    // Contains an orExpression only
    assert ctx.getChildCount() == 1;
    return visit(ctx.getChild(0));
  }

  @Override
  public LtlFormula visitOrExpression(OrExpressionContext ctx) {
    // Contains a disjunction of conjunctions
    assert ctx.getChildCount() > 0;

    ImmutableList.Builder<LtlFormula> builder = ImmutableList.builder();
    for (int i = 0; i < ctx.getChildCount(); i++) {
      if (i % 2 == 0) {
        builder.add(visit(ctx.getChild(i)));
      } else {
        assert ctx.getChild(i) instanceof TerminalNode;
      }
    }

    ImmutableList<LtlFormula> list = builder.build();
    return Disjunction.of(list);
  }

  @Override
  public LtlFormula visitAndExpression(AndExpressionContext ctx) {
    // Contains a conjunction of binaryExpressions
    assert ctx.getChildCount() > 0;

    ImmutableList.Builder<LtlFormula> builder = ImmutableList.builder();
    for (int i = 0; i < ctx.getChildCount(); i++) {
      if (i % 2 == 0) {
        builder.add(visit(ctx.getChild(i)));
      } else {
        assert ctx.getChild(i) instanceof TerminalNode;
      }
    }

    ImmutableList<LtlFormula> list = builder.build();
    return Conjunction.of(list);
  }

  @Override
  public LtlFormula visitBinaryOperation(BinaryOperationContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    BinaryOpContext binaryOp = ctx.binaryOp();
    LtlFormula left = visit(ctx.left);
    LtlFormula right = visit(ctx.right);

    if (binaryOp.EQUIV() != null) {
      return Disjunction.of(Conjunction.of(left, right), Conjunction.of(left.not(), right.not()));
    }

    if (binaryOp.IMP() != null) {
      return Disjunction.of(left.not(), right);
    }

    if (binaryOp.XOR() != null) {
      return Disjunction.of(Conjunction.of(left, right.not()), Conjunction.of(left.not(), right));
    }

    if (binaryOp.UNTIL() != null) {
      return Until.of(left, right);
    }

    if (binaryOp.WUNTIL() != null) {
      return WeakUntil.of(left, right);
    }

    if (binaryOp.RELEASE() != null) {
      return Release.of(left, right);
    }

    if (binaryOp.SRELEASE() != null) {
      return StrongRelease.of(left, right);
    }

    throw new ParseCancellationException("Unknown binary operator");
  }

  @Override
  public LtlFormula visitUnaryOperation(UnaryOperationContext ctx) {
    assert ctx.getChildCount() == 2;

    UnaryOpContext unaryOp = ctx.unaryOp();
    LtlFormula operand = visit(ctx.inner);

    if (unaryOp.NOT() != null) {
      return operand.not();
    }

    if (unaryOp.FINALLY() != null) {
      return Finally.of(operand);
    }

    if (unaryOp.GLOBALLY() != null) {
      return Globally.of(operand);
    }

    if (unaryOp.NEXT() != null) {
      return Next.of(operand);
    }

    throw new ParseCancellationException("Unknown unary operator");
  }

  @Override
  public LtlFormula visitBoolean(BooleanContext ctx) {
    assert ctx.getChildCount() == 1;
    BoolContext constant = ctx.bool();

    if (constant.FALSE() != null) {
      return BooleanConstant.FALSE;
    }

    if (constant.TRUE() != null) {
      return BooleanConstant.TRUE;
    }

    throw new ParseCancellationException("Unknown boolean constant");
  }

  @Override
  public LtlFormula visitQuotedVariable(QuotedVariableContext ctx) {
    // Contains: QUOTATIONMARK var EQUALS val QUOTATIONMARK
    assert ctx.getChildCount() == 5;

    String name = ctx.var.getText() + ctx.val.getText();
    return Literal.of(name, false);
  }

  @Override
  public LtlFormula visitVariable(VariableContext ctx) {
    assert ctx.getChildCount() == 1;
    return Literal.of(ctx.getText(), false);
  }

  @Override
  public LtlFormula visitNested(NestedContext ctx) {
    assert ctx.getChildCount() == 3;
    return visit(ctx.nested);
  }
}
