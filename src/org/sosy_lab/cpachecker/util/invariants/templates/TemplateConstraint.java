/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.invariants.templates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.sosy_lab.cpachecker.util.invariants.Coeff;
import org.sosy_lab.cpachecker.util.invariants.InfixReln;
import org.sosy_lab.cpachecker.util.invariants.interfaces.Constraint;
import org.sosy_lab.cpachecker.util.invariants.interfaces.VariableManager;
import org.sosy_lab.cpachecker.util.invariants.redlog.Rational;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;

public class TemplateConstraint extends TemplateConjunction implements Constraint {

  private TemplateSum LHS = null;
  private InfixReln reln = null;
  private TemplateSum RHS = null;

// ------------------------------------------------------------------
// Constructors

  public TemplateConstraint() {
    LHS = new TemplateSum();
    RHS = new TemplateSum();
  }

  public TemplateConstraint(TemplateSum s1, InfixReln R, TemplateSum s2) {
    reln = R;
    // Store in normal form.
    TemplateSum C1 = s1.getConstantPart();
    TemplateSum V1 = s1.getNonConstantPart();
    TemplateSum C2 = s2.getConstantPart();
    TemplateSum V2 = s2.getNonConstantPart();

    LHS = TemplateSum.subtract(V1,V2);
    RHS = TemplateSum.subtract(C2,C1);
  }

//------------------------------------------------------------------
// copy

  @Override
  public TemplateConstraint copy() {
    TemplateConstraint c = new TemplateConstraint(LHS.copy(), reln, RHS.copy());
    return c;
  }


//------------------------------------------------------------------
// Alter and Undo

  @Override
  public void alias(AliasingMap amap) {
    LHS.alias(amap);
    RHS.alias(amap);
  }

  @Override
  public void unalias() {
    LHS.unalias();
    RHS.unalias();
  }

  @Override
  public boolean evaluate(HashMap<String,Rational> map) {
    boolean ans = true;
    ans &= LHS.evaluate(map);
    ans &= RHS.evaluate(map);
    return ans;
  }

  @Override
  public void unevaluate() {
    LHS.unevaluate();
    RHS.unevaluate();
  }

  @Override
  public void postindex(Map<String,Integer> indices) {
    LHS.postindex(indices);
    RHS.postindex(indices);
  }

  @Override
  public void preindex(Map<String,Integer> indices) {
    LHS.preindex(indices);
    RHS.preindex(indices);
  }

  @Override
  public void unindex() {
    LHS.unindex();
    RHS.unindex();
  }

  @Override
  public Purification purify(Purification pur) {
    pur = LHS.purify(pur);
    pur = RHS.purify(pur);
    return pur;
  }

  @Override
  public void unpurify() {
    LHS.unpurify();
    RHS.unpurify();
  }

//------------------------------------------------------------------
// Other cascade methods

  @Override
  public Set<String> getAllVariables(VariableWriteMode vwm) {
    HashSet<String> vars = new HashSet<String>();
    vars.addAll(LHS.getAllVariables(vwm));
    vars.addAll(RHS.getAllVariables(vwm));
    return vars;
  }

  @Override
  public Set<TemplateVariable> getAllParameters() {
    HashSet<TemplateVariable> params = new HashSet<TemplateVariable>();
    params.addAll(LHS.getAllParameters());
    params.addAll(RHS.getAllParameters());
    return params;
  }

  @Override
  public HashMap<String,Integer> getMaxIndices(HashMap<String,Integer> map) {
    map = LHS.getMaxIndices(map);
    map = RHS.getMaxIndices(map);
    return map;
  }

  @Override
  public TemplateVariableManager getVariableManager() {
    TemplateVariableManager tvm = new TemplateVariableManager();
    tvm.merge( LHS.getVariableManager() );
    tvm.merge( RHS.getVariableManager() );
    return tvm;
  }

  @Override
  public void prefixVariables(String prefix) {
    // All terms with variables should be in the LHS, so we only
    // work on that side.
    LHS.prefixVariables(prefix);
  }

  @Override
  public Formula translate(FormulaManager fmgr) {
  	Formula form = null;
  	Formula lhs = LHS.translate(fmgr);
  	Formula rhs = RHS.translate(fmgr);
  	switch (reln) {
  	case EQUAL: form = fmgr.makeEqual(lhs, rhs); break;
  	case LEQ:   form = fmgr.makeLeq(lhs, rhs);   break;
  	}
  	return form;
  }

  @Override
  public List<TemplateFormula> extractAtoms(boolean sAE, boolean cO) {
    List<TemplateFormula> atoms = new Vector<TemplateFormula>();
  	if (!sAE) {
  		atoms.add(this);
  	} else {
  		// In this case we want to split equations into pairs of inequalities.
  		TemplateConstraint tc1 = new TemplateConstraint(LHS, InfixReln.LEQ, RHS);
  		TemplateConstraint tc2 = new TemplateConstraint(RHS, InfixReln.LEQ, LHS);
  		atoms.add(tc1);
  		atoms.add(tc2);
  	}
  	return atoms;
  }

  @Override
  Set<TermForm> getTopLevelTermForms() {
    // Get a copy of the LHS.
    TemplateSum copy = LHS.copy();

    // By purifying and unpurifying, we can normalize all
    // the sums in the entire syntax tree, without changing
    // anything else.
    // This makes it so that any two occurrences of a given
    // form will be identical except for coefficients.
    copy.purify(new Purification());
    copy.unpurify();

    // Forming the set throws away all but one of each form.
    Vector<TemplateTerm> terms = copy.getTerms();
    Set<TermForm> forms = new HashSet<TermForm>();
    for (TemplateTerm t : terms) {
      forms.add( new TermForm(t) );
    }
    return forms;
  }

//------------------------------------------------------------------
// Other

  @Override
  public Vector<TemplateConstraint> getConstraints() {
    Vector<TemplateConstraint> v = new Vector<TemplateConstraint>();
    v.add(this);
    return v;
  }

  @Override
  public Set<TemplateTerm> getRHSTerms() {
    Set<TemplateTerm> s = new HashSet<TemplateTerm>(RHS.getTerms());
    return s;
  }

  @Override
  public boolean isTrue() {
    return false;
  }

  @Override
  public boolean isFalse() {
    return false;
  }

  public List<Coeff> getNormalFormCoeffs(VariableManager vmgr, VariableWriteMode vwm) {
    return LHS.getCoeffsWithParams(vwm, vmgr);
  }

  public Coeff getNormalFormConstant(VariableWriteMode vwm) {
    return new Coeff(RHS.toString(vwm));
  }

  public InfixReln getInfixReln() {
    return reln;
  }

  @Override
  public String toString() {
    return toString(VariableWriteMode.PLAIN);
  }

  @Override
  public String toString(VariableWriteMode vwm) {
    String s = "";
    if (LHS!=null && reln!=null && RHS!=null) {
      s = LHS.toString(vwm);
      //System.out.println("LHS returned: "+s);
      s += " "+reln.toString()+" ";
      s += RHS.toString(vwm);
    }
    return s;
  }

}