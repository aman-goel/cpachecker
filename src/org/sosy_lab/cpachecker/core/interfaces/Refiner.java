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
package org.sosy_lab.cpachecker.core.interfaces;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * Strategy for refinement of ARG used by {@link CEGARAlgorithm}.
 *
 * Implementations need to have exactly one public constructor or a static method named "create"
 * which may take a {@link ConfigurableProgramAnalysis}, and throw at most a
 * {@link InvalidConfigurationException} and a {@link CPAException}.
 */
public interface Refiner {

  /**
   * Perform refinement, if possible.
   *
   * @param pReached The reached set.
   * @return Whether the refinement was successful.
   * @throws CPAException If an error occurred during refinement.
   */
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException;

  interface Factory {
    Refiner create(ConfigurableProgramAnalysis cpa) throws InvalidConfigurationException;
  }
}
