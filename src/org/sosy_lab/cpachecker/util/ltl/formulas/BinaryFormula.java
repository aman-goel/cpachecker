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
package org.sosy_lab.cpachecker.util.ltl.formulas;

public abstract class BinaryFormula implements LtlFormula {

  public final LtlFormula left;
  public final LtlFormula right;

  BinaryFormula(LtlFormula left, LtlFormula right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + left.hashCode();
    result = prime * result + getSymbol();
    result = prime * result + right.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (getClass() != obj.getClass()) {
      return false;
    }
    BinaryFormula other = (BinaryFormula) obj;
    if (!left.equals(other.left)) {
      return false;
    }
    if (getSymbol() != other.getSymbol()) {
      return false;
    }
    if (!right.equals(other.right)) {
      return false;
    }
    return true;
  }

  public abstract char getSymbol();

  @Override
  public String toString() {
    return String.format("(%s %s %s)", left, getSymbol(), right);
  }
}
