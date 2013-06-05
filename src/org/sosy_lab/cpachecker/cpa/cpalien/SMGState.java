 /*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.cpalien;

import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

public class SMGState implements AbstractQueryableState {
  static private int id_counter = 0;

  private final CLangSMG heap;
  private final LogManager logger;
  private SMGState predecessor;
  private final int id;

  private SMGRuntimeCheck runtimeCheckLevel;

  private boolean invalidWrite = false;

  /**
   * Constructor.
   *
   * Keeps consistency: yes
   *
   * @param pLogger A logger to log any messages
   * @param pMachineModel A machine model for the underlying SMGs
   */
  public SMGState(LogManager pLogger, MachineModel pMachineModel) {
    heap = new CLangSMG(pMachineModel);
    logger = pLogger;
    predecessor = null;
    id = id_counter++;
    runtimeCheckLevel = SMGRuntimeCheck.NONE;
  }

  /**
   * Copy constructor.
   *
   * Keeps consistency: yes
   *
   * @param pOriginalState Original state. Will be the predecessor of the
   * new state
   * @throws SMGInconsistentException
   */
  public SMGState(SMGState pOriginalState) {
    heap = new CLangSMG(pOriginalState.heap);
    logger = pOriginalState.logger;
    predecessor = pOriginalState.predecessor;
    runtimeCheckLevel = pOriginalState.runtimeCheckLevel;
    id = id_counter++;
  }

  /**
   * Sets a level of runtime checks performed.
   *
   * Keeps consistency: yes
   *
   * @param pLevel One of {@link SMGRuntimeCheck.NONE},
   * {@link SMGRuntimeCheck.HALF} or {@link SMGRuntimeCheck.FULL}
   * @throws SMGInconsistentException
   */
  final public void setRuntimeCheck(SMGRuntimeCheck pLevel) throws SMGInconsistentException {
    runtimeCheckLevel = pLevel;
    if (pLevel.isFinerOrEqualThan(SMGRuntimeCheck.HALF)) {
      CLangSMG.setPerformChecks(true);
    }
    else {
      CLangSMG.setPerformChecks(false);
    }
    this.performConsistencyCheck(SMGRuntimeCheck.FULL);
  }

  /**
   * Constant.
   *
   * @param pSMGState A state to set as a predecessor.
   * @throws SMGInconsistentException
   */
  final public void setPredecessor(SMGState pSMGState) throws SMGInconsistentException {
    predecessor = pSMGState;
    this.performConsistencyCheck(SMGRuntimeCheck.FULL);
  }

  /**
   * Makes SMGState create a new object and put it into the current stack
   * frame.
   *
   * Keeps consistency: yes
   *
   * @param pType Type of the new object
   * @param pVarName Name of the local variable
   * @return Newly created object
   *
   * @throws SMGInconsistentException when resulting SMGState is inconsistent
   * and the checks are enabled
   */
  public SMGObject addLocalVariable(CType pType, String pVarName) throws SMGInconsistentException {
    int size = heap.getMachineModel().getSizeof(pType);
    SMGObject new_object = new SMGObject(size, pVarName);

    heap.addStackObject(new_object);
    this.performConsistencyCheck(SMGRuntimeCheck.HALF);
    return new_object;
  }

  /**
   * Adds a new frame for the function.
   *
   * Keeps consistency: yes
   *
   * @param pFunctionDefinition A function for which to create a new stack frame
   * @throws SMGInconsistentException
   */
  public void addStackFrame(CFunctionDeclaration pFunctionDefinition) throws SMGInconsistentException {
    heap.addStackFrame(pFunctionDefinition);
    this.performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  /* ********************************************* */
  /* Non-modifying functions: getters and the like */
  /* ********************************************* */

  /**
   * Constant.
   *
   * @return The ID of this SMGState
   */
  final public int getId() {
    return id;
  }

  /**
   * Constant.
   * .
   * @return The predecessor state, i.e. one from which this one was copied
   */
  final public SMGState getPredecessor() {
    return predecessor;
  }

  /**
   * Constant.
   *
   * @return A {@link SMGObject} for current function return value storage.
   */
  final public SMGObject getFunctionReturnObject() {
    return heap.getFunctionReturnObject();
  }

  /**
   * Get memory of variable with the given name.
   *
   * @param pVariableName A name of the desired variable
   * @return An object corresponding to the variable name
   */
  public SMGObject getObjectForVisibleVariable(String pVariableName) {
    return this.heap.getObjectForVisibleVariable(pVariableName);
  }

  /**
   * Based on the current setting of runtime check level, it either performs
   * a full consistency check or not. If the check is performed and the
   * state is deemed inconsistent, a {@link SMGInconsistentException} is thrown.
   *
   * Constant.
   *
   * @param pLevel A level of the check request. When e.g. HALF is passed, it
   * means "perform the check if the setting is HALF or finer.
   * @throws SMGInconsistentException
   */
  final public void performConsistencyCheck(SMGRuntimeCheck pLevel) throws SMGInconsistentException {
    if (this.runtimeCheckLevel.isFinerOrEqualThan(pLevel)) {
      if ( ! CLangSMGConsistencyVerifier.verifyCLangSMG(logger, heap) ){
        throw new SMGInconsistentException("SMG was found inconsistent during a check");
      }
    }
  }

  /**
   * Returns a DOT representation of the SMGState.
   *
   * Constant.
   *
   * @param pName A name of the graph.
   * @param pLocation A location in the program.
   * @return String containing a DOT graph corresponding to the SMGState.
   */
  public String toDot(String pName, String pLocation) {
    SMGPlotter plotter = new SMGPlotter();
    return plotter.smgAsDot(heap, pName, pLocation);
  }

  /**
   * @return A string representation of the SMGState.
   */
  @Override
  public String toString() {
    if ( this.getPredecessor() != null) {
      return "SMGState [" + this.getId() + "] <-- parent [" + this.getPredecessor().getId() + "]\n" + heap.toString();
    } else {
      return "SMGState [" + this.getId() + "] <-- no parent, initial state\n" + heap.toString();
    }
  }

  public SMGEdgePointsTo getPointerFromValue(Integer pValue) throws SMGInconsistentException {
    for (SMGEdgePointsTo edge : heap.getPTEdges()){
      if (edge.getValue() == pValue) {
        return edge;
      }
    }

    throw new SMGInconsistentException("Asked for a Points-To edge for a non-pointer value");
  }

  /**
   * Read Value in field (object, type) of an Object.
   *
   * @param pObject SMGObject representing the memory the field belongs to.
   * @param pOffset offset of field being read.
   * @param pType type of field
   * @return
   * @throws SMGInconsistentException
   */
  public Integer readValue(SMGObject pObject, int pOffset, CType pType) throws SMGInconsistentException {
    SMGEdgeHasValue edge = new SMGEdgeHasValue(pType, pOffset, pObject, 0);
    Set<SMGEdgeHasValue> edges = heap.getValuesForObject(pObject, pOffset);

    for (SMGEdgeHasValue object_edge : edges) {
      if (edge.isCompatibleFieldOnSameObject(object_edge, heap.getMachineModel())){
        this.performConsistencyCheck(SMGRuntimeCheck.HALF);
        return object_edge.getValue();
      }
    }

    // TODO: Nullified blocks coverage interpretation
    this.performConsistencyCheck(SMGRuntimeCheck.HALF);
    return null;
  }

  /**
   * Write a value into a field (offset, type) of an Object.
   *
   *
   * @param object SMGObject representing the memory the field belongs to.
   * @param offset offset of field written into.
   * @param type type of field written into.
   * @param value value to be written into field.
   * @param machineModel Currently used Machine Model
   * @throws SMGInconsistentException
   */
  public SMGEdgeHasValue writeValue(SMGObject pObject, int pOffset, CType pType, Integer pValue) throws SMGInconsistentException {
    // vgl Algorithm 1 Byte-Precise Verification of Low-Level List Manipulation FIT-TR-2012-04

    if (pValue == null) {
      pValue = heap.getNullValue();
    }

    if (! this.heap.isObjectValid(pObject)){
      //Attempt to write to invalid object
      this.setInvalidWrite();
      return null;
    }

    SMGEdgeHasValue new_edge = new SMGEdgeHasValue(pType, pOffset, pObject, pValue);

    // Check if the edge is  not present already
    Set<SMGEdgeHasValue> edges = heap.getValuesForObject(pObject);
    if (edges.contains(new_edge)){
      this.performConsistencyCheck(SMGRuntimeCheck.HALF);
      return new_edge;
    }

    // If the value is not in the SMG, we need to add it
    if ( ! heap.getValues().contains(pValue) ){
      heap.addValue(pValue);
    }

    // We need to remove all non-zero overlapping edges
    for (SMGEdgeHasValue hv : edges){
      if (hv.getValue() != heap.getNullValue() && new_edge.overlapsWith(hv, heap.getMachineModel())){
        heap.removeHasValueEdge(hv);
      }
    }

    //TODO: Shrink overlapping zero edges
    heap.addHasValueEdge(new_edge);
    this.performConsistencyCheck(SMGRuntimeCheck.HALF);

    return new_edge;
  }

  private void setInvalidWrite() {
    this.invalidWrite = true;
  }

  /**
   * Returns an unmodifiable set of {@link SMGEdgeHasValue} objects leading from
   * {@link pObject}.
   *
   * Constant.
   *
   * @param pObject A source object
   * @return A set of edges leading from an object.
   */
  public Set<SMGEdgeHasValue> getValuesForObject(SMGObject pObject) {
    return heap.getValuesForObject(pObject);
  }

  /**
   * Computes the join of this abstract State and the reached abstract State.
   *
   * @param reachedState the abstract state this state will be joined to.
   * @return the join of the two states.
   */
  public SMGState join(SMGState reachedState) {
    // Not neccessary if merge_SEP and stop_SEP is used.
    return null;
  }

  /**
   * Computes whether this abstract state is covered by the given abstract state.
   * A state is covered by another state, if the set of concrete states
   * a state represents is a subset of the set of concrete states the other
   * state represents.
   *
   *
   * @param reachedState already reached state, that may cover this state already.
   * @return True, if this state is covered by the given state, false otherwise.
   */
  public boolean isLessOrEqual(SMGState reachedState) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String getCPAName() {
    return "CPAlien";
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    // SMG Properties:
    // has-leaks:boolean

    switch (pProperty) {
      case "has-leaks":
        if (heap.hasMemoryLeaks()) {
          //TODO: Give more information
          this.logger.log(Level.SEVERE, "Memory leak found");
          return true;
        }
        return false;
      case "has-invalid-writes":
        if (this.invalidWrite) {
          //TODO: Give more information
          this.logger.log(Level.SEVERE, "Invalid write found");
          return true;
        }
        return false;
      default:
        throw new InvalidQueryException("Query '" + pProperty + "' is invalid.");
    }
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    return checkProperty(pProperty);
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    // TODO Auto-generated method stub

  }

  public void addGlobalObject(SMGObject newObject) {
    heap.addGlobalObject(newObject);
  }

  public boolean isGlobal(String variable) {
    return  heap.getGlobalObjects().containsValue(heap.getObjectForVisibleVariable(variable));
  }

  public SMGEdgePointsTo addNewHeapAllocation(int pSize, String pLabel) throws SMGInconsistentException {
    SMGObject new_object = new SMGObject(pSize, pLabel);
    int new_value = SMGValueFactory.getNewValue();
    SMGEdgePointsTo points_to = new SMGEdgePointsTo(new_value, new_object, 0);
    heap.addHeapObject(new_object);
    heap.addValue(new_value);
    heap.addPointsToEdge(points_to);

    this.performConsistencyCheck(SMGRuntimeCheck.HALF);
    return points_to;
  }

  public void setMemLeak() {
    heap.setMemoryLeak();
  }

 public boolean containsValue(int value) {
   return heap.getValues().contains(value);
 }

  /**
   * Get address of the given memory with the given offset.
   *
   * @param memory get address belonging to this memory.
   * @param offset get address with this offset relative to the beginning of the memory.
   * @return Address of the given field, or null, if such an address
   * does not yet exist in the SMG.
   */
  public Integer getAddress(SMGObject memory, Integer offset) {
    // TODO Auto-generated method stub
    return null;
  }


  /**
   * Get the SMGObject representing the Memory the given address points to.
   *
   * @param address the address belonging to the memory to be returned.
   * @return SMGObject representing the Memory this address points to, or null,
   * if the memory this address belongs to is unkown.
   */
  public SMGObject getMemoryOfAddress(Integer address) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * This method simulates a free invocation. It checks,
   * whether the call is valid, and invalidates the
   * Memory the given address points to.
   * The address (address, offset, smgObject) is the argument
   * of the free invocation. It does not need to be part of the SMG.
   *
   * @param address The symbolic Value of the address.
   * @param offset The offset of the address relative to the beginning of smgObject.
   * @param smgObject The memory the given Address belongs to.
   * @throws SMGInconsistentException
   */
  public void free(Integer address, Integer offset, SMGObject smgObject) throws SMGInconsistentException {
    heap.setValidity(smgObject, false);
    for (SMGEdgeHasValue edge : heap.getValuesForObject(smgObject)) {
      heap.removeHasValueEdge(edge);
    }
    this.performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  /**
   * Set the two given symbolic values to be not equal.
   *
   * @param value1 the first symbolic value.
   * @param value2 the second symbolic value.
   */
  public void setUnequal(int value1, int value2) {
    // TODO Auto-generated method stub
  }

  /**
   * Determine, whether the two given symbolic values are not equal.
   * If this method does not return true, the relation of these
   * symbolic values is unknown.
   *
   * @param value1 first symbolic value to be checked
   * @param value2 second symbolic value to be checked
   * @return true, if the symbolic values are known to be not equal, false, if it is unknown.
   */
  public boolean isUnequal(int value1, int value2) {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * Get the offset of this address.
   *
   * @param address An address pointing to an object in the SMG.
   * @return the offset of the given address or null, if the given value is
   *   not an address or the object its pointing to is unknown.
   *
   */
  public Integer getOffset(Integer address) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Drop the stack frame representing the stack of
   * the function with the given name
   *
   * @param functionName
   * @throws SMGInconsistentException
   */
  public void dropStackFrame(String functionName) throws SMGInconsistentException {
    this.heap.dropStackFrame();
    this.performConsistencyCheck(SMGRuntimeCheck.FULL);
    this.heap.pruneUnreachable();
    this.performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  /**
   * Creates a new SMGObject representing Memory.
   *
   * @param size the size in Bytes of the newly created SMGObject.
   * @param label a label representing this SMGObject as a String.
   * @return A newly created SMGObject representing Memory.
   */
  public SMGObject createObject(int size, String label) {
    return new SMGObject(size, label);
  }

  /**
   * Assigns the given symbolic value an explicit value.
   *
   * @param symbolicValue the symbolic value to be assigned.
   * @param explicitValue the explicit value which will be assigned to the given symbolic value.
   */
  public void assignExplicitValue(Integer symbolicValue, Integer explicitValue) {
    // TODO Auto-generated method stub
  }

  /**
   * Return true, if the explicit value of the given symbolic value is known
   *
   * @param symbolicValue Search for the explicit value of the given symbolic value.
   * @return true if the explicit value of the given symbolic one is known, else false.
   */
  public boolean isExplicitValueKnown(Integer symbolicValue) {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * Return the explicit value assigned to the given symbolic value,
   * or null, if the symbolic value was not assigned a explicit value.
   *
   * @param symbolicValue get the explicit value assigned to this symbolic value.
   * @return the explicit value assigned to the given symbolic value, or null.
   */
  public Integer getExplicitValue(Integer symbolicValue) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Returns true, if the given explicit value is assigned to
   * a symbolic value.
   *
   * @param explicitValue the explicit Value to be searched for an assignment,
   *
   * @return true if the given explicit value is assigned to
   * a symbolic value, else false.
   */
  public boolean isSymbolicValueKnown(int explicitValue) {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * Return the symbolic value assigned to a explicit value,
   * or null, if the explicit value was not assigned  one.
   *
   * @param explicitValue get the symbolic value assigned to this explicit value.
   * @return the symbolic value assigned to the given explicit value, or null.
   */
  public Integer getSymbolicValue(int explicitValue) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   *  Signals an invalid free call.
   */
  public void setInvalidFree() {
    // TODO Auto-generated method stub
  }

  /**
   * Signals a dereference of a pointer or array
   *  which could not be resolved.
   */
  public void setUnkownDereference() {
    // TODO Auto-generated method stub
  }
}
