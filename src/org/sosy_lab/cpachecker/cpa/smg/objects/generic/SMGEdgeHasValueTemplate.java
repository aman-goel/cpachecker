/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.smg.objects.generic;

import java.math.BigInteger;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

public class SMGEdgeHasValueTemplate extends SMGEdgeTemplate
    implements SMGEdgeHasValueTemplateWithConcreteValue {

  private final CType type;

  public SMGEdgeHasValueTemplate(SMGObjectTemplate pAbstractObject,
      int pAbstractValue, BigInteger pOffset,
      CType pType) {
    super(pAbstractObject, pAbstractValue, pOffset);
    type = pType;
  }

  @Override
  public CType getType() {
    return type;
  }

  @Override
  public int getValue() {
    return getAbstractValue();
  }

  @Override
  public String toString() {
    return getObjectTemplate().toString() + " O" + getOffset() + "B->" + getValue();
  }
}