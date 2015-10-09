/*
 * Copyright 2009-2015 DigitalGlobe, Inc.
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
 */

package org.mrgeo.mapalgebra.binarymath

import org.mrgeo.mapalgebra.parser.ParserNode
import org.mrgeo.mapalgebra.raster.RasterMapOp
import org.mrgeo.mapalgebra.{MapOpRegistrar, MapOp}

object XOrMapOp extends MapOpRegistrar {
  override def register: Array[String] = {
    Array[String]("xor")
  }
  override def apply(node:ParserNode, variables: String => Option[ParserNode]): MapOp =
    new XOrMapOp(node, variables)
}

class XOrMapOp extends RawBinaryMathMapOp {

  private[binarymath] def this(node:ParserNode, variables: String => Option[ParserNode]) = {
    this()

    initialize(node, variables)
  }

  override private[binarymath] def function(a: Double, b: Double): Double = {
    if ((a < -RasterMapOp.EPSILON || a > RasterMapOp.EPSILON) == (b < -RasterMapOp.EPSILON || b > RasterMapOp.EPSILON)) {
      0
    }
    else {
      1
    }
  }
}
