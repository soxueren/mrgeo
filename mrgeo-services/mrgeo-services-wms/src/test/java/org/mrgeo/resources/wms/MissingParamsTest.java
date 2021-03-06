/*
 * Copyright 2009-2016 DigitalGlobe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 */

package org.mrgeo.resources.wms;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mrgeo.core.MrGeoConstants;
import org.mrgeo.junit.IntegrationTest;
import org.mrgeo.test.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

@SuppressWarnings("all") // Test code, not included in production
public class MissingParamsTest extends WmsGeneratorTestAbstract
{
@SuppressWarnings("unused")
private static final Logger log =
    LoggerFactory.getLogger(MissingParamsTest.class);

@BeforeClass
public static void setUpForJUnit()
{
  try
  {
    baselineInput = TestUtils.composeInputDir(MissingParamsTest.class);
    WmsGeneratorTestAbstract.setUpForJUnit();
  }
  catch (Exception e)
  {
    e.printStackTrace();
  }
}

@Test
@Category(IntegrationTest.class)
public void testGetMapMissingParams() throws Exception
{
  String[] paramNames =
      new String[]
          {
              "FORMAT",
              "BBOX",
              "LAYERS",
              "WIDTH",
              "HEIGHT"
          };
  for (int i = 0; i < paramNames.length; i++)
  {
    testGetMapMissingParam(paramNames[i]);
  }
}

@Test
@Category(IntegrationTest.class)
public void testGetMosaicMissingParams() throws Exception
{
  String[] paramNames =
      new String[]
          {
              "FORMAT",
              "BBOX",
              "LAYERS"
          };
  for (int i = 0; i < paramNames.length; i++)
  {
    testGetMosaicMissingParam(paramNames[i]);
  }
}

@Test
@Category(IntegrationTest.class)
public void testGetTileMissingParams() throws Exception
{
  String[] paramNames =
      new String[]
          {
              "FORMAT",
              "LAYER",
              "TILEROW",
              "TILECOL",
              "SCALE"
          };
  for (int i = 0; i < paramNames.length; i++)
  {
    testGetTileMissingParam(paramNames[i]);
  }
}

private void testGetMapMissingParam(String paramName) throws Exception
{
  WebTarget webResource = target().path("/wms")
      .queryParam("SERVICE", "WMS")
      .queryParam("REQUEST", "getmap");
  if (!paramName.equals("LAYERS"))
  {
    webResource = webResource.queryParam("LAYERS", "IslandsElevation-v2");
  }
  if (!paramName.equals("FORMAT"))
  {
    webResource = webResource.queryParam("FORMAT", "image/png");
  }
  if (!paramName.equals("BBOX"))
  {
    webResource = webResource.queryParam("BBOX", "160.312500,-11.250000,161.718750,-9.843750");
  }
  if (!paramName.equals("WIDTH"))
  {
    webResource = webResource.queryParam("WIDTH", MrGeoConstants.MRGEO_MRS_TILESIZE_DEFAULT);
  }
  if (!paramName.equals("HEIGHT"))
  {
    webResource = webResource.queryParam("HEIGHT", MrGeoConstants.MRGEO_MRS_TILESIZE_DEFAULT);
  }

  Response response = webResource.request().get();
  processXMLResponse(response, "testGetMapMissingParam" + paramName + ".xml", Response.Status.BAD_REQUEST);
}

private void testGetMosaicMissingParam(String paramName) throws Exception
{
  WebTarget webResource = target().path("/wms")
      .queryParam("SERVICE", "WMS")
      .queryParam("REQUEST", "getmosaic");
  if (!paramName.equals("LAYERS"))
  {
    webResource = webResource.queryParam("LAYERS", "IslandsElevation-v2");
  }
  if (!paramName.equals("FORMAT"))
  {
    webResource = webResource.queryParam("FORMAT", "image/png");
  }
  if (!paramName.equals("BBOX"))
  {
    webResource = webResource.queryParam("BBOX", "160.312500,-11.250000,161.718750,-9.843750");
  }

  Response response = webResource.request().get();

  processXMLResponse(response, "testGetMosaicMissingParam" + paramName + ".xml", Response.Status.BAD_REQUEST);
}

private void testGetTileMissingParam(String paramName) throws Exception
{
  WebTarget webResource = target().path("/wms")
      .queryParam("SERVICE", "WMS")
      .queryParam("REQUEST", "gettile");
  if (!paramName.equals("LAYER"))
  {
    webResource = webResource.queryParam("LAYER", "IslandsElevation-v2");
  }
  if (!paramName.equals("FORMAT"))
  {
    webResource = webResource.queryParam("FORMAT", "image/tif");
  }
  if (!paramName.equals("TILEROW"))
  {
    webResource = webResource.queryParam("TILEROW", "224");
  }
  if (!paramName.equals("TILECOL"))
  {
    webResource = webResource.queryParam("TILECOL", "970");
  }
  if (!paramName.equals("SCALE"))
  {
    webResource = webResource.queryParam("SCALE", "272989.38673277234");
  }

  Response response = webResource.request().get();

  processXMLResponse(response, "testGetTileMissingParam" + paramName + ".xml", Response.Status.BAD_REQUEST);
}
}
