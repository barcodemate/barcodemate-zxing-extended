/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/BitMatrixCursor.h (tag v3.1.1,
 * commit 287c85d).
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.common.BitMatrix;

/**
 * A position and a heading inside a {@link BitMatrix}, able to walk in that
 * direction and to find the edges it crosses.
 *
 * <p>This is how the ported 2D detection reads an image. Rather than sampling
 * fixed grid positions, it puts a cursor somewhere it believes a symbol to be
 * and follows the black and white runs outward, which is what makes the
 * detection tolerant of rotation and perspective: no step assumes the symbol is
 * axis aligned.</p>
 *
 * <p>Upstream's class is a template over its point type, instantiated at
 * integer and floating point. The two behave differently in one respect, which
 * the subclasses here carry: {@link BitMatrixCursorI} keeps its direction
 * exactly as given, so it steps whole pixels, while {@link BitMatrixCursorF}
 * scales the direction so the larger component is 1, walking Bresenham style.
 * Positions are held in double precision in both cases, which is exact for the
 * integer one and keeps the two in step numerically.</p>
 *
 * <p>A value read from the matrix is one of {@link #BLACK}, {@link #WHITE} or
 * {@link #INVALID}, the last meaning the position is outside the image. Note
 * that outside counts as <em>different</em> from any colour, so walking off the
 * edge of the image registers as an edge; several of the callers depend on
 * that.</p>
 */
public abstract class BitMatrixCursor {

  public static final int INVALID = -1;
  public static final int WHITE = 0;
  public static final int BLACK = 1;

  /** Which way to turn, relative to the current heading. */
  public enum Direction {
    LEFT(-1),
    RIGHT(1);

    final int value;

    Direction(int value) {
      this.value = value;
    }

    public Direction opposite() {
      return this == LEFT ? RIGHT : LEFT;
    }
  }

  public final BitMatrix image;
  public PointF p;
  public PointF d;

  protected BitMatrixCursor(BitMatrix image, PointF p, PointF d) {
    this.image = image;
    this.p = p;
    this.d = d;
  }

  /** Adopts a new heading, in whatever form this cursor type requires. */
  public abstract void setDirection(PointF dir);

  public abstract BitMatrixCursor movedBy(PointF offset);

  public abstract BitMatrixCursor turnedBack();

  public final boolean isIn(PointF q, int border) {
    return border <= q.x && q.x < image.getWidth() - border
        && border <= q.y && q.y < image.getHeight() - border;
  }

  public final boolean isIn(PointF q) {
    return isIn(q, 0);
  }

  public final boolean isIn() {
    return isIn(p);
  }

  /** {@link #BLACK}, {@link #WHITE}, or {@link #INVALID} when outside the image. */
  public final int testAt(PointF q) {
    return isIn(q) ? (image.get((int) q.x, (int) q.y) ? BLACK : WHITE) : INVALID;
  }

  public final boolean blackAt(PointF q) {
    return testAt(q) == BLACK;
  }

  public final boolean whiteAt(PointF q) {
    return testAt(q) == WHITE;
  }

  public final boolean isBlack() {
    return blackAt(p);
  }

  public final boolean isWhite() {
    return whiteAt(p);
  }

  public final PointF front() {
    return d;
  }

  public final PointF back() {
    return d.negated();
  }

  public final PointF left() {
    return d.left();
  }

  public final PointF right() {
    return d.right();
  }

  public final PointF direction(Direction dir) {
    return right().times(dir.value);
  }

  public final void turnBack() {
    d = back();
  }

  public final void turnLeft() {
    d = left();
  }

  public final void turnRight() {
    d = right();
  }

  public final void turn(Direction dir) {
    d = direction(dir);
  }

  /**
   * The colour at the current position if the neighbour one step along
   * {@code delta} differs from it, otherwise {@link #INVALID}. In other words:
   * the colour of the edge, when there is one.
   */
  public final int edgeAt(PointF delta) {
    int v = testAt(p);
    return testAt(p.plus(delta)) != v ? v : INVALID;
  }

  public final int edgeAtFront() {
    return edgeAt(front());
  }

  public final int edgeAtBack() {
    return edgeAt(back());
  }

  public final int edgeAtLeft() {
    return edgeAt(left());
  }

  public final int edgeAtRight() {
    return edgeAt(right());
  }

  public final int edgeAt(Direction dir) {
    return edgeAt(direction(dir));
  }

  public final boolean step() {
    return step(1);
  }

  public final boolean step(double s) {
    p = p.plus(d.times(s));
    return isIn(p);
  }

  public final int stepToEdge() {
    return stepToEdge(1, 0, false);
  }

  public final int stepToEdge(int nth, int range) {
    return stepToEdge(nth, range, false);
  }

  /**
   * Advances until {@code nth} edges have been crossed.
   *
   * @param nth how many edges to pass
   * @param range the most steps to take, or 0 for no limit
   * @param backup whether to end one step before the edge rather than on it
   * @return the number of steps taken, or 0 if the edges were not found within
   *         range. Note that the cursor moves either way.
   */
  public final int stepToEdge(int nth, int range, boolean backup) {
    int steps = 0;
    int lastValue = testAt(p);

    while (nth > 0 && (range == 0 || steps < range) && lastValue != INVALID) {
      steps++;
      int value = testAt(p.plus(d.times(steps)));
      if (lastValue != value) {
        lastValue = value;
        nth--;
      }
    }
    if (backup) {
      steps--;
    }
    p = p.plus(d.times(steps));
    return nth == 0 ? steps : 0;
  }

  public final boolean stepAlongEdge(Direction dir) {
    return stepAlongEdge(dir, false);
  }

  /**
   * Takes one step while keeping an edge on the given side, turning as needed
   * to stay against it.
   */
  public final boolean stepAlongEdge(Direction dir, boolean skipCorner) {
    if (edgeAt(dir) == INVALID) {
      turn(dir);
    } else if (edgeAtFront() != INVALID) {
      turn(dir.opposite());
      if (edgeAtFront() != INVALID) {
        turn(dir.opposite());
        if (edgeAtFront() != INVALID) {
          return false; // boxed in on every side
        }
      }
    }

    boolean moved = step();

    if (moved && skipCorner && edgeAt(dir) == INVALID) {
      turn(dir);
      moved = step();
    }
    return moved;
  }

  public final int countEdges(int range) {
    int count = 0;
    while (range > 0) {
      int steps = stepToEdge(1, range);
      if (steps == 0) {
        break;
      }
      range -= steps;
      count++;
    }
    return count;
  }

  /**
   * Reads consecutive run lengths into {@code result}, one per edge crossed.
   *
   * @param max the most steps in total, or 0 for no limit
   * @param min keep going in pairs until at least this many steps were taken
   * @return {@code result}, zero-filled from wherever the walk stopped
   */
  public final int[] readPattern(int[] result, int max, int min) {
    java.util.Arrays.fill(result, 0);
    for (int i = 0; i < result.length; i++) {
      result[i] = stepToEdge(1, max);
      if (result[i] == 0) {
        return result;
      }
      if (max != 0) {
        max -= result[i];
      }
    }
    if (min != 0 && max != 0) {
      int total = 0;
      for (int v : result) {
        total += v;
      }
      min -= total;
      int steps = -1;
      while (min > 0 && max != 0 && steps != 0) {
        steps = stepToEdge(2, max);
        max -= steps;
        min -= steps;
      }
    }
    return result;
  }

  public final int[] readPattern(int[] result) {
    return readPattern(result, 0, 0);
  }

  /** As {@link #readPattern}, after first skipping up to that much white. */
  public final int[] readPatternFromBlack(int[] result, int maxWhitePrefix, int max, int min) {
    if (maxWhitePrefix != 0 && isWhite() && stepToEdge(1, maxWhitePrefix) == 0) {
      java.util.Arrays.fill(result, 0);
      return result;
    }
    return readPattern(result, max, min);
  }
}
