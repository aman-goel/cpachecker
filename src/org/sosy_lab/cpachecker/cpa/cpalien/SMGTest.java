/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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

import static org.mockito.Mockito.mock;

import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CType;


public class SMGTest {
  private LogManager logger = mock(LogManager.class);

  private SMG smg;
  SMGObject obj1 = new SMGObject(8, "object-1");
  SMGObject obj2 = new SMGObject(8, "object-2");

  Integer val1 = Integer.valueOf(1);
  Integer val2 = Integer.valueOf(2);

  SMGEdgePointsTo pt1to1 = new SMGEdgePointsTo(val1, obj1, 0);
  SMGEdgeHasValue hv2has2at0 = new SMGEdgeHasValue(mock(CType.class), 0, obj2, val2);
  SMGEdgeHasValue hv2has1at4 = new SMGEdgeHasValue(mock(CType.class), 4, obj2, val1);

  private static SMG getNewSMG64(){
    return new SMG(MachineModel.LINUX64);
  }

  @Before
  public void setUp(){
    smg = getNewSMG64();

    smg.addObject(obj1);
    smg.addObject(obj2);

    smg.addValue(val1.intValue());
    smg.addValue(val2.intValue());

    smg.addPointsToEdge(pt1to1);

    smg.addHasValueEdge(hv2has2at0);
    smg.addHasValueEdge(hv2has1at4);
  }

  @Test
  public void SMGConstructorTest(){
    SMG smg = getNewSMG64();
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));
    SMGObject nullObject = smg.getNullObject();
    int nullAddress = smg.getNullValue();


    Assert.assertNotNull(nullObject);
    Assert.assertFalse(nullObject.notNull());
    Assert.assertEquals(1, smg.getObjects().size());
    Assert.assertTrue(smg.getObjects().contains(nullObject));

    Assert.assertEquals(1, smg.getValues().size());
    Assert.assertTrue(smg.getValues().contains(Integer.valueOf(nullAddress)));

    Assert.assertEquals(1, smg.getPTEdges().size());
    SMGObject target_object = smg.getObjectPointedBy(nullAddress);
    Assert.assertEquals(nullObject, target_object);

    Assert.assertEquals(0, smg.getHVEdges().size());

    //copy constructor
    SMG smg_copy = new SMG(smg);
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg_copy));

    SMGObject third_object = new SMGObject(16, "object-3");
    Integer third_value = Integer.valueOf(3);
    smg_copy.addObject(third_object);
    smg_copy.addValue(third_value.intValue());
    smg_copy.addHasValueEdge(new SMGEdgeHasValue(mock(CType.class), 0, third_object,  third_value));
    smg_copy.addPointsToEdge(new SMGEdgePointsTo(third_value, third_object, 0));

    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg_copy));
    Assert.assertEquals(1, smg.getObjects().size());
    Assert.assertEquals(2, smg_copy.getObjects().size());
    Assert.assertTrue(smg_copy.getObjects().contains(third_object));

    Assert.assertEquals(1, smg.getValues().size());
    Assert.assertEquals(2, smg_copy.getValues().size());
    Assert.assertTrue(smg_copy.getValues().contains(third_value));

    Assert.assertEquals(1, smg.getPTEdges().size());
    Assert.assertEquals(2, smg_copy.getPTEdges().size());
    SMGObject target_object_for_third = smg_copy.getObjectPointedBy(third_value);
    Assert.assertEquals(third_object, target_object_for_third);

    Assert.assertEquals(0, smg.getHVEdges().size());
    Assert.assertEquals(1, smg_copy.getHVEdges().size());
  }

  @Test
  public void validityTest(){
    Assert.assertFalse(smg.isObjectValid(smg.getNullObject()));
    Assert.assertTrue(smg.isObjectValid(obj1));
    Assert.assertTrue(smg.isObjectValid(obj2));

    SMG smg_copy = new SMG(smg);
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg_copy));
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));

    smg.setValidity(obj1, false);
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg_copy));
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));
    Assert.assertFalse(smg.isObjectValid(smg.getNullObject()));
    Assert.assertFalse(smg.isObjectValid(obj1));
    Assert.assertTrue(smg.isObjectValid(obj2));
    Assert.assertFalse(smg_copy.isObjectValid(smg_copy.getNullObject()));
    Assert.assertTrue(smg_copy.isObjectValid(obj1));
    Assert.assertTrue(smg_copy.isObjectValid(obj2));

    smg.setValidity(obj2, false);
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg_copy));
    Assert.assertFalse(smg_copy.isObjectValid(smg_copy.getNullObject()));
    Assert.assertTrue(smg_copy.isObjectValid(obj1));
    Assert.assertTrue(smg_copy.isObjectValid(obj2));
  }

  @Test
  public void ConsistencyViolationValidNullTest(){
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));
    smg.setValidity(smg.getNullObject(), true);
    Assert.assertFalse(SMGConsistencyVerifier.verifySMG(logger, smg));
  }

  @Test
  public void ConsistencyViolationInvalidRegionHasValue(){
    smg.setValidity(obj1, false);
    Assert.assertTrue(SMGConsistencyVerifier.verifySMG(logger, smg));
    smg.setValidity(obj2, false);
    Assert.assertFalse(SMGConsistencyVerifier.verifySMG(logger, smg));
  }

  @Test(expected=IllegalArgumentException.class)
  public void isObjectValidBadCallTest(){
    smg.isObjectValid(new SMGObject(24, "wee"));
  }

  @Test(expected=IllegalArgumentException.class)
  public void setValidityBadCallTest(){
    smg.setValidity(new SMGObject(24, "wee"), true);
  }

  @Test
  public void getObjectsTest() {
    HashSet<SMGObject> set = new HashSet<>();
    set.add(obj1);
    set.add(obj2);
    set.add(smg.getNullObject());

    Assert.assertTrue(smg.getObjects().containsAll(set));
  }

  @Test
  public void getNullObjectTest(){
    SMGObject nullObject = smg.getNullObject();
    Assert.assertFalse(smg.isObjectValid(nullObject));
    Assert.assertEquals(nullObject.getSizeInBytes(), 0);
  }

  @Test
  public void getValuesTest(){
    HashSet<Integer> set = new HashSet<>();
    set.add(val1);
    set.add(val2);
    set.add(smg.getNullValue());

    Assert.assertTrue(smg.getValues().containsAll(set));
  }

  @Test
  public void getHVEdgesTest(){
    HashSet<SMGEdgeHasValue> set = new HashSet<>();
    set.add(hv2has1at4);
    set.add(hv2has1at4);

    Assert.assertTrue(smg.getHVEdges().containsAll(set));
  }

  @Test
  public void getPTEdgesTest(){
    HashSet<SMGEdgePointsTo> set = new HashSet<>();
    set.add(pt1to1);

    Assert.assertTrue(smg.getPTEdges().containsAll(set));
  }

  @Test
  public void getObjectPointedByTest(){
    Assert.assertEquals(obj1, smg.getObjectPointedBy(val1));
    Assert.assertNull(smg.getObjectPointedBy(val2));
  }

  @Test
  public void getValuesForObjectTest(){
    Assert.assertEquals(smg.getValuesForObject(obj1).size(), 0);
    Assert.assertEquals(smg.getValuesForObject(obj2).size(), 2);

    Assert.assertEquals(smg.getValuesForObject(obj2, 0).size(), 1);
    Assert.assertTrue(smg.getValuesForObject(obj2, 0).contains(hv2has2at0));
    Assert.assertEquals(smg.getValuesForObject(obj2, 3).size(), 0);
    Assert.assertEquals(smg.getValuesForObject(obj2, 4).size(), 1);
    Assert.assertTrue(smg.getValuesForObject(obj2, 4).contains(hv2has1at4));

    //TODO: Filter by types
  }
}
