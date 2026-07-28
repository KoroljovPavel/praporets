package io.praporets.core;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Java25SmokeTest {

    private sealed interface Shape permits Circle, Rectangle {}
    private record Circle(double radius) implements Shape {}
    private record Rectangle(double width, double height) implements Shape {}

    @Test
    @DisplayName("Verify Toolchain supports Sealed Interfaces, Records, and Record Patterns in Switch")
    void verifyModernJavaSyntaxSupport() {
        Shape firstShape = new Circle(5.0);
        Shape secondShape = new Rectangle(4.0, 5.0);

        double firstArea = calculateArea(firstShape);
        double secondArea = calculateArea(secondShape);

        assertThat(firstArea).isCloseTo(78.5398, Offset.offset(0.0001));
        assertThat(secondArea).isEqualTo(20.0);
    }

    private double calculateArea(Shape shape) {
        return switch (shape) {
            case Circle(double radius) -> Math.PI * radius * radius;
            case Rectangle(double width, double height) -> width * height;
            case null -> 0.0;
        };
    }

    private sealed interface Event permits Event.Start, Event.Stop {
        record Start(String name, long timestamp) implements Event {}
        record Stop(String name, int exitCode) implements Event {}
    }

    @Test
    void shouldCompileAndExecuteRecordPatternsInSwitch() {
        Event startEvent = new Event.Start("MyApp", 1000);
        Event stopEvent = new Event.Stop("MyApp", 0);

        assertThat(formatEvent(startEvent)).isEqualTo("Started MyApp at 1000");
        assertThat(formatEvent(stopEvent)).isEqualTo("Stopped MyApp with exit code 0");
    }

    private String formatEvent(Event event) {
        return switch (event) {
            case Event.Start(String name, long timestamp) -> String.format("Started %s at %d", name, timestamp);
            case Event.Stop(String name, int exitCode) -> String.format("Stopped %s with exit code %d", name, exitCode);
        };
    }
}
