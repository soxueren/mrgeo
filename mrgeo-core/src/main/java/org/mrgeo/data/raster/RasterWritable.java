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

package org.mrgeo.data.raster;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.compress.*;
import org.mrgeo.utils.ByteArrayUtils;

import java.awt.image.*;
import java.io.*;
import java.nio.*;
import java.util.Arrays;

public class RasterWritable implements WritableComparable<RasterWritable>
{
private static final long serialVersionUID = 1L;

private static int HEADERSIZE = 5;

public static long serializeTime = 0;
public static long serializeCnt = 0;
public static long deserializeTime = 0;
public static long deserializeCnt = 0;

private static final Object serializeSync = new Object();
private static final Object deserializeSync = new Object();

private byte[] bytes;

public static class RasterWritableException extends RuntimeException
{

  private static final long serialVersionUID = 1L;
  private final Exception origException;

  public RasterWritableException(final Exception e)
  {
    this.origException = e;
  }

  public RasterWritableException(final String msg)
  {
    final Exception e = new Exception(msg);
    this.origException = e;
  }

  @Override
  public void printStackTrace()
  {
    origException.printStackTrace();
  }
}

public int compareTo(RasterWritable other)
{
  return Arrays.equals(bytes, other.bytes) ? 0 : 1;
}

@Override
public void write(DataOutput out) throws IOException
{
  out.write(bytes.length);
  out.write(bytes);
}

@Override
public void readFields(DataInput in) throws IOException
{
  int len = in.readInt();
  bytes = new byte[len];
  in.readFully(bytes);
}


private enum SampleModelType {
  PIXELINTERLEAVED, BANDED, SINGLEPIXELPACKED, MULTIPIXELPACKED, COMPONENT
}

public RasterWritable()
{
  super();
}

public RasterWritable(final byte[] bytes)
{
  this.bytes = bytes;
}

public RasterWritable(RasterWritable copy)
{
  this.bytes = copy.bytes;
}

// we could use the default serializations here, but instead we'll just do it manually
private void writeObject(ObjectOutputStream stream) throws IOException
{
  stream.writeInt(bytes.length);
  stream.write(bytes, 0, bytes.length);
}

private void readObject(ObjectInputStream stream) throws IOException
{
  int size = stream.readInt();
  bytes = new byte[size];

  stream.readFully(bytes, 0, size);
}

public int getSize()
{
  return bytes.length;
}

public byte[] getBytes()
{
  return bytes;
}

public byte[] copyBytes()
{
  byte[] copy = new byte[bytes.length];
  System.arraycopy(bytes, 0, copy, 0, bytes.length);

  return copy;
}

public static MrGeoRaster toMrGeoRaster(final RasterWritable writable) throws IOException
{
  long starttime = System.currentTimeMillis();
  try
  {
    int version = ByteArrayUtils.getByte(writable.bytes);
    if (version == 0)
    {
      // this is an old MrsPyramid v2 image, read it into a MrGeoRaster
      return RasterWritable.convertFromV2(writable.bytes);
    }
    return MrGeoRaster.createRaster(writable.bytes);
  }
  finally
  {
    synchronized (deserializeSync)
    {
      deserializeCnt++;
      deserializeTime += (System.currentTimeMillis() - starttime);
    }
  }

}

public static RasterWritable toWritable(MrGeoRaster raster) throws IOException
{
  long starttime = System.currentTimeMillis();
  try
  {
    return new RasterWritable(raster.data());
  }
  finally
  {
    synchronized (serializeSync)
    {
      serializeCnt++;
      serializeTime += (System.currentTimeMillis() - starttime);
    }
  }
}

private static MrGeoRaster convertFromV2(byte[] data)
{
  final ByteBuffer rasterBuffer = ByteBuffer.wrap(data);

  final int headersize = (rasterBuffer.getInt() + 1) * 4; // include the header! ( * sizeof(int) )
  final int height = rasterBuffer.getInt();
  final int width = rasterBuffer.getInt();
  final int bands = rasterBuffer.getInt();
  final int datatype = rasterBuffer.getInt();
  final SampleModelType sampleModelType = SampleModelType.values()[rasterBuffer.getInt()];

  MrGeoRaster raster = MrGeoRaster.createEmptyRaster(width, height, bands, datatype);
  switch (sampleModelType)
  {
  case BANDED:
    // this one is easy, just make a new MrGeoRaster and copy the data
    int srclen = data.length - headersize;

    System.arraycopy(data, headersize, raster.data, raster.dataoffset(), srclen);
    break;
  case MULTIPIXELPACKED:
    throw new NotImplementedException("MultiPixelPackedSampleModel not implemented yet");
  case COMPONENT:
  case PIXELINTERLEAVED:
  {
    throw new NotImplementedException("PixelInterleaved and Component not implemented yet");
  }
  case SINGLEPIXELPACKED:
    throw new NotImplementedException("SinglePixelPackedSampleModel not implemented yet");
  default:
    throw new RasterWritableException("Unknown RasterSampleModel type");
  }

  return raster;
}

@Deprecated
public static Raster toRaster(final RasterWritable writable) throws IOException
{
  return toRaster(writable, null);
}

@Deprecated
public static Raster toRaster(final RasterWritable writable, Writable payload) throws IOException
{
  return read(writable.bytes, payload);
}

@Deprecated
public static Raster toRaster(final RasterWritable writable,
    final CompressionCodec codec, final Decompressor decompressor) throws IOException
{
  return toRaster(writable, codec, decompressor, null);
}

@Deprecated
public static Raster toRaster(final RasterWritable writable,
    final CompressionCodec codec, final Decompressor decompressor, Writable payload) throws IOException
{
  decompressor.reset();
  final ByteArrayInputStream bis = new ByteArrayInputStream(writable.bytes, 0, writable.bytes.length);
  final CompressionInputStream gis = codec.createInputStream(bis, decompressor);
  final ByteArrayOutputStream baos = new ByteArrayOutputStream();
  IOUtils.copyBytes(gis, baos, 1024 * 1024 * 2, true);

  return read(baos.toByteArray(), payload);
}

private static SampleModelType toSampleModelType(final SampleModel model)
{
  if (model instanceof PixelInterleavedSampleModel)
  {
    return SampleModelType.PIXELINTERLEAVED;
  }
  if (model instanceof BandedSampleModel)
  {
    return SampleModelType.BANDED;
  }
  if (model instanceof SinglePixelPackedSampleModel)
  {
    return SampleModelType.SINGLEPIXELPACKED;
  }
  if (model instanceof MultiPixelPackedSampleModel)
  {
    return SampleModelType.MULTIPIXELPACKED;
  }
  if (model instanceof ComponentSampleModel)
  {
    return SampleModelType.COMPONENT;
  }

  throw new RasterWritableException("Unknown RasterSampleModel type");
}

@Deprecated
public static RasterWritable toWritable(byte[] data, int width, int height, int bands, int datatype) throws IOException
{
  return toWritable(data, width, height, bands, datatype, null);
}

@Deprecated
public static RasterWritable toWritable(byte[] data, int width, int height, int bands, int datatype, Writable payload) throws IOException
{
  final ByteArrayOutputStream baos = new ByteArrayOutputStream();

  writeHeader(width, height, bands, datatype, baos);
  baos.write(data, 0, data.length);

  if (payload != null)
  {
    writePayload(payload, baos);
  }
  baos.close();
  return new RasterWritable(baos.toByteArray());
}

@Deprecated
public static RasterWritable toWritable(final Raster raster) throws IOException
{
  return toWritable(raster, null);
}

@Deprecated
public static RasterWritable toWritable(final Raster raster, final Writable payload)
    throws IOException
{
  long starttime = System.currentTimeMillis();
  try
  {
    final byte[] pixels = rasterToBytes(raster);
    final ByteArrayInputStream bis = new ByteArrayInputStream(pixels);
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    writeHeader(raster, baos);
    IOUtils.copyBytes(bis, baos, pixels.length, false);

    if (payload != null)
    {
      writePayload(payload, baos);
    }
    bis.close();
    baos.close();
    return new RasterWritable(baos.toByteArray());
  }
  finally
  {
    synchronized (serializeSync)
    {
      serializeCnt++;
      serializeTime += (System.currentTimeMillis() - starttime);
    }
  }
}

@Deprecated
public static RasterWritable toWritable(final Raster raster, final CompressionCodec codec,
    final Compressor compressor) throws IOException
{
  return toWritable(raster, codec, compressor, null);
}

@Deprecated
public static RasterWritable toWritable(final Raster raster, final CompressionCodec codec,
    final Compressor compressor, final Writable payload) throws IOException
{
  compressor.reset();

  final byte[] pixels = rasterToBytes(raster);
  final ByteArrayInputStream bis = new ByteArrayInputStream(pixels);

  final ByteArrayOutputStream baos = new ByteArrayOutputStream();
  final CompressionOutputStream cos = codec.createOutputStream(baos, compressor);

  writeHeader(raster, cos);
  IOUtils.copyBytes(bis, cos, pixels.length, false);

  if (payload != null)
  {
    writePayload(payload, cos);
  }
  bis.close();
  cos.close();
  return new RasterWritable(baos.toByteArray());
}

@Deprecated
private static byte[] rasterToBytes(final Raster raster)
{
  final int datatype = raster.getTransferType();

  byte[] pixels;

  final Object elements = raster.getDataElements(raster.getMinX(), raster.getMinY(),
      raster.getWidth(), raster.getHeight(), null);

  switch (datatype)
  {
  case DataBuffer.TYPE_BYTE:
  {
    pixels = (byte[]) elements;
    break;
  }
  case DataBuffer.TYPE_FLOAT:
  {
    final float[] floatElements = (float[]) elements;

    pixels = new byte[floatElements.length * RasterUtils.FLOAT_BYTES];

    final ByteBuffer bytebuff = ByteBuffer.wrap(pixels);
    final FloatBuffer floatbuff = bytebuff.asFloatBuffer();
    floatbuff.put(floatElements);

    break;
  }
  case DataBuffer.TYPE_DOUBLE:
  {
    final double[] doubleElements = (double[]) elements;

    pixels = new byte[doubleElements.length * RasterUtils.DOUBLE_BYTES];

    final ByteBuffer bytebuff = ByteBuffer.wrap(pixels);
    final DoubleBuffer doubleBuff = bytebuff.asDoubleBuffer();
    doubleBuff.put(doubleElements);

    break;
  }
  case DataBuffer.TYPE_INT:
  {
    final int[] intElements = (int[]) elements;

    pixels = new byte[intElements.length * RasterUtils.INT_BYTES];

    final ByteBuffer bytebuff = ByteBuffer.wrap(pixels);
    final IntBuffer intBuff = bytebuff.asIntBuffer();
    intBuff.put(intElements);

    break;
  }
  case DataBuffer.TYPE_SHORT:
  case DataBuffer.TYPE_USHORT:
  {
    final short[] shortElements = (short[]) elements;

    pixels = new byte[shortElements.length * RasterUtils.SHORT_BYTES];

    final ByteBuffer bytebuff = ByteBuffer.wrap(pixels);
    final ShortBuffer shortbuff = bytebuff.asShortBuffer();
    shortbuff.put(shortElements);

    break;
  }
  default:
    throw new RasterWritableException("Error trying to append raster.  Bad raster data type");
  }

  return pixels;
}

@Deprecated
public static byte[] toBytes(Raster raster, Writable payload) throws IOException
{
  return RasterWritable.toWritable(raster, payload).bytes;
}

@Deprecated
public static Raster toRaster(final byte[] rasterBytes, Writable payload) throws IOException
{
  return read(rasterBytes, payload);
}

@Deprecated
private static Raster read(final byte[] rasterBytes, Writable payload)
    throws IOException
{
  long starttime = System.currentTimeMillis();
  try
  {
    WritableRaster raster;

    final ByteBuffer rasterBuffer = ByteBuffer.wrap(rasterBytes);

  /*final int headersize =*/
    rasterBuffer.getInt(); // this isn't really used anymore...
    final int height = rasterBuffer.getInt();
    final int width = rasterBuffer.getInt();
    final int bands = rasterBuffer.getInt();
    final int datatype = rasterBuffer.getInt();
    final SampleModelType sampleModelType = SampleModelType.values()[rasterBuffer.getInt()];

    SampleModel model;
    switch (sampleModelType)
    {
    case BANDED:
      model = new BandedSampleModel(datatype, width, height, bands);
      break;
    case MULTIPIXELPACKED:
      throw new NotImplementedException("MultiPixelPackedSampleModel not implemented yet");
      // model = new MultiPixelPackedSampleModel(dataType, w, h, numberOfBits)
    case PIXELINTERLEAVED:
    {
      final int pixelStride = rasterBuffer.getInt();
      final int scanlineStride = rasterBuffer.getInt();
      final int bandcnt = rasterBuffer.getInt();
      final int[] bandOffsets = new int[bandcnt];
      for (int i = 0; i < bandcnt; i++)
      {
        bandOffsets[i] = rasterBuffer.getInt();
      }
      model = new PixelInterleavedSampleModel(datatype, width, height, pixelStride, scanlineStride,
          bandOffsets);
      break;
    }
    case SINGLEPIXELPACKED:
      throw new NotImplementedException("SinglePixelPackedSampleModel not implemented yet");
      // model = new SinglePixelPackedSampleModel(dataType, w, h, bitMasks);
    case COMPONENT:
    {
      final int pixelStride = rasterBuffer.getInt();
      final int scanlineStride = rasterBuffer.getInt();
      final int bandcnt = rasterBuffer.getInt();
      final int[] bandOffsets = new int[bandcnt];
      for (int i = 0; i < bandcnt; i++)
      {
        bandOffsets[i] = rasterBuffer.getInt();
      }
      model = new ComponentSampleModel(datatype, width, height, pixelStride, scanlineStride,
          bandOffsets);
      break;
    }
    default:
      throw new RasterWritableException("Unknown RasterSampleModel type");
    }

    // include the header size param in the count
    int startdata = rasterBuffer.position();

    // calculate the data size
    int[] samplesize = model.getSampleSize();
    int samplebytes = 0;
    for (int ss : samplesize)
    {
      // bits to bytes
      samplebytes += (ss / 8);
    }
    int databytes = model.getHeight() * model.getWidth() * samplebytes;

    // final ByteBuffer rasterBuffer = ByteBuffer.wrap(rasterBytes, headerbytes, databytes);
    // the corner of the raster is always 0,0
    raster = Raster.createWritableRaster(model, null);

    switch (datatype)
    {
    case DataBuffer.TYPE_BYTE:
    {
      // we can't use the byte buffer explicitly because the header info is
      // still in it...
      final byte[] bytedata = new byte[databytes];
      rasterBuffer.get(bytedata);

      raster.setDataElements(0, 0, width, height, bytedata);
      break;
    }
    case DataBuffer.TYPE_FLOAT:
    {
      final FloatBuffer floatbuff = rasterBuffer.asFloatBuffer();
      final float[] floatdata = new float[databytes / RasterUtils.FLOAT_BYTES];

      floatbuff.get(floatdata);

      raster.setDataElements(0, 0, width, height, floatdata);
      break;
    }
    case DataBuffer.TYPE_DOUBLE:
    {
      final DoubleBuffer doublebuff = rasterBuffer.asDoubleBuffer();
      final double[] doubledata = new double[databytes / RasterUtils.DOUBLE_BYTES];

      doublebuff.get(doubledata);

      raster.setDataElements(0, 0, width, height, doubledata);

      break;
    }
    case DataBuffer.TYPE_INT:
    {
      final IntBuffer intbuff = rasterBuffer.asIntBuffer();
      final int[] intdata = new int[databytes / RasterUtils.INT_BYTES];

      intbuff.get(intdata);

      raster.setDataElements(0, 0, width, height, intdata);

      break;
    }
    case DataBuffer.TYPE_SHORT:
    case DataBuffer.TYPE_USHORT:
    {
      final ShortBuffer shortbuff = rasterBuffer.asShortBuffer();
      final short[] shortdata = new short[databytes / RasterUtils.SHORT_BYTES];
      shortbuff.get(shortdata);
      raster.setDataElements(0, 0, width, height, shortdata);
      break;
    }
    default:
      throw new RasterWritableException("Error trying to read raster.  Bad raster data type");
    }

    // should we even try to extract the payload?
    if (payload != null)
    {
      // test to see if this is a raster with a possible payload
      final int payloadStart = startdata + databytes;
      if (rasterBytes.length > payloadStart)
      {
        // extract the payload
        final ByteArrayInputStream bais =
            new ByteArrayInputStream(rasterBytes, payloadStart, rasterBytes.length - payloadStart);
        final DataInputStream dis = new DataInputStream(bais);
        payload.readFields(dis);
      }
    }
    return raster;
  }
  finally
  {
    synchronized (deserializeSync)
    {
      deserializeCnt++;
      deserializeTime += (System.currentTimeMillis() - starttime);
    }
  }
}

@Deprecated
private static void writeHeader(int width, int height, int bands, int datatype, OutputStream out) throws IOException
{
  final DataOutputStream dos = new DataOutputStream(out);

  dos.writeInt(HEADERSIZE);
  dos.writeInt(height);
  dos.writeInt(width);
  dos.writeInt(bands);
  dos.writeInt(datatype);

  dos.writeInt(SampleModelType.BANDED.ordinal());
}

@Deprecated
private static void writeHeader(final Raster raster, final OutputStream out) throws IOException
{
  final DataOutputStream dos = new DataOutputStream(out);

  int headersize = HEADERSIZE;
  // this is in integers!
  // MAKE SURE TO KEEP THIS CORRECT IF YOU ADD PARAMETERS TO THE HEADER!!!

  final SampleModel model = raster.getSampleModel();
  final SampleModelType modeltype = toSampleModelType(model);

  int[] bandOffsets = null;
  switch (modeltype)
  {
  case BANDED:
    break;
  case PIXELINTERLEAVED:
  case COMPONENT:
    bandOffsets = ((ComponentSampleModel) model).getBandOffsets();

    // add pixel-stride, scanline-stride, band offset count, & band offsets to
    // the header count
    headersize += 3 + bandOffsets.length;
    break;
  case MULTIPIXELPACKED:
    break;
  case SINGLEPIXELPACKED:
    break;
  default:
  }

  dos.writeInt(headersize);
  dos.writeInt(raster.getHeight());
  dos.writeInt(raster.getWidth());
  dos.writeInt(raster.getNumBands());
  dos.writeInt(raster.getTransferType());

  dos.writeInt(modeltype.ordinal());

  switch (modeltype)
  {
  case BANDED:
    break;
  case COMPONENT:
  case PIXELINTERLEAVED:
  {
    final ComponentSampleModel pism = (ComponentSampleModel) model;
    dos.writeInt(pism.getPixelStride());
    dos.writeInt(pism.getScanlineStride());

    dos.writeInt(bandOffsets.length);
    for (final int bandOffset : bandOffsets)
    {
      dos.writeInt(bandOffset);
    }
  }
  break;
  case MULTIPIXELPACKED:
    break;
  case SINGLEPIXELPACKED:
    break;
  default:
  }

}

@Deprecated
private static void writePayload(final Writable payload, final OutputStream out) throws IOException
{
  final DataOutputStream dos = new DataOutputStream(out);
  payload.write(dos);
}
}
