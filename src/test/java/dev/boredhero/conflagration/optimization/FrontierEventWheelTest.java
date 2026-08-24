package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierEventWheelTest {

    @Test
    void drainsOnlyDueEventsAcrossWheelRotations() {
        FrontierEventWheel wheel = new FrontierEventWheel(100);
        List<Long> targets = new ArrayList<>();
        wheel.offer(110, 1, 10, 0);
        wheel.offer(110 + FrontierEventWheel.WHEEL_SIZE, 2, 20, 0);

        assertEquals(1, wheel.drainDue(110, 10, (due, source, target, age) -> targets.add(target)));
        assertEquals(List.of(10L), targets);
        assertEquals(1, wheel.size());

        assertEquals(1, wheel.drainDue(110 + FrontierEventWheel.WHEEL_SIZE, 10,
                (due, source, target, age) -> targets.add(target)));
        assertEquals(List.of(10L, 20L), targets);
    }

    @Test
    void budgetResumesSameBucketWithoutDroppingEvents() {
        FrontierEventWheel wheel = new FrontierEventWheel(0);
        List<Long> targets = new ArrayList<>();
        wheel.offer(5, 1, 10, 0);
        wheel.offer(5, 1, 20, 0);
        wheel.offer(5, 1, 30, 0);

        assertEquals(2, wheel.drainDue(5, 2, (due, source, target, age) -> targets.add(target)));
        assertEquals(1, wheel.size());
        assertEquals(1, wheel.drainDue(5, 2, (due, source, target, age) -> targets.add(target)));
        assertEquals(List.of(10L, 20L, 30L), targets);
    }

    @Test
    void delayedPollingStillFindsPastDueBuckets() {
        FrontierEventWheel wheel = new FrontierEventWheel(0);
        List<Long> targets = new ArrayList<>();
        wheel.offer(3, 1, 99, 0);

        assertEquals(1, wheel.drainDue(20, 10, (due, source, target, age) -> targets.add(target)));
        assertEquals(List.of(99L), targets);
    }
}
