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
package org.sosy_lab.cpachecker.cpa.smg;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CVoidType;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;

/**
 * Represents a C language stack frame.
 *
 * <p>This is an immutable class.
 */
public final class CLangStackFrame {
  static final String RETVAL_LABEL = "___cpa_temp_result_var_";

  /**
   * Function to which this stack frame belongs
   */
  private final CFunctionDeclaration stack_function;

  /** A mapping from variable names to a set of SMG objects, representing local variables. */
  private final PersistentMap<String, SMGRegion> stack_variables;

  /**
   * An object to store function return value.
   * The Object is Null if function has Void-type.
   */
  @Nullable private final SMGRegion returnValueObject;

  private CLangStackFrame(
      CFunctionDeclaration pDeclaration,
      PersistentMap<String, SMGRegion> pVariables,
      SMGRegion pReturnValueObject) {
    stack_variables = pVariables;
    stack_function = pDeclaration;
    returnValueObject = pReturnValueObject;
  }

  /**
   * Constructor. Creates an empty frame.
   *
   * @param pDeclaration Function for which the frame is created
   *
   * TODO: [PARAMETERS] Create objects for function parameters
   */
  public CLangStackFrame(CFunctionDeclaration pDeclaration, MachineModel pMachineModel) {
    stack_variables = PathCopyingPersistentTreeMap.of();
    stack_function = pDeclaration;
    CType returnType = pDeclaration.getType().getReturnType().getCanonicalType();
    if (returnType instanceof CVoidType) {
      // use a plain int as return type for void functions
      returnValueObject = null;
    } else {
      int return_value_size = pMachineModel.getBitSizeof(returnType);
      returnValueObject = new SMGRegion(return_value_size, CLangStackFrame.RETVAL_LABEL);
    }
  }

  /**
   * Copy constructor.
   *
   * @param pFrame Original frame
   */
  public CLangStackFrame(CLangStackFrame pFrame) {
    stack_function = pFrame.stack_function;
    stack_variables = pFrame.stack_variables;
    returnValueObject = pFrame.returnValueObject;
  }


  /**
   * Adds a SMG object pObj to a stack frame, representing variable pVariableName
   *
   * <p>Throws {@link IllegalArgumentException} when some object is already present with the name
   * pVariableName
   *
   * @param pVariableName A name of the variable
   * @param pObject An object to put into the stack frame
   */
  public CLangStackFrame addStackVariable(String pVariableName, SMGRegion pObject) {
    Preconditions.checkArgument(!stack_variables.containsKey(pVariableName),
        "Stack frame for function '%s' already contains a variable '%s'",
        stack_function.toASTString(), pVariableName);

    return new CLangStackFrame(
        stack_function, stack_variables.putAndCopy(pVariableName, pObject), returnValueObject);
  }

  /* ********************************************* */
  /* Non-modifying functions: getters and the like */
  /* ********************************************* */

  /**
   * @return String representation of the stack frame
   */
  @Override
  public String toString() {
    return "<" + Joiner.on(" ").join(stack_variables.values()) + ">";
  }

  public CLangStackFrame removeVariable(String pName) {
    if (RETVAL_LABEL.equals(pName)) {
      // Do nothing for the moment
      return this;
    } else {
      return new CLangStackFrame(
          stack_function, stack_variables.removeAndCopy(pName), returnValueObject);
    }
  }

  /**
   * Getter for obtaining an object corresponding to a variable name
   *
   * Throws {@link NoSuchElementException} when passed a name not present
   *
   * @param pName Variable name
   * @return SMG object corresponding to pName in the frame
   */
  public SMGRegion getVariable(String pName) {

    if (pName.equals(RETVAL_LABEL) && returnValueObject != null) {
      return returnValueObject;
    }

    SMGRegion to_return = stack_variables.get(pName);
    if (to_return == null) {
      throw new NoSuchElementException(String.format(
          "No variable with name '%s' in stack frame for function '%s'",
          pName, stack_function.toASTString()));
    }
    return to_return;
  }

  /**
   * @param pName Variable name
   * @return True if variable pName is present, false otherwise
   */
  public boolean containsVariable(String pName) {
    if (pName.equals(RETVAL_LABEL)) {
      return returnValueObject != null;
    } else {
      return stack_variables.containsKey(pName);
    }
  }

  /**
   * @return Declaration of a function corresponding to the frame
   */
  public CFunctionDeclaration getFunctionDeclaration() {
    return stack_function;
  }

  /**
   * @return a mapping from variables name to SMGObjects
   */
  public Map<String, SMGRegion> getVariables() {
    return stack_variables;
  }

  /**
   * @return a set of all objects: return value object, variables, parameters
   */
  public Set<SMGObject> getAllObjects() {
    Builder<SMGObject> retset = ImmutableSet.builder();
    retset.addAll(stack_variables.values());
    if (returnValueObject != null) {
      retset.add(returnValueObject);
    }
    return retset.build();
  }

  /**
   * @return an {@link SMGObject} reserved for function return value
   */
  public SMGRegion getReturnObject() {
    return returnValueObject;
  }

  /**
   * returns true if stack contains the given variable, else false.
   */
  public boolean hasVariable(String var) {
    return stack_variables.containsKey(var);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CLangStackFrame)) {
      return false;
    }
    CLangStackFrame other = (CLangStackFrame)o;
    return Objects.equals(stack_variables, other.stack_variables)
        && Objects.equals(stack_function, other.stack_function)
        && Objects.equals(returnValueObject, other.returnValueObject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stack_variables, stack_function, returnValueObject);
  }
}
