package app.ui.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZoomableGraphPaneTest {

    @Test
    void testFitCalculationBoundsUnder30Nodes() {
        // Viewport 1200x800, graph bounds 0..600, 0..400, 10 nodes
        // graphW = 600 + 80 = 680, graphH = 400 + 80 = 480
        // s = min(1200/680, 800/480) = min(1.76, 1.66) -> capped at 1.0 (do not magnify above 1.0)
        ZoomableGraphPane.FitResult res = ZoomableGraphPane.calculateFit(1200, 800, 0, 0, 600, 400, 10);

        assertEquals(1.0, res.scale(), 0.001);
        // Center X: 300 * 1.0 = 300, viewW/2 = 600 -> tx = 300
        assertEquals(300.0, res.translateX(), 0.001);
        // Center Y: 200 * 1.0 = 200, viewH/2 = 400 -> ty = 200
        assertEquals(200.0, res.translateY(), 0.001);
    }

    @Test
    void testFitCalculationClampsMinScaleForSmallNodeCount() {
        // Viewport 800x600, very large bounds (0..2400, 0..1800), but nodeCount = 5 <= 30
        // Natural scale would be min(800/2480, 600/1880) ≈ 0.319
        // For <= 30 nodes, scale must not drop below 0.6 to keep text legible!
        ZoomableGraphPane.FitResult res = ZoomableGraphPane.calculateFit(800, 600, 0, 0, 2400, 1800, 5);

        assertEquals(0.6, res.scale(), 0.001);
    }

    @Test
    void testFitCalculationAllowsDeeperZoomOutForLargeGraphs() {
        // Viewport 800x600, large bounds with 50 nodes
        // Scale should scale down below 0.6
        ZoomableGraphPane.FitResult res = ZoomableGraphPane.calculateFit(800, 600, 0, 0, 2400, 1800, 50);

        assertTrue(res.scale() < 0.6);
        assertTrue(res.scale() >= 0.25);
    }

    @Test
    void testFitCalculationCentering() {
        // Bounds at offset (200..600, 100..500), nodeCount = 10
        // minX = 200, maxX = 600 -> centerX = 400
        // minY = 100, maxY = 500 -> centerY = 300
        // graphW = 400 + 80 = 480, graphH = 400 + 80 = 480
        // view = 1000x800 -> s = 1.0
        ZoomableGraphPane.FitResult res = ZoomableGraphPane.calculateFit(1000, 800, 200, 100, 600, 500, 10);

        assertEquals(1.0, res.scale(), 0.001);
        // tx = 500 - 400*1 = 100
        assertEquals(100.0, res.translateX(), 0.001);
        // ty = 400 - 300*1 = 100
        assertEquals(100.0, res.translateY(), 0.001);
    }
}

