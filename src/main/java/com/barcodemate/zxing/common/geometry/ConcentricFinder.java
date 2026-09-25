/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/ConcentricFinder.cpp and
 * ConcentricFinder.h (tag v3.1.1, commit 287c85d).
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.common.BitMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locates concentric patterns -- a black square inside a white ring inside a
 * black ring -- and pins down their centre and corners to sub-pixel accuracy.
 *
 * <p>This is the part ZXing for Java has no counterpart for, and the reason the
 * whole geometry layer had to be ported. ZXing's own QR detector finds three
 * finder patterns by scanning rows for a 1:1:3:1:1 run and then fits a
 * perspective transform through their centres. rMQR has <em>one</em> finder
 * pattern, and Micro QR also has one, so there are no three centres to fit
 * anything through. Instead the single pattern has to give up everything by
 * itself: its exact centre, its size, and the four corners that establish the
 * symbol's orientation and perspective.</p>
 *
 * <p>Getting that from one pattern needs more than a row scan. The centre is
 * refined by walking the edge of each ring all the way around and averaging,
 * and the corners by fitting straight lines to the four sides of the ring and
 * intersecting them. Both are why {@link BitMatrixCursor} and
 * {@link RegressionLine} exist.</p>
 */
public final class ConcentricFinder {

  private ConcentricFinder() {
  }

  /**
   * Walks outward averaging the positions of the first {@code numOfEdges}
   * edges, giving a sub-pixel estimate of where the pattern's edge really is.
   */
  static ConcentricPattern averageEdgePixels(BitMatrixCursorI cur, int range, int numOfEdges) {
    PointF sum = PointF.ZERO;
    double totalSteps = 0;
    for (int i = 0; i < numOfEdges; i++) {
      int steps = cur.stepToEdge(1, (int) (range - totalSteps));
      if (steps == 0) {
        return null;
      }
      totalSteps += steps;
      // The edge lies between the last pixel of one colour and the first of
      // the next, so average the centres of both.
      sum = sum.plus(centered(cur.p)).plus(centered(cur.p.plus(cur.back())));
    }
    return new ConcentricPattern(sum.dividedBy(2 * numOfEdges), totalSteps);
  }

  /**
   * Averages the edges found along all four axes through a point, in both
   * directions, rejecting the candidate if opposite legs disagree about their
   * length.
   */
  static ConcentricPattern centerOfDoubleCross(BitMatrix image, PointI center, int width, int numOfEdges) {
    PointF sumP = PointF.ZERO;
    double sumS = 0;
    PointI[] directions = {new PointI(0, 1), new PointI(1, 0), new PointI(1, 1), new PointI(1, -1)};
    for (PointI d : directions) {
      ConcentricPattern first = averageEdgePixels(
          new BitMatrixCursorI(image, center, d), width * 3 / 5, numOfEdges);
      ConcentricPattern second = averageEdgePixels(
          new BitMatrixCursorI(image, center, d.negated()), width * 3 / 5, numOfEdges);
      if (first == null || second == null) {
        return null;
      }
      double smaller = Math.min(first.size, second.size);
      double larger = Math.max(first.size, second.size);
      if (larger > 1.2 * smaller + 1) { // the two legs must be nearly the same length
        return null;
      }
      sumP = sumP.plus(first.center).plus(second.center);
      sumS += first.size + second.size;
    }
    return new ConcentricPattern(sumP.dividedBy(8), 2 * sumS / 8);
  }

  /** A square of mean radius r is about 1.74*r wide. */
  private static double meanRadiusToSquareWidth(double r) {
    return 1.74 * r;
  }

  public static ConcentricPattern centerOfRing(BitMatrix image, PointI center, int width, int nth) {
    return centerOfRing(image, center, width, nth, true);
  }

  /**
   * Follows the {@code nth} ring around the centre all the way round and
   * averages it.
   *
   * <p>Walking the ring rather than sampling a few rows is what makes the
   * centre accurate enough to work from: every edge pixel of the ring
   * contributes. It also gives a cheap sanity check, since a ring that really
   * surrounds the centre must be seen in all eight directions from it.</p>
   *
   * @param nth which edge outward to follow; negative to follow it from inside
   * @param requireCircle whether to insist the ring closes around the centre
   *        and is reasonably regular
   */
  public static ConcentricPattern centerOfRing(BitMatrix image, PointI center, int width, int nth,
                                               boolean requireCircle) {
    final int maxN = 4 * (width + 1) * 3 / 2; // an upper bound on the ring's circumference
    final int maxR = requireCircle ? width + 1 : 2 * width;
    boolean inner = nth < 0;
    nth = Math.abs(nth);

    BitMatrixCursorI cur = new BitMatrixCursorI(image, center, new PointI(1, 0));
    if (cur.stepToEdge(nth, maxR, inner) == 0) {
      return null;
    }
    cur.turnRight(); // go clockwise, keeping the edge on one side
    BitMatrixCursor.Direction edgeDir = inner
        ? BitMatrixCursor.Direction.LEFT : BitMatrixCursor.Direction.RIGHT;

    int neighbourMask = 0;
    PointF start = cur.p;
    PointF sumP = PointF.ZERO;
    double sumR = 0;
    int nP = 0;

    do {
      sumP = sumP.plus(centered(cur.p));
      nP++;

      // Record which of the eight directions from the centre this pixel lies
      // in; a closed ring will eventually have seen all eight.
      neighbourMask |= 1 << (4 + toPointI(cur.p).minus(center).bresenhamDirection().dot(new PointI(1, 3)));

      if (!cur.stepAlongEdge(edgeDir)) {
        return null;
      }

      // Half a pixel takes the radius from the pixel centre to its edge.
      double r = cur.p.distance(center.toPointF()) + (inner ? 0.5 : -0.5);
      sumR += r;

      if (r > maxR || center.toPointF().equals(cur.p) || nP > maxN) {
        return null;
      }
    } while (!cur.p.equals(start));

    if (requireCircle && neighbourMask != 0b111101111) {
      return null;
    }

    PointF meanP = sumP.dividedBy(nP);
    double meanR = sumR / nP;
    // Edge pixels per unit of radius: about 2*pi for a circle, 8 for a square.
    // Much more than that means the edge is ragged rather than a real ring.
    double c = nP / meanR;
    double centerMove = meanP.distance(center.toPointF()) / width;

    if (requireCircle && (c > 12 || centerMove > 0.5)) {
      return null;
    }
    return new ConcentricPattern(meanP, meanRadiusToSquareWidth(meanR));
  }

  /** Averages the centres of several successive rings. */
  public static ConcentricPattern centerOfRings(BitMatrix image, PointF center, int range, int numOfRings) {
    int n = 1;
    double size = 0;
    PointF sum = center;
    for (int i = 2; i < numOfRings + 1; i++) {
      ConcentricPattern ring = centerOfRing(image, new PointI(center), range, i);
      if (ring == null) {
        if (n == 1) {
          return null;
        }
        break;
      } else if (ring.center.distance(center) > range / numOfRings / 2.0) {
        return null;
      }
      sum = sum.plus(ring.center);
      size = ring.size;
      n++;
    }
    return new ConcentricPattern(sum.dividedBy(n), n == numOfRings ? size : 0);
  }

  /** Every edge pixel of one ring, in order around it. */
  static List<PointF> collectRingPoints(BitMatrix image, PointF center, int width, int edgeIndex, boolean backup) {
    PointI centerI = new PointI(center);
    final int maxN = 4 * (width + 1) * 3 / 2;
    final int maxR = width * 2;

    BitMatrixCursorI cur = new BitMatrixCursorI(image, centerI, new PointI(1, 0));
    if (cur.stepToEdge(edgeIndex, maxR, backup) == 0) {
      return Collections.emptyList();
    }
    cur.turnRight();
    BitMatrixCursor.Direction edgeDir = backup
        ? BitMatrixCursor.Direction.LEFT : BitMatrixCursor.Direction.RIGHT;

    int neighbourMask = 0;
    PointF start = cur.p;
    List<PointF> points = new ArrayList<>(maxN / 2);

    do {
      points.add(centered(cur.p));
      neighbourMask |= 1 << (4 + toPointI(cur.p).minus(centerI).bresenhamDirection().dot(new PointI(1, 3)));

      if (!cur.stepAlongEdge(edgeDir)) {
        return Collections.emptyList();
      }
      if (cur.p.distance(centerI.toPointF()) > maxR || centerI.toPointF().equals(cur.p)
          || points.size() > maxN) {
        return Collections.emptyList();
      }
    } while (!cur.p.equals(start));

    if (neighbourMask != 0b111101111) {
      return Collections.emptyList();
    }
    return points;
  }

  /**
   * Fits a quadrilateral to points collected around a ring, by finding its four
   * corners and intersecting lines fitted to the four sides between them.
   *
   * <p>Fitting each side to all of its points, rather than taking the corners
   * as found, is what gives sub-pixel corners: individual edge pixels are
   * quantised, a line through dozens of them is not.</p>
   */
  static Quadrilateral fitQuadrilateralToPoints(PointF center, List<PointF> points) {
    int n = points.size();
    int nearest = 0;
    int farthest = 0;
    for (int i = 0; i < n; i++) {
      if (points.get(i).distance(center) < points.get(nearest).distance(center)) {
        nearest = i;
      }
      // >= not >: std::minmax_element returns the *last* maximum, unlike
      // std::max_element which returns the first. On a square ring all four
      // corners are equidistant from the centre, so this decides which corner
      // the quadrilateral starts at -- picking the wrong one rotates the whole
      // result half a turn.
      if (points.get(i).distance(center) >= points.get(farthest).distance(center)) {
        farthest = i;
      }
    }

    // A square's nearest and farthest ring points differ by about 0.7; a
    // circle's by 1. Too round means this is not a square's outline.
    if (points.get(nearest).distance(center) / points.get(farthest).distance(center) > 0.85) {
      return null;
    }

    // Start at a corner: the point furthest from the centre.
    Collections.rotate(points, -farthest);

    int[] corners = new int[4];
    corners[0] = 0;
    // The opposite corner is the furthest point roughly half way round.
    corners[2] = indexOfMaxDistanceToCenter(points, center, n * 3 / 8, n * 5 / 8);
    // The other two are the points furthest from the long diagonal.
    RegressionLine diagonal = new RegressionLine(points.get(corners[0]), points.get(corners[2]));
    corners[1] = indexOfMaxDistanceToLine(points, diagonal, n * 1 / 8, n * 3 / 8);
    corners[3] = indexOfMaxDistanceToLine(points, diagonal, n * 5 / 8, n * 7 / 8);

    int[] begin = {corners[0] + 1, corners[1] + 1, corners[2] + 1, corners[3] + 1};
    int[] end = {corners[1], corners[2], corners[3], n};

    RegressionLine[] lines = new RegressionLine[4];
    for (int i = 0; i < 4; i++) {
      if (begin[i] > end[i]) {
        return null;
      }
      lines[i] = RegressionLine.through(points, begin[i], end[i]);
      if (!lines[i].isValid()) {
        return null;
      }
    }

    // Every point on a side must actually lie near the line fitted to it,
    // otherwise this ring is not a quadrilateral at all.
    for (int i = 0; i < 4; i++) {
      int len = end[i] - begin[i];
      if (len <= 3) {
        continue;
      }
      double tolerance = Math.max(1.0, Math.min(8.0, len / 8.0));
      for (int j = begin[i]; j < end[i]; j++) {
        if (lines[i].distance(points.get(j)) > tolerance) {
          return null;
        }
      }
    }

    PointF[] q = new PointF[4];
    for (int i = 0; i < 4; i++) {
      q[i] = RegressionLine.intersect(lines[i], lines[(i + 1) % 4]);
    }
    return new Quadrilateral(q[0], q[1], q[2], q[3]);
  }

  private static int indexOfMaxDistanceToCenter(List<PointF> points, PointF center, int from, int to) {
    int best = from;
    double bestDistance = -1;
    for (int i = from; i < to; i++) {
      double d = points.get(i).distance(center);
      if (d > bestDistance) { // strictly greater, so ties keep the first
        bestDistance = d;
        best = i;
      }
    }
    return best;
  }

  private static int indexOfMaxDistanceToLine(List<PointF> points, RegressionLine line, int from, int to) {
    int best = from;
    double bestDistance = -1;
    for (int i = from; i < to; i++) {
      double d = line.distance(points.get(i));
      if (d > bestDistance) {
        bestDistance = d;
        best = i;
      }
    }
    return best;
  }

  private static boolean isPlausibleSquare(Quadrilateral q, int lineIndex) {
    double min = q.get(0).distance(q.get(3));
    double max = min;
    for (int i = 1; i < 4; i++) {
      double side = q.get(i - 1).distance(q.get(i));
      min = Math.min(min, side);
      max = Math.max(max, side);
    }
    return min >= lineIndex * 2 && min > max / 3;
  }

  /** The quadrilateral traced by one ring of a concentric pattern. */
  public static Quadrilateral fitSquareToPoints(BitMatrix image, PointF center, int width,
                                                int lineIndex, boolean backup) {
    List<PointF> points = collectRingPoints(image, center, width, lineIndex, backup);
    if (points.isEmpty()) {
      return null;
    }
    Quadrilateral fitted = fitQuadrilateralToPoints(center, points);
    if (fitted == null || !isPlausibleSquare(fitted, lineIndex - (backup ? 1 : 0))) {
      return null;
    }
    return fitted;
  }

  /**
   * The four corners of a concentric pattern, averaged from the inside and the
   * outside of the same ring so that ink spread cancels rather than biasing
   * them outward.
   */
  public static Quadrilateral findConcentricPatternCorners(BitMatrix image, PointF center, int width, int ringIndex) {
    Quadrilateral inner = fitSquareToPoints(image, center, width, ringIndex, false);
    if (inner == null) {
      return null;
    }
    Quadrilateral outer = fitSquareToPoints(image, center, width, ringIndex + 1, true);
    if (outer == null) {
      return null;
    }
    return inner.blend(outer);
  }

  /**
   * Refines a rough centre into an accurate one, insisting on finding the
   * ring structure a concentric pattern must have.
   */
  public static ConcentricPattern finetuneConcentricPatternCenter(BitMatrix image, PointF center,
                                                                 int width, int finderPatternSize) {
    // There must be at least one closed white ring around the centre.
    ConcentricPattern first = centerOfRing(image, new PointI(center), width * 2 / 3, 1);
    if (first == null || !isBlack(image, first.center)) {
      return null;
    }
    // And then either more rings around that,
    ConcentricPattern rings = centerOfRings(image, first.center, width, finderPatternSize / 2);
    if (rings != null && isBlack(image, rings.center)) {
      // centerOfRings only measures the white ring, which is 5/7 of the whole.
      return rings.withSize(rings.size * 7 / 5);
    }
    // or a centre that at least looks like a square from every direction.
    ConcentricPattern cross = centerOfDoubleCross(image, new PointI(first.center), width,
        finderPatternSize / 2 + 1);
    if (cross != null && isBlack(image, cross.center)) {
      return cross;
    }
    return null;
  }

  private static boolean isBlack(BitMatrix image, PointF p) {
    int x = (int) p.x;
    int y = (int) p.y;
    return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && image.get(x, y);
  }

  private static PointF centered(PointF p) {
    return new PointF(p.x + 0.5, p.y + 0.5);
  }

  private static PointI toPointI(PointF p) {
    return new PointI((int) p.x, (int) p.y);
  }

  // ---------------------------------------------------------------------------
  // The parts upstream keeps in ConcentricFinder.h as templates.
  // ---------------------------------------------------------------------------

  /**
   * Where the centre of a symmetric pattern is, given where its last element
   * ends. Averages several ways of measuring back from the end, which cancels
   * some of the quantisation in the individual run lengths.
   */
  public static double centerFromEnd(int[] pattern, double end) {
    int n = pattern.length;
    if (n == 5) {
      double a = pattern[4] + pattern[3] + pattern[2] / 2.0;
      double b = pattern[4] + (pattern[3] + pattern[2] + pattern[1]) / 2.0;
      double c = (pattern[4] + pattern[3] + pattern[2] + pattern[1] + pattern[0]) / 2.0;
      return end - (2 * a + b + c) / 4;
    } else if (n == 3) {
      double a = pattern[2] + pattern[1] / 2.0;
      double b = (pattern[2] + pattern[1] + pattern[0]) / 2.0;
      return end - (2 * a + b) / 3;
    }
    double a = pattern[n / 2] / 2.0;
    for (int i = n / 2 + 1; i < n; i++) {
      a += pattern[i];
    }
    return end - a;
  }

  /**
   * Reads a run of {@code result.length} elements centred on the cursor, by
   * walking outward in both directions at once. The pattern must have an odd
   * number of elements, since one of them straddles the starting point.
   */
  public static boolean readSymmetricPattern(BitMatrixCursorI cur, int[] result, int range) {
    int half = result.length / 2;
    java.util.Arrays.fill(result, 0);
    BitMatrixCursor opposite = cur.turnedBack();

    int[] remaining = {range};
    for (int i = 0; i <= half; i++) {
      if (!stepOutward(cur, result, half + i, remaining)
          || !stepOutward(opposite, result, half - i, remaining)) {
        return false;
      }
    }
    result[half]--; // the starting pixel was counted from both directions
    return true;
  }

  private static boolean stepOutward(BitMatrixCursor cur, int[] result, int index, int[] remaining) {
    int steps = cur.stepToEdge(1, remaining[0]);
    if (steps == 0) {
      return false;
    }
    result[index] += steps;
    if (remaining[0] != 0) {
      remaining[0] -= steps;
    }
    return true;
  }

  /**
   * Whether the run through the cursor, measured outward in both directions,
   * matches the given proportions.
   *
   * @param updatePosition whether to recentre the cursor on the middle element
   * @param relaxed whether to use the edge-to-edge measure, which tolerates ink
   *        spread but is looser
   * @return the total width in pixels, or 0 for no match
   */
  public static int checkSymmetricPattern(BitMatrixCursorI cur, int[] pattern, int range,
                                          boolean updatePosition, boolean relaxed) {
    FastEdgeToEdgeCounter forward = new FastEdgeToEdgeCounter(cur);
    FastEdgeToEdgeCounter backward = new FastEdgeToEdgeCounter(cur.turnedBack());

    int centerForward = forward.stepToNextEdge(range);
    if (centerForward == 0) {
      return 0;
    }
    int centerBackward = backward.stepToNextEdge(range);
    if (centerBackward == 0) {
      return 0;
    }

    int[] result = new int[pattern.length];
    int half = result.length / 2;
    result[half] = centerForward + centerBackward - 1; // the starting pixel counted twice
    range -= result[half];

    for (int i = 1; i <= half; i++) {
      int steps = forward.stepToNextEdge(range);
      result[half + i] = steps;
      range -= steps;
      if (steps == 0) {
        return 0;
      }
      steps = backward.stepToNextEdge(range);
      result[half - i] = steps;
      range -= steps;
      if (steps == 0) {
        return 0;
      }
    }

    double match = relaxed
        ? Patterns.isPatternEdgeToEdge(result, 0, pattern, 0, 0)
        : Patterns.isPattern(result, 0, pattern, Patterns.sum(pattern), 0, 0, 0);
    if (match == 0) {
      return 0;
    }

    if (updatePosition) {
      cur.step(result[half] / 2 - (centerBackward - 1));
    }

    int total = 0;
    for (int v : result) {
      total += v;
    }
    return total;
  }

  /**
   * Confirms a candidate position really holds a concentric pattern and pins
   * down its centre and size.
   *
   * <p>The pattern is checked along all four axes -- horizontal, vertical and
   * both diagonals -- and the widths measured along them must agree, which is
   * what rules out stripes and other accidental 1:1:3:1:1 runs. Only then is
   * the centre refined.</p>
   *
   * @param width the expected width of the pattern in pixels
   * @return the located pattern, or null
   */
  public static ConcentricPattern locateConcentricPattern(BitMatrix image, int[] pattern,
                                                          PointF center, int width, boolean e2e) {
    BitMatrixCursorI cur = new BitMatrixCursorI(image, new PointI(center), PointI.ZERO);
    int range = width * 2;
    double minSpread = image.getWidth();
    double maxSpread = 0;
    int maxError = 0;

    PointI[] axes = {new PointI(0, 1), new PointI(1, 0)};
    for (PointI d : axes) {
      cur.setDirection(d.toPointF());
      int spread = checkSymmetricPattern(cur, pattern, range, true, e2e);
      if (spread != 0) {
        minSpread = Math.min(minSpread, spread);
        maxSpread = Math.max(maxSpread, spread);
      } else if (--maxError < 0) {
        return null;
      }
    }

    PointI[] diagonals = {new PointI(1, 1), new PointI(1, -1)};
    for (PointI d : diagonals) {
      cur.setDirection(d.toPointF());
      // Always the relaxed measure on the diagonals: a square pattern crossed
      // corner to corner does not have the same proportions as edge to edge.
      int spread = checkSymmetricPattern(cur, pattern, range, false, true);
      if (spread != 0) {
        minSpread = Math.min(minSpread, spread);
        maxSpread = Math.max(maxSpread, spread);
      } else if (--maxError < 0) {
        return null;
      }
    }

    if (maxSpread > 5 * minSpread) {
      return null;
    }

    ConcentricPattern refined = finetuneConcentricPatternCenter(image, cur.p, width, pattern.length);
    if (refined != null && refined.size == 0) {
      return refined.withSize((maxSpread + minSpread) / 2);
    }
    return refined;
  }

  /**
   * Steps from edge to edge along a fixed direction, reading the matrix
   * directly. Upstream keeps this separate from the cursor because it is the
   * innermost loop of detection and runs millions of times per image.
   */
  private static final class FastEdgeToEdgeCounter {

    private final BitMatrix image;
    private final int dx;
    private final int dy;
    private int x;
    private int y;
    private int stepsToBorder;

    FastEdgeToEdgeCounter(BitMatrixCursor cur) {
      this.image = cur.image;
      this.dx = (int) cur.d.x;
      this.dy = (int) cur.d.y;
      this.x = (int) cur.p.x;
      this.y = (int) cur.p.y;

      int maxStepsX = dx != 0 ? (dx > 0 ? image.getWidth() - 1 - x : x) : Integer.MAX_VALUE;
      int maxStepsY = dy != 0 ? (dy > 0 ? image.getHeight() - 1 - y : y) : Integer.MAX_VALUE;
      this.stepsToBorder = Math.min(maxStepsX, maxStepsY);
    }

    int stepToNextEdge(int range) {
      int maxSteps = Math.min(stepsToBorder, range);
      int steps = 0;
      while (true) {
        if (++steps > maxSteps) {
          if (maxSteps == stepsToBorder) {
            break; // ran into the image border, which counts as an edge
          }
          return 0;
        }
        // The starting value is read here rather than before the loop on
        // purpose. Upstream dereferences it inside the loop condition, so it
        // is never touched when the step count check exits first -- and by
        // then the position may be outside the image, where C++ reads garbage
        // harmlessly and Java would throw.
        if (image.get(x + steps * dx, y + steps * dy) != image.get(x, y)) {
          break;
        }
      }
      x += steps * dx;
      y += steps * dy;
      stepsToBorder -= steps;
      return steps;
    }
  }
}
