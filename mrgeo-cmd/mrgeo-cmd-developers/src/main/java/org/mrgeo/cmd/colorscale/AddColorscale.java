/*
 * Copyright 2009-2017. DigitalGlobe, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mrgeo.cmd.colorscale;

import org.apache.commons.cli.*;
import org.apache.hadoop.conf.Configuration;
import org.mrgeo.cmd.Command;
import org.mrgeo.colorscale.ColorScale;
import org.mrgeo.colorscale.ColorScaleManager;
import org.mrgeo.core.MrGeoConstants;
import org.mrgeo.core.MrGeoProperties;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.image.MrsPyramidMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AddColorscale extends Command
{
private static Logger log = LoggerFactory.getLogger(AddColorscale.class);

//private boolean verbose = false;
//private boolean debug = false;


public AddColorscale()
{
}

private static Options createOptions()
{
  Options result = new Options();

  result.addOption(new Option("l", "list", false, "List existing colorscale"));
  result.addOption(new Option("h", "help", false, "help"));

  return result;
}


@Override
public int run(final String[] args, final Configuration conf,
    final ProviderProperties providerProperties)
{
  log.info("AddColorscale");

  try
  {

    final Options options = AddColorscale.createOptions();
    CommandLine line;

    final CommandLineParser parser = new PosixParser();
    line = parser.parse(options, args);

    String pyramidName = null;
    String colorscale = null;

    boolean list = line.hasOption("l");


    if (line.getArgs().length >= 1)
    {
      pyramidName = line.getArgs()[0];
    }

    if (line.getArgs().length == 2)
    {
      colorscale = line.getArgs()[1];
    }


    if (pyramidName == null || (!list && colorscale == null) || line.hasOption("h"))
    {
      new HelpFormatter().printHelp("colorscale <pyramid> [colorscale name]", options);
      return 1;
    }

    try
    {
      MrsImageDataProvider dp = DataProviderFactory.getMrsImageDataProvider(pyramidName, DataProviderFactory.AccessMode.READ, new ProviderProperties());
      MrsPyramidMetadata meta = dp.getMetadataReader().read();

      if (list)
      {
        String existing = meta.getTag(MrGeoConstants.MRGEO_DEFAULT_COLORSCALE, "[not set]");
        System.out.println("Default colorscale: " + existing);

        return 0;
      }

      meta.setTag(MrGeoConstants.MRGEO_DEFAULT_COLORSCALE, colorscale);
      dp.getMetadataWriter().write(meta);

      ColorScale cs = null;
      try
      {
        cs = ColorScaleManager.fromName(colorscale);
      }
      catch (ColorScale.ColorScaleException ignored)
      {
      }

      if (cs == null)
      {
        System.out.println("The default colorscale was set, however, it doesn't exist in the colorscale directory (" +
            MrGeoProperties.getInstance().getProperty(MrGeoConstants.MRGEO_HDFS_COLORSCALE) + ")");
      }
      else
      {
        System.out.println("Default colorscale set to: " + colorscale);
      }
    }
    catch (IOException ignored)
    {
      System.out.println("Pyramid not found: " + pyramidName);
    }
    return 0;
  }
  catch (ParseException e)
  {
    log.error("Exception thrown", e);
  }

  return -1;
}

}
