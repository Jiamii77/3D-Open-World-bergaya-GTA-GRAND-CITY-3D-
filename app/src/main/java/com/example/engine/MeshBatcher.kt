package com.example.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interleaved vertex layout (10 floats = 40 bytes per vertex):
 * [x, y, z, nx, ny, nz, r, g, b, emissive]
 * Keeps draw calls ultra-low by batching static city blocks into chunk VBOs.
 */
class MeshBuilder(initialCapacityFloats: Int = 6000) {
  private var data = FloatArray(initialCapacityFloats)
  private var size = 0

  val vertexCount: Int get() = size / FLOATS_PER_VERTEX

  private fun ensureCapacity(extraFloats: Int) {
    if (size + extraFloats > data.size) {
      val next = FloatArray((data.size * 2).coerceAtLeast(size + extraFloats))
      System.arraycopy(data, 0, next, 0, size)
      data = next
    }
  }

  fun addVertex(
    x: Float, y: Float, z: Float,
    nx: Float, ny: Float, nz: Float,
    r: Float, g: Float, b: Float,
    emissive: Float = 0f,
  ) {
    ensureCapacity(FLOATS_PER_VERTEX)
    data[size++] = x
    data[size++] = y
    data[size++] = z
    data[size++] = nx
    data[size++] = ny
    data[size++] = nz
    data[size++] = r
    data[size++] = g
    data[size++] = b
    data[size++] = emissive
  }

  fun addQuad(
    x0: Float, y0: Float, z0: Float,
    x1: Float, y1: Float, z1: Float,
    x2: Float, y2: Float, z2: Float,
    x3: Float, y3: Float, z3: Float,
    nx: Float, ny: Float, nz: Float,
    r: Float, g: Float, b: Float,
    emissive: Float = 0f,
  ) {
    addVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive)
    addVertex(x1, y1, z1, nx, ny, nz, r, g, b, emissive)
    addVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive)
    addVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive)
    addVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive)
    addVertex(x3, y3, z3, nx, ny, nz, r, g, b, emissive)
  }

  /**
   * Emits a flat horizontal quad on XZ plane at height y.
   */
  fun addGroundRect(
    cx: Float, cz: Float,
    w: Float, d: Float,
    y: Float,
    r: Float, g: Float, b: Float,
    emissive: Float = 0f,
  ) {
    val hx = w * 0.5f
    val hz = d * 0.5f
    addQuad(
      cx - hx, y, cz - hz,
      cx - hx, y, cz + hz,
      cx + hx, y, cz + hz,
      cx + hx, y, cz - hz,
      0f, 1f, 0f,
      r, g, b,
      emissive,
    )
  }

  /**
   * Emits a 3D box centered horizontally at (cx, cz) with bottom at minY and top at minY + height.
   */
  fun addBox(
    cx: Float, minY: Float, cz: Float,
    w: Float, h: Float, d: Float,
    r: Float, g: Float, b: Float,
    emissive: Float = 0f,
    topShade: Float = 1.06f,
    sideShade: Float = 0.92f,
  ) {
    val x0 = cx - w * 0.5f
    val x1 = cx + w * 0.5f
    val y0 = minY
    val y1 = minY + h
    val z0 = cz - d * 0.5f
    val z1 = cz + d * 0.5f

    val tr = (r * topShade).coerceAtMost(1f)
    val tg = (g * topShade).coerceAtMost(1f)
    val tb = (b * topShade).coerceAtMost(1f)
    val sr = r * sideShade
    val sg = g * sideShade
    val sb = b * sideShade

    // Top (+Y)
    addQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f, tr, tg, tb, emissive)
    // Bottom (-Y)
    addQuad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f, r * 0.6f, g * 0.6f, b * 0.6f, emissive)
    // Front (+Z)
    addQuad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0f, 0f, 1f, r, g, b, emissive)
    // Back (-Z)
    addQuad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0f, 0f, -1f, sr, sg, sb, emissive)
    // Right (+X)
    addQuad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1f, 0f, 0f, sr, sg, sb, emissive)
    // Left (-X)
    addQuad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1f, 0f, 0f, r, g, b, emissive)
  }

  /**
   * Emits a drivable sloped ramp along Z axis (rising from zStart at yStart to zEnd at yEnd).
   */
  fun addRampZ(
    cx: Float, width: Float,
    zStart: Float, yStart: Float,
    zEnd: Float, yEnd: Float,
    thickness: Float,
    r: Float, g: Float, b: Float,
  ) {
    val x0 = cx - width * 0.5f
    val x1 = cx + width * 0.5f
    val dz = zEnd - zStart
    val dy = yEnd - yStart
    val len = sqrt(dz * dz + dy * dy).coerceAtLeast(0.01f)
    val ny = abs(dz) / len
    val nz = if (dz > 0f) -dy / len else dy / len

    // Top drivable surface
    if (zEnd > zStart) {
      addQuad(x0, yStart, zStart, x0, yEnd, zEnd, x1, yEnd, zEnd, x1, yStart, zStart, 0f, ny, nz, r, g, b, 0f)
    } else {
      addQuad(x0, yEnd, zEnd, x0, yStart, zStart, x1, yStart, zStart, x1, yEnd, zEnd, 0f, ny, nz, r, g, b, 0f)
    }
    // Side skirts for solid low-poly look
    addQuad(x0, yStart, zStart, x0, yStart - thickness, zStart, x0, yEnd - thickness, zEnd, x0, yEnd, zEnd, -1f, 0f, 0f, r * 0.75f, g * 0.75f, b * 0.75f, 0f)
    addQuad(x1, yEnd, zEnd, x1, yEnd - thickness, zEnd, x1, yStart - thickness, zStart, x1, yStart, zStart, 1f, 0f, 0f, r * 0.75f, g * 0.75f, b * 0.75f, 0f)
  }

  fun addTree(cx: Float, cz: Float, scale: Float = 1f) {
    addBox(cx, 0f, cz, 0.55f * scale, 2.5f * scale, 0.55f * scale, 0.42f, 0.27f, 0.16f)
    addBox(cx, 2.3f * scale, cz, 2.6f * scale, 1.8f * scale, 2.6f * scale, 0.16f, 0.58f, 0.26f)
    addBox(cx, 3.8f * scale, cz, 1.8f * scale, 1.4f * scale, 1.8f * scale, 0.22f, 0.70f, 0.32f)
  }

  fun addRing(cx: Float, y: Float, cz: Float, radius: Float, thickness: Float, segments: Int, r: Float, g: Float, b: Float, emissive: Float) {
    val inner = (radius - thickness).coerceAtLeast(0.1f)
    val outer = radius
    for (i in 0 until segments) {
      val a0 = (2.0 * PI * i / segments).toFloat()
      val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
      val c0 = cos(a0)
      val s0 = sin(a0)
      val c1 = cos(a1)
      val s1 = sin(a1)
      addQuad(
        cx + c0 * inner, y, cz + s0 * inner,
        cx + c0 * outer, y, cz + s0 * outer,
        cx + c1 * outer, y, cz + s1 * outer,
        cx + c1 * inner, y, cz + s1 * inner,
        0f, 1f, 0f,
        r, g, b,
        emissive,
      )
    }
  }

  fun buildMesh(): GpuMesh {
    val floatCount = size
    val vCount = floatCount / FLOATS_PER_VERTEX
    val buf = ByteBuffer.allocateDirect(floatCount * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
    buf.put(data, 0, floatCount)
    buf.position(0)
    return GpuMesh(buf, vCount)
  }

  companion object {
    const val FLOATS_PER_VERTEX = 10
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
    private fun abs(v: Float) = if (v < 0f) -v else v
  }
}

class GpuMesh(
  private val cpuBuffer: FloatBuffer,
  val vertexCount: Int,
) {
  private var vboId: Int = 0

  fun uploadIfNeeded() {
    if (vboId != 0 || vertexCount == 0) return
    val ids = IntArray(1)
    GLES20.glGenBuffers(1, ids, 0)
    vboId = ids[0]
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
    cpuBuffer.position(0)
    GLES20.glBufferData(
      GLES20.GL_ARRAY_BUFFER,
      vertexCount * MeshBuilder.STRIDE_BYTES,
      cpuBuffer,
      GLES20.GL_STATIC_DRAW,
    )
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  }

  fun bindAndDraw(
    posLoc: Int,
    normalLoc: Int,
    colorLoc: Int,
    emissiveLoc: Int,
  ) {
    if (vertexCount == 0) return
    uploadIfNeeded()
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)

    GLES20.glEnableVertexAttribArray(posLoc)
    GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, MeshBuilder.STRIDE_BYTES, 0)

    GLES20.glEnableVertexAttribArray(normalLoc)
    GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, MeshBuilder.STRIDE_BYTES, 3 * 4)

    GLES20.glEnableVertexAttribArray(colorLoc)
    GLES20.glVertexAttribPointer(colorLoc, 3, GLES20.GL_FLOAT, false, MeshBuilder.STRIDE_BYTES, 6 * 4)

    GLES20.glEnableVertexAttribArray(emissiveLoc)
    GLES20.glVertexAttribPointer(emissiveLoc, 1, GLES20.GL_FLOAT, false, MeshBuilder.STRIDE_BYTES, 9 * 4)

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  }

  fun dispose() {
    if (vboId != 0) {
      GLES20.glDeleteBuffers(1, intArrayOf(vboId), 0)
      vboId = 0
    }
  }
}
