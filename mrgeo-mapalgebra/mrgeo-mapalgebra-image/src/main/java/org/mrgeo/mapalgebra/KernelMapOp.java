package org.mrgeo.mapalgebra;

import org.apache.hadoop.conf.Configuration;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.mapalgebra.parser.ParserAdapter;
import org.mrgeo.mapalgebra.parser.ParserNode;
import org.mrgeo.mapreduce.job.JobCancelledException;
import org.mrgeo.mapreduce.job.JobFailedException;
import org.mrgeo.opimage.MrsPyramidDescriptor;
import org.mrgeo.progress.Progress;
import org.mrgeo.spark.KernelDriver;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

public class KernelMapOp extends RasterMapOp implements InputsCalculator
{
final public static String Gaussian = "gaussian";
final public static String Laplacian = "laplacian";

final private static double EPSILON = 1e-8;

private String method;

// gaussian & laplacian
private Double sigma;

// triangular
private Double min;
private Double max;
private Double mode;
private Double bin;


public static String[] register()
{
  return new String[] { "kernel" };
}

@Override
public void addInput(MapOp n) throws IllegalArgumentException
{
  if (!(n instanceof RasterMapOp))
  {
    throw new IllegalArgumentException("Can only apply kernel to raster inputs");
  }

  if (_inputs.size() >= 1)
  {
    throw new IllegalArgumentException("Can only run kernel on a single raster input");
  }

  _inputs.add(n);
}

@Override
public void build(Progress p) throws IOException, JobFailedException, JobCancelledException
{
  p.starting();

  // check that we haven't already calculated ourselves
  if (_output == null)
  {
    String input = null;
    for (MapOp in : _inputs)
    {
      input = ((RasterMapOp) (in)).getOutputName();
    }

    String output = getOutputName();
    Configuration conf = createConfiguration();

    switch (method.toLowerCase())
    {
    case Gaussian:
      KernelDriver.gaussian(input, output, sigma, conf);
      break;
    case Laplacian:
      KernelDriver.laplacian(input, output, sigma, conf);
      break;
    }
    MrsImageDataProvider dp = DataProviderFactory.getMrsImageDataProvider(output,
        DataProviderFactory.AccessMode.READ, getProviderProperties());
    _output = MrsPyramidDescriptor.create(dp);
  }

  p.complete();
}

@Override
public Vector<ParserNode> processChildren(Vector<ParserNode> children, ParserAdapter parser)
{
  if (children.size() < 3)
  {
    throw new IllegalArgumentException("Usage: kernel(<method>, <raster>, <params ...>)");
  }

  method = parseChildString(children.get(0), "method", parser);

  Vector<ParserNode> results = new Vector<>();
  results.add(children.get(1));

  switch (method.toLowerCase()) {
  case Gaussian:
  case Laplacian:
    if (children.size() != 3)
    {
      throw new IllegalArgumentException(
          method + " takes two additional arguments. (source raster, and sigma (in meters))");
    }

    sigma = parseChildDouble(children.get(2), "sigma", parser);
    break;
  }
  return results;
}
@Override
public Set<String> calculateInputs()
{
  Set<String> inputPyramids = new HashSet<>();
  if (_outputName != null)
  {
    inputPyramids.add(_outputName);
  }
  return inputPyramids;
}

@Override
public String toString()
{
  return "kernel";
}
}